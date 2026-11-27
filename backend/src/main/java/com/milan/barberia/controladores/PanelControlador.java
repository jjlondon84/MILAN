package com.milan.barberia.controladores;

import com.milan.barberia.servicios.AutenticacionServicio;
import com.milan.barberia.servicios.BusquedaServicio;
import com.milan.barberia.servicios.PanelServicio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
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

    @Operation(summary = "Mi día (barbero)", description = "Panel del barbero de la sesión: sus citas de hoy, la que atiende, lo generado, propinas y retraso")
    @GetMapping("/mi-panel")
    public Map<String, Object> miPanel(@Parameter(hidden = true) @RequestAttribute("sesion") AutenticacionServicio.Sesion sesion) {
        return dashboard.miPanel(sesion.barberoPropio());
    }

    @Operation(summary = "Buscar clientes, barberos y productos", description = "Mínimo 2 caracteres. Un barbero solo busca clientes")
    @GetMapping("/buscar")
    public Map<String, Object> buscar(@Parameter(hidden = true) @RequestAttribute("sesion") AutenticacionServicio.Sesion sesion,
                                      @RequestParam String q) {
        Map<String, Object> r = busqueda.buscar(q);
        if (!sesion.admin()) {
            r.put("barberos", List.of());
            r.put("productos", List.of());
        }
        return r;
    }
}
