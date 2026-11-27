package com.milan.barberia.controladores;

import com.milan.barberia.servicios.IngresoServicio;
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

/** Solo administrador (el interceptor bloquea esta ruta para los barberos). */
@RestController
@RequestMapping("/api/ingresos")
@RequiredArgsConstructor
@Tag(name = "Ingresos", description = "Ingresos por día, semana o mes (solo administrador)")
public class IngresoControlador {

    private final IngresoServicio servicio;

    @Operation(summary = "Reporte de ingresos de un periodo",
            description = "Ej: /api/ingresos?desde=2026-11-01&hasta=2026-11-30&agrupar=dia. "
                    + "agrupar = dia | semana | mes (si se omite se elige según el largo del periodo).")
    @GetMapping
    public Map<String, Object> reporte(
            @Parameter(description = "Fecha inicial (aaaa-mm-dd)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @Parameter(description = "Fecha final (aaaa-mm-dd)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @Parameter(description = "dia, semana o mes") @RequestParam(required = false) String agrupar) {
        return servicio.reporte(desde, hasta, agrupar);
    }
}
