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

import co.edu.unbosque.ciclo3back.model.Clientes;
import co.edu.unbosque.ciclo3back.service.ClientesService;

/**
 * Capa de presentación (API REST) para los clientes del gabinete.
 * Los errores (404, 409, 400) los resuelve ManejadorErroresAPI.
 */
@RestController
@RequestMapping("clientes")
public class ClientesAPI {

	private final ClientesService clientesService;

	@Autowired
	public ClientesAPI(ClientesService clientesService) {
		this.clientesService = clientesService;
	}

	@PostMapping("/guardar")
	public ResponseEntity<Clientes> guardar(@Valid @RequestBody Clientes cliente) {
		return ResponseEntity.status(HttpStatus.CREATED).body(clientesService.crear(cliente));
	}

	@GetMapping("/listar")
	public List<Clientes> listar() {
		return clientesService.listar();
	}

	@GetMapping("/{id}")
	public Clientes buscar(@PathVariable("id") long id) {
		return clientesService.buscar(id);
	}

	@PutMapping("/actualizar")
	public Clientes actualizar(@Valid @RequestBody Clientes cliente) {
		return clientesService.actualizar(cliente);
	}

	@DeleteMapping("/eliminar/{id}")
	public ResponseEntity<Void> eliminar(@PathVariable("id") long id) {
		clientesService.eliminar(id);
		return ResponseEntity.noContent().build();
	}

}
