package com.milan.barberia.controladores;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * CRUD genérico: listar, obtener, crear, actualizar y eliminar.
 * Cada controlador concreto solo indica su repositorio y el nombre de su campo id.
 */
public abstract class CrudBaseControlador<T> {

    protected final JpaRepository<T, Integer> repo;
    private final String propiedadId;

    protected CrudBaseControlador(JpaRepository<T, Integer> repo, String propiedadId) {
        this.repo = repo;
        this.propiedadId = propiedadId;
    }

    @Operation(summary = "Listar todos")
    @GetMapping
    public List<T> listar() {
        return repo.findAll();
    }

    @Operation(summary = "Consultar por id")
    @GetMapping("/{id}")
    public T obtener(@PathVariable Integer id) {
        return repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No existe el registro " + id));
    }

    @Operation(summary = "Crear")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public T crear(@Valid @RequestBody T cuerpo) {
        PropertyAccessorFactory.forBeanPropertyAccess(cuerpo).setPropertyValue(propiedadId, null);
        return repo.save(cuerpo);
    }

    @Operation(summary = "Actualizar (se envía el registro completo)")
    @PutMapping("/{id}")
    public T actualizar(@PathVariable Integer id, @Valid @RequestBody T cuerpo) {
        if (!repo.existsById(id)) {
            throw new NoSuchElementException("No existe el registro " + id);
        }
        PropertyAccessorFactory.forBeanPropertyAccess(cuerpo).setPropertyValue(propiedadId, id);
        return repo.save(cuerpo);
    }

    @Operation(summary = "Eliminar", description = "Si el registro está en uso responde 409; en ese caso desactívalo")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Integer id) {
        if (!repo.existsById(id)) {
            throw new NoSuchElementException("No existe el registro " + id);
        }
        repo.deleteById(id);
    }
}
