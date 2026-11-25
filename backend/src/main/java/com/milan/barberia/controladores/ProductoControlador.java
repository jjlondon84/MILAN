package com.milan.barberia.controladores;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import com.milan.barberia.entidades.Producto;
import com.milan.barberia.repositorios.ProductoRepositorio;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

/**
 * Productos de venta e insumos: /api/productos
 * Al editar, el stock NO cambia: se mueve solo con las ventas y con /api/inventario/movimientos.
 */
@RestController
@RequestMapping("/api/productos")
@Tag(name = "Productos", description = "Productos de venta e insumos")
public class ProductoControlador extends CrudBaseControlador<Producto> {

    private final ProductoRepositorio productoRepo;

    public ProductoControlador(ProductoRepositorio repo) {
        super(repo, "idProducto");
        this.productoRepo = repo;
    }

    @Operation(summary = "Buscar productos por nombre")
    @GetMapping("/buscar")
    public java.util.List<Producto> buscar(@RequestParam String q) {
        return productoRepo.findByNombreContainingIgnoreCase(q);
    }

    @Override
    @PutMapping("/{id}")
    public Producto actualizar(@PathVariable Integer id, @Valid @RequestBody Producto cuerpo) {
        Producto actual = productoRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No existe el producto " + id));
        cuerpo.setStockActual(actual.getStockActual());
        return super.actualizar(id, cuerpo);
    }
}
