package com.milan.barberia.entidades;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cita")
@Getter
@Setter
@NoArgsConstructor
public class Cita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idCita;

    private Integer idCliente;

    private Integer idBarbero;

    private LocalDateTime fechaHoraInicio;

    private LocalDateTime fechaHoraFin;

    private String estado = "programada";

    private String observaciones;

    /** Hora en que el barbero pulsó "Terminé". */
    private LocalDateTime fechaHoraFinReal;

    /** Propina que el cliente le dio directo al barbero (100% suya). */
    private BigDecimal propina = BigDecimal.ZERO;
}
