package com.milan.barberia.controladores;

import com.milan.barberia.entidades.Barbero;
import com.milan.barberia.repositorios.BarberoRepositorio;
import com.milan.barberia.servicios.BarberoServicio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Barberos: /api/barberos */
@RestController
@RequestMapping("/api/barberos")
@Tag(name = "Barberos", description = "Personal de la barbería")
public class BarberoControlador extends CrudBaseControlador<Barbero> {

    private final BarberoRepositorio barberoRepo;
    private final BarberoServicio barberoServicio;

    public BarberoControlador(BarberoRepositorio repo, BarberoServicio barberoServicio) {
        super(repo, "idBarbero");
        this.barberoRepo = repo;
        this.barberoServicio = barberoServicio;
    }

    @Operation(summary = "Personal con turno, descanso y ausencia del día",
            description = "disponibilidad: en_turno, descanso, fuera_de_turno, ausente, sin_turno o inactivo")
    @GetMapping("/resumen")
    public List<BarberoServicio.BarberoResumen> resumen(
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @org.springframework.web.bind.annotation.RequestAttribute("sesion") com.milan.barberia.servicios.AutenticacionServicio.Sesion sesion,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return barberoServicio.resumen(fecha).stream()
                .filter(b -> sesion.puedeVerBarbero(b.idBarbero()))  // un barbero solo se ve a sí mismo
                .toList();
    }

    @Operation(summary = "Solo barberos activos (para agendar)")
    @GetMapping("/activos")
    public List<Barbero> activos(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @org.springframework.web.bind.annotation.RequestAttribute("sesion") com.milan.barberia.servicios.AutenticacionServicio.Sesion sesion) {
        return barberoRepo.findByEstadoOrderByNombres("activo").stream()
                .filter(b -> sesion.puedeVerBarbero(b.getIdBarbero()))
                .toList();
    }
}
