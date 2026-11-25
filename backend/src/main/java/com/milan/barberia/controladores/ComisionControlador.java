package com.milan.barberia.controladores;

import com.milan.barberia.servicios.ComisionServicio;
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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/comisiones")
@RequiredArgsConstructor
@Tag(name = "Comisiones", description = "Liquidación de comisiones por barbero (servicios + productos)")
public class ComisionControlador {

    private final ComisionServicio servicio;

    @Operation(summary = "Comisión de cada barbero en un periodo", description = "Ej: /api/comisiones?desde=2026-11-01&hasta=2026-11-15")
    @GetMapping
    public List<Map<String, Object>> calcular(
            @Parameter(description = "Fecha inicial (aaaa-mm-dd)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @Parameter(description = "Fecha final (aaaa-mm-dd)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return servicio.calcular(desde, hasta);
    }

    @Operation(summary = "Comisiones con totales (total generado y total a liquidar)")
    @GetMapping("/resumen")
    public Map<String, Object> resumen(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return servicio.resumen(desde, hasta);
    }
}
