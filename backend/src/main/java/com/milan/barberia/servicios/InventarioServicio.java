package com.milan.barberia.servicios;

import com.milan.barberia.excepciones.ExcepcionNegocio;
import com.milan.barberia.entidades.MovimientoInventario;
import com.milan.barberia.entidades.Producto;
import com.milan.barberia.repositorios.MovimientoInventarioRepositorio;
import com.milan.barberia.repositorios.ProductoRepositorio;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import com.milan.barberia.repositorios.ReporteRepositorio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Entradas (compras) y salidas (consumo, mermas) de inventario, y alerta de stock bajo. */
@Service
@RequiredArgsConstructor
public class InventarioServicio {

    public record MovimientoRequest(@NotNull Integer idProducto,
                                    @NotBlank String tipo,
                                    @NotNull @Min(1) Integer cantidad,
                                    String motivo) {}

    private final MovimientoInventarioRepositorio movRepo;
    private final ProductoRepositorio productoRepo;
    private final ReporteRepositorio reportes;

    @Transactional
    public MovimientoInventario registrar(Integer idUsuario, MovimientoRequest r) {
        if (!Set.of("entrada", "salida").contains(r.tipo())) {
            throw new ExcepcionNegocio("El tipo debe ser 'entrada' o 'salida'");
        }
        Producto p = productoRepo.findById(r.idProducto())
                .orElseThrow(() -> new ExcepcionNegocio("El producto no existe"));
        if ("salida".equals(r.tipo()) && p.getStockActual() < r.cantidad()) {
            throw new ExcepcionNegocio("Stock insuficiente de «" + p.getNombre() + "»: hay " + p.getStockActual());
        }
        MovimientoInventario m = new MovimientoInventario();
        m.setIdProducto(r.idProducto());
        m.setIdUsuario(idUsuario);
        m.setTipo(r.tipo());
        m.setCantidad(r.cantidad());
        m.setMotivo(r.motivo());
        return movRepo.save(m); // el trigger actualiza el stock
    }

    public List<MovimientoInventario> historial(Integer idProducto) {
        return movRepo.findByIdProductoOrderByFechaHoraDesc(idProducto);
    }

    /** Productos en o por debajo de su stock mínimo (vista v_productos_stock_bajo). */
    public List<Map<String, Object>> stockBajo() {
        return reportes.stockBajo();
    }
}
