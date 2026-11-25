package com.milan.barberia.controladores;

import com.milan.barberia.entidades.Cliente;
import com.milan.barberia.repositorios.ClienteRepositorio;
import com.milan.barberia.servicios.ClienteServicio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

/** Clientes: /api/clientes (+ búsqueda por nombre, apellido o teléfono) */
@RestController
@RequestMapping("/api/clientes")
@Tag(name = "Clientes", description = "Fichas de clientes: contacto, preferencias e historial")
public class ClienteControlador extends CrudBaseControlador<Cliente> {

    private final ClienteRepositorio clienteRepo;
    private final ClienteServicio clienteServicio;

    public ClienteControlador(ClienteRepositorio repo, ClienteServicio clienteServicio) {
        super(repo, "idCliente");
        this.clienteRepo = repo;
        this.clienteServicio = clienteServicio;
    }

    @Operation(summary = "Buscar por nombre, apellido o teléfono")
    @GetMapping("/buscar")
    public List<Cliente> buscar(@RequestParam String q) {
        return clienteRepo.findByNombresContainingIgnoreCaseOrApellidosContainingIgnoreCaseOrTelefonoContaining(q, q, q);
    }

    @Operation(summary = "Clientes con número de visitas, última visita y próxima cita")
    @GetMapping("/resumen")
    public List<ClienteServicio.ClienteResumen> resumen(@RequestParam(required = false) String q) {
        return clienteServicio.resumen(q);
    }

    /** Al editar se conserva la fecha de registro original. */
    @Override
    @PutMapping("/{id}")
    public Cliente actualizar(@PathVariable Integer id, @Valid @RequestBody Cliente cuerpo) {
        Cliente actual = clienteRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No existe el cliente " + id));
        cuerpo.setFechaRegistro(actual.getFechaRegistro());
        return super.actualizar(id, cuerpo);
    }
}
