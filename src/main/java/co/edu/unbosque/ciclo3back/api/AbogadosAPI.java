package co.edu.unbosque.ciclo3back.api;

import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.ciclo3back.model.Abogados;
import co.edu.unbosque.ciclo3back.service.AbogadosService;

/**
 * Capa de presentación (API REST) para los abogados del gabinete.
 */
@RestController
@RequestMapping("abogados")
public class AbogadosAPI {

	private final AbogadosService abogadosService;

	@Autowired
	public AbogadosAPI(AbogadosService abogadosService) {
		this.abogadosService = abogadosService;
	}

	@PostMapping("/guardar")
	public ResponseEntity<Abogados> guardar(@Valid @RequestBody Abogados abogado) {
		return ResponseEntity.status(HttpStatus.CREATED).body(abogadosService.crear(abogado));
	}

	@GetMapping("/listar")
	public List<Abogados> listar() {
		return abogadosService.listar();
	}

	@GetMapping("/{id}")
	public Abogados buscar(@PathVariable("id") long id) {
		return abogadosService.buscar(id);
	}

	@PutMapping("/actualizar")
	public Abogados actualizar(@Valid @RequestBody Abogados abogado) {
		return abogadosService.actualizar(abogado);
	}

	@DeleteMapping("/eliminar/{id}")
	public ResponseEntity<Void> eliminar(@PathVariable("id") long id) {
		abogadosService.eliminar(id);
		return ResponseEntity.noContent().build();
	}

}
