package co.abogados.procesos.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import co.abogados.procesos.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;
import co.abogados.procesos.dominio.modelo.Documento;
import co.abogados.procesos.dominio.modelo.ProcesoVista;
import co.abogados.procesos.dominio.puertos.DocumentoRepositorio;
import co.abogados.procesos.dominio.puertos.ProcesoRepositorio;
import co.abogados.procesos.infraestructura.archivos.AlmacenArchivosDisco;

/** F10: expediente digital con archivos reales en una carpeta temporal y la base en memoria. */
class DocumentoServicioTest {

	static final byte[] PDF = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);

	@TempDir
	Path carpeta;

	DocumentosEnMemoria repo;
	DocumentoServicio servicio;

	static ProcesoVista proceso(int id, int cliente) {
		return new ProcesoVista(id, "11001310303220260050100", "11001-31-03-032-2026-00501-00", (short) 1, "Verbal",
				"CIVIL", "Civil y comercial", "Juzgado 32", null, "Juzgado 32", 0, cliente, "DEMANDANTE", "Asunto",
				BigDecimal.ONE, "MAYOR", "REPRESENTACION", "ACTIVO", "PRUEBAS", LocalDate.of(2026, 3, 1), true, 1L);
	}

	@BeforeEach
	void preparar() {
		ProcesoRepositorio procesos = mock(ProcesoRepositorio.class);
		given(procesos.buscar(34)).willReturn(Optional.of(proceso(34, 6)));
		given(procesos.buscar(35)).willReturn(Optional.of(proceso(35, 1)));
		repo = new DocumentosEnMemoria();
		// Máximo de 1 MB para probar el límite sin crear archivos grandes
		servicio = new DocumentoServicio(repo, procesos, new AlmacenArchivosDisco(carpeta.toString()), Clock.systemUTC(), 1);
	}

	Documento subir(String nombre, byte[] contenido, boolean visible) {
		return servicio.subir(34, nombre, new ByteArrayInputStream(contenido), Documento.Categoria.PRUEBA, "Factura",
				visible, 1019087654L);
	}

	long archivosGuardados() throws Exception {
		try (Stream<Path> s = Files.walk(carpeta)) {
			return s.filter(Files::isRegularFile).count();
		}
	}

	@Test
	void guardaElPdfConSuHuellaYLoDevuelveIgual() throws Exception {
		Documento d = subir("C:\\Users\\x\\Factura 101.pdf", PDF, false);

		assertThat(d.nombre()).isEqualTo("Factura 101.pdf");                 // sin la ruta del equipo
		assertThat(d.tipoMime()).isEqualTo("application/pdf");
		assertThat(d.tamano()).isEqualTo(PDF.length);
		assertThat(d.sha256()).hasSize(64);
		assertThat(servicio.abrir(34, d.idDocumento(), 1L).contenido().readAllBytes()).isEqualTo(PDF);
		assertThat(archivosGuardados()).isEqualTo(1);
	}

	@Test
	void rechazaTiposNoPermitidosYContenidoQueNoCoincideConLaExtension() throws Exception {
		assertThatThrownBy(() -> subir("pagina.html", "<html><script>alert(1)</script>".getBytes(), false))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no permitido");
		assertThatThrownBy(() -> subir("programa.exe", new byte[] { 'M', 'Z', 0, 0 }, false))
				.hasMessageContaining("no permitido");
		assertThatThrownBy(() -> subir("falso.pdf", "<html>no soy un pdf</html>".getBytes(), false))
				.hasMessageContaining("no corresponde");
		assertThatThrownBy(() -> subir("nota.txt", "<!DOCTYPE html><html>".getBytes(), false))
				.hasMessageContaining("no corresponde");
		assertThat(archivosGuardados()).isZero();
		assertThat(subir("nota.txt", "Llamar al perito el lunes\n".getBytes(), false).tipoMime()).startsWith("text/plain");
	}

	@Test
	void unArchivoDemasiadoGrandeNoDejaNadaGuardado() throws Exception {
		byte[] grande = new byte[1024 * 1024 + 10];
		System.arraycopy(PDF, 0, grande, 0, PDF.length);

		assertThatThrownBy(() -> subir("grande.pdf", grande, false)).hasMessageContaining("máximo");
		assertThat(archivosGuardados()).isZero();
		assertThat(repo.datos).isEmpty();
	}

	@Test
	void elClienteSoloVeLosVisiblesDeSusProcesos() {
		Documento oculto = subir("borrador.pdf", PDF, false);
		Documento visible = subir("sentencia.pdf", PDF, true);

		assertThat(servicio.visiblesParaCliente(6, 34)).extracting(Documento::nombre).containsExactly("sentencia.pdf");
		assertThat(servicio.abrirParaCliente(6, 34, visible.idDocumento(), 1L).documento().nombre()).isEqualTo("sentencia.pdf");
		assertThatThrownBy(() -> servicio.abrirParaCliente(6, 34, oculto.idDocumento(), 1L))
				.isInstanceOf(RecursoNoEncontradoException.class);
		// Otro cliente: el proceso "no existe" para él
		assertThatThrownBy(() -> servicio.visiblesParaCliente(1, 34)).isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> servicio.abrirParaCliente(1, 34, visible.idDocumento(), 1L))
				.isInstanceOf(RecursoNoEncontradoException.class);
	}

	@Test
	void borrarQuitaLosDatosYElArchivo() throws Exception {
		Documento d = subir("poder.pdf", PDF, false);

		servicio.eliminar(34, d.idDocumento(), 1L);

		assertThat(repo.datos).isEmpty();
		assertThat(archivosGuardados()).isZero();
		assertThatThrownBy(() -> servicio.abrir(34, d.idDocumento(), 1L)).isInstanceOf(RecursoNoEncontradoException.class);
	}

	@Test
	void unDocumentoDeOtroProcesoNoSeAbrePorEsteProceso() {
		Documento d = subir("poder.pdf", PDF, false);
		assertThatThrownBy(() -> servicio.abrir(35, d.idDocumento(), 1L)).isInstanceOf(RecursoNoEncontradoException.class);
	}

	// ----------------------------------------------------------------- doble

	static class DocumentosEnMemoria implements DocumentoRepositorio {
		final List<Documento> datos = new ArrayList<>();
		int siguiente = 1;

		public Documento guardar(Documento d) {
			Documento con = new Documento(siguiente++, d.idProceso(), d.archivo(), d.nombre(), d.tipoMime(), d.tamano(),
					d.sha256(), d.categoria(), d.descripcion(), d.visibleCliente(), d.subidoPor(), d.subido());
			datos.add(con);
			return con;
		}
		public List<Documento> listar(int p, boolean soloVisibles) {
			return datos.stream().filter(d -> d.idProceso() == p && (!soloVisibles || d.visibleCliente())).toList();
		}
		public Optional<Documento> buscar(int p, int id) {
			return datos.stream().filter(d -> d.idProceso() == p && d.idDocumento() == id).findFirst();
		}
		public void actualizar(int id, Documento.Categoria c, String desc, boolean v) {
		}
		public void eliminar(int id) {
			datos.removeIf(d -> d.idDocumento() == id);
		}
	}

}
