package co.edu.unbosque.ciclo3back.api;

import java.time.LocalDate;
import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.ciclo3back.model.AsuntoRequest;
import co.edu.unbosque.ciclo3back.model.Asuntos;
import co.edu.unbosque.ciclo3back.model.EstadoAsunto;
import co.edu.unbosque.ciclo3back.service.AsuntosService;

/**
 * Capa de presentación (API REST) para los asuntos del gabinete.
 * Además del CRUD, permite consultar asuntos por cliente, por abogado y
 * por estado, y archivar un asunto.
 */
@RestController
@RequestMapping("asuntos")
public class AsuntosAPI {

	private final AsuntosService asuntosService;

	@Autowired
	public AsuntosAPI(AsuntosService asuntosService) {
		this.asuntosService = asuntosService;
	}

	@PostMapping("/guardar")
	public ResponseEntity<Asuntos> guardar(@Valid @RequestBody AsuntoRequest asunto) {
		return ResponseEntity.status(HttpStatus.CREATED).body(asuntosService.crear(asunto));
	}

	@GetMapping("/listar")
	public List<Asuntos> listar() {
		return asuntosService.listar();
	}

	@GetMapping("/{expediente}")
	public Asuntos buscar(@PathVariable("expediente") String expediente) {
		return asuntosService.buscar(expediente);
	}

	@GetMapping("/cliente/{id}")
	public List<Asuntos> porCliente(@PathVariable("id") long idCliente) {
		return asuntosService.listarPorCliente(idCliente);
	}

	@GetMapping("/abogado/{id}")
	public List<Asuntos> porAbogado(@PathVariable("id") long idAbogado) {
		return asuntosService.listarPorAbogado(idAbogado);
	}

	@GetMapping("/estado/{estado}")
	public List<Asuntos> porEstado(@PathVariable("estado") EstadoAsunto estado) {
		return asuntosService.listarPorEstado(estado);
	}

	@PutMapping("/actualizar")
	public Asuntos actualizar(@Valid @RequestBody AsuntoRequest asunto) {
		return asuntosService.actualizar(asunto);
	}

	/** PUT /asuntos/{expediente}/archivar?fecha=2026-09-01  (sin fecha, se usa la de hoy) */
	@PutMapping("/{expediente}/archivar")
	public Asuntos archivar(@PathVariable("expediente") String expediente,
			@RequestParam(value = "fecha", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
		return asuntosService.archivar(expediente, fecha);
	}

	@DeleteMapping("/eliminar/{expediente}")
	public ResponseEntity<Void> eliminar(@PathVariable("expediente") String expediente) {
		asuntosService.eliminar(expediente);
		return ResponseEntity.noContent().build();
	}

}
