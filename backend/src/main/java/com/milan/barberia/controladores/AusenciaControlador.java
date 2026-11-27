package com.milan.barberia.controladores;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.milan.barberia.excepciones.ExcepcionNegocio;
import com.milan.barberia.entidades.Ausencia;
import com.milan.barberia.repositorios.AusenciaRepositorio;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/** Licencias, días libres, incapacidades y vacaciones: /api/ausencias */
@RestController
@RequestMapping("/api/ausencias")
@Tag(name = "Ausencias", description = "Licencias, días libres, incapacidades y vacaciones")
public class AusenciaControlador extends CrudBaseControlador<Ausencia> {

    private static final Set<String> TIPOS = Set.of("licencia", "dia_libre", "incapacidad", "vacaciones");

    private final AusenciaRepositorio ausenciaRepo;

    public AusenciaControlador(AusenciaRepositorio repo) {
        super(repo, "idAusencia");
        this.ausenciaRepo = repo;
    }

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Ausencia crear(@Valid @RequestBody Ausencia cuerpo) {
        validar(cuerpo);
        return super.crear(cuerpo);
    }

    @Override
    @PutMapping("/{id}")
    public Ausencia actualizar(@PathVariable Integer id, @Valid @RequestBody Ausencia cuerpo) {
        validar(cuerpo);
        return super.actualizar(id, cuerpo);
    }

    @GetMapping("/barbero/{idBarbero}")
    public List<Ausencia> porBarbero(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @org.springframework.web.bind.annotation.RequestAttribute("sesion") com.milan.barberia.servicios.AutenticacionServicio.Sesion sesion,
                                   @PathVariable Integer idBarbero) {
        if (!sesion.puedeVerBarbero(idBarbero)) {
            throw new com.milan.barberia.excepciones.ExcepcionAccesoDenegado("Solo puedes ver tu propio horario");
        }
        return ausenciaRepo.findByIdBarbero(idBarbero);
    }

    private void validar(Ausencia a) {
        if (!TIPOS.contains(a.getTipo())) {
            throw new ExcepcionNegocio("Tipo inválido. Usa: licencia, dia_libre, incapacidad o vacaciones");
        }
        if (a.getFechaFin().isBefore(a.getFechaInicio())) {
            throw new ExcepcionNegocio("La fecha final no puede ser anterior a la inicial");
        }
    }
}
