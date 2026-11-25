package com.milan.barberia.controladores;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import com.milan.barberia.entidades.Caja;
import com.milan.barberia.servicios.AutenticacionServicio;
import com.milan.barberia.servicios.CajaServicio;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/caja")
@Tag(name = "Caja", description = "Apertura, cierre y arqueo de caja")
@RequiredArgsConstructor
public class CajaControlador {

    public record AbrirRequest(@DecimalMin("0") BigDecimal montoApertura) {}

    public record CerrarRequest(@NotNull @DecimalMin("0") BigDecimal efectivoContado, String observaciones) {}

    private final CajaServicio servicio;

    @PostMapping("/abrir")
    @ResponseStatus(HttpStatus.CREATED)
    public Caja abrir(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestAttribute("sesion") AutenticacionServicio.Sesion sesion, @Valid @RequestBody AbrirRequest r) {
        return servicio.abrir(sesion.idUsuario(), r.montoApertura());
    }

    @Operation(summary = "Caja abierta actualmente (404 si no hay)")
    @GetMapping("/actual")
    public Caja actual() {
        return servicio.actual();
    }

    @Operation(summary = "Estado de la caja: {abierta: true/false, caja, arqueo}")
    @GetMapping("/estado")
    public Map<String, Object> estado() {
        Caja c = servicio.actualOpcional();
        Map<String, Object> r = new java.util.LinkedHashMap<>();
        r.put("abierta", c != null);
        r.put("caja", c);
        r.put("arqueo", c != null ? servicio.arqueo(c.getIdCaja()) : null);
        return r;
    }

    @GetMapping
    public List<Caja> listar() {
        return servicio.listar();
    }

    @PostMapping("/{id}/cerrar")
    public Map<String, Object> cerrar(@PathVariable Integer id, @Valid @RequestBody CerrarRequest r) {
        return servicio.cerrar(id, r.efectivoContado(), r.observaciones());
    }

    @GetMapping("/{id}/arqueo")
    public Map<String, Object> arqueo(@PathVariable Integer id) {
        return servicio.arqueo(id);
    }
}
