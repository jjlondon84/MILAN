package com.milan.barberia.controladores;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.milan.barberia.entidades.MovimientoInventario;
import com.milan.barberia.servicios.AutenticacionServicio;
import com.milan.barberia.servicios.InventarioServicio;
import com.milan.barberia.servicios.InventarioServicio.MovimientoRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventario")
@Tag(name = "Inventario", description = "Entradas, salidas y alertas de stock bajo")
@RequiredArgsConstructor
public class InventarioControlador {

    private final InventarioServicio servicio;

    @PostMapping("/movimientos")
    @ResponseStatus(HttpStatus.CREATED)
    public MovimientoInventario registrar(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestAttribute("sesion") AutenticacionServicio.Sesion sesion,
                                          @Valid @RequestBody MovimientoRequest r) {
        return servicio.registrar(sesion.idUsuario(), r);
    }

    @GetMapping("/movimientos/{idProducto}")
    public List<MovimientoInventario> historial(@PathVariable Integer idProducto) {
        return servicio.historial(idProducto);
    }

    /** Alerta: productos en o bajo el stock mínimo. */
    @GetMapping("/stock-bajo")
    public List<Map<String, Object>> stockBajo() {
        return servicio.stockBajo();
    }
}
