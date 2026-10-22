package co.abogados.procesos.infraestructura.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** F10: el nombre del archivo descargado llega bien en cualquier navegador (RFC 6266). */
class DocumentosRecursoTest {

	@Test
	void elNombreVaEnAsciiYEnUtf8() {
		assertThat(DocumentosRecurso.adjunto("Contrato año 2026 \"final\".pdf"))
				.isEqualTo("attachment; filename=\"Contrato ano 2026 final.pdf\"; "
						+ "filename*=UTF-8''Contrato%20a%C3%B1o%202026%20%22final%22.pdf");
	}

}
