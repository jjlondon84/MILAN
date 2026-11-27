package com.milan.barberia.controladores;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.milan.barberia.entidades.TurnoLaboral;
import com.milan.barberia.repositorios.TurnoLaboralRepositorio;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Horario semanal de cada barbero: /api/turnos */
@RestController
@RequestMapping("/api/turnos")
@Tag(name = "Turnos", description = "Horario semanal y descanso de cada barbero (1=lunes ... 7=domingo)")
public class TurnoControlador extends CrudBaseControlador<TurnoLaboral> {

    private final TurnoLaboralRepositorio turnoRepo;

    public TurnoControlador(TurnoLaboralRepositorio repo) {
        super(repo, "idTurno");
        this.turnoRepo = repo;
    }

    @GetMapping("/barbero/{idBarbero}")
    public List<TurnoLaboral> porBarbero(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @org.springframework.web.bind.annotation.RequestAttribute("sesion") com.milan.barberia.servicios.AutenticacionServicio.Sesion sesion,
                                   @PathVariable Integer idBarbero) {
        if (!sesion.puedeVerBarbero(idBarbero)) {
            throw new com.milan.barberia.excepciones.ExcepcionAccesoDenegado("Solo puedes ver tu propio horario");
        }
        return turnoRepo.findByIdBarbero(idBarbero);
    }
}
