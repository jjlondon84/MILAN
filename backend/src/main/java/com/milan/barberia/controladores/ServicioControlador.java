package com.milan.barberia.controladores;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.milan.barberia.entidades.Servicio;
import com.milan.barberia.repositorios.ServicioRepositorio;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Catálogo de servicios: /api/servicios */
@RestController
@RequestMapping("/api/servicios")
@Tag(name = "Servicios", description = "Catálogo de servicios: precio, duración y % de comisión")
public class ServicioControlador extends CrudBaseControlador<Servicio> {
    public ServicioControlador(ServicioRepositorio repo) {
        super(repo, "idServicio");
    }
}
