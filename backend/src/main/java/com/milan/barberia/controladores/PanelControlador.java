package com.milan.barberia.controladores;

import com.milan.barberia.servicios.BusquedaServicio;
import com.milan.barberia.servicios.PanelServicio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Resumen general y búsqueda global")
public class PanelControlador {

    private final PanelServicio dashboard;
    private final BusquedaServicio busqueda;

    @Operation(summary = "Indicadores del día", description = "Citas, ingresos (vs. día anterior), stock bajo, barberos en turno y próximas citas")
    @GetMapping("/dashboard")
    public Map<String, Object> resumen(
            @Parameter(description = "Día a consultar (por defecto hoy)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return dashboard.resumen(fecha);
    }

    @Operation(summary = "Buscar clientes, barberos y productos", description = "Mínimo 2 caracteres")
    @GetMapping("/buscar")
    public Map<String, Object> buscar(@RequestParam String q) {
        return busqueda.buscar(q);
    }
}
