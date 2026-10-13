package co.edu.unbosque.ciclo3back.api;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.ciclo3back.dao.AbogadosDAO;
import co.edu.unbosque.ciclo3back.dao.UsuariosDAO;
import co.edu.unbosque.ciclo3back.model.Usuarios;

/**
 * API REST para las operaciones CRUD sobre la tabla usuarios.
 * Cada método devuelve un ResponseEntity con el código HTTP adecuado,
 * de forma que el frontend pueda reaccionar correctamente.
 */
@RestController
@RequestMapping("usuarios")
public class UsuariosAPI {

	private final UsuariosDAO usuariosDAO;
	private final AbogadosDAO abogadosDAO;

	@Autowired
	public UsuariosAPI(UsuariosDAO usuariosDAO, AbogadosDAO abogadosDAO) {
		this.usuariosDAO = usuariosDAO;
		this.abogadosDAO = abogadosDAO;
	}

	/** Crea un nuevo usuario. Devuelve 409 si la cédula ya existe, 400 si los datos son inválidos. */
	@PostMapping("/guardar")
	public ResponseEntity<?> guardar(@Valid @RequestBody Usuarios usuarios) {
		if (usuariosDAO.existsById(usuarios.getCedula_usuario())) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body("Ya existe un usuario con la cédula " + usuarios.getCedula_usuario());
		}
		Usuarios creado = usuariosDAO.save(usuarios);
		return ResponseEntity.status(HttpStatus.CREATED).body(creado);
	}

	/** Lista todos los usuarios, ordenados por cédula ascendente. */
	@GetMapping("/listar")
	public List<Usuarios> listar() {
		List<Usuarios> lista = usuariosDAO.findAll();
		lista.sort(Comparator.comparingLong(Usuarios::getCedula_usuario));
		return lista;
	}

	/** Consulta un usuario por su cédula. Devuelve 404 si no existe. */
	@GetMapping("/{id}")
	public ResponseEntity<Usuarios> buscar(@PathVariable("id") Long id) {
		return usuariosDAO.findById(id)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	/** Elimina un usuario por su cédula. Devuelve 404 si no existe y 409 si es la cuenta de un abogado. */
	@DeleteMapping("/eliminar/{id}")
	public ResponseEntity<?> eliminar(@PathVariable("id") Long id) {
		if (!usuariosDAO.existsById(id)) {
			return ResponseEntity.notFound().build();
		}
		if (abogadosDAO.contarPorUsuario(id, -1) > 0) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body("El usuario " + id + " es la cuenta de un abogado; desvincúlelo del abogado antes de eliminarlo");
		}
		usuariosDAO.deleteById(id);
		return ResponseEntity.noContent().build();
	}

	/** Actualiza un usuario existente. Devuelve 404 si no existe, 400 si los datos son inválidos. */
	@PutMapping("/actualizar")
	public ResponseEntity<?> actualizar(@Valid @RequestBody Usuarios usuarios) {
		if (!usuariosDAO.existsById(usuarios.getCedula_usuario())) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body("No existe un usuario con la cédula " + usuarios.getCedula_usuario());
		}
		Usuarios actualizado = usuariosDAO.save(usuarios);
		return ResponseEntity.ok(actualizado);
	}

	/**
	 * Captura los errores de Bean Validation (@Valid) y los devuelve como
	 * un JSON { campo: mensaje } con código 400, para que el frontend pueda
	 * mostrar cada error junto a su campo.
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, String> manejarErroresValidacion(MethodArgumentNotValidException ex) {
		Map<String, String> errores = new HashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			errores.put(error.getField(), error.getDefaultMessage());
		}
		return errores;
	}

}
