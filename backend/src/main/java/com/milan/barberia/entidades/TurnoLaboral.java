package com.milan.barberia.entidades;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

@Entity
@Table(name = "turno_laboral")
@Getter
@Setter
@NoArgsConstructor
public class TurnoLaboral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idTurno;

    @NotNull
    private Integer idBarbero;

    @NotNull
    @Min(1)
    @Max(7)
    private Integer diaSemana;

    @NotNull
    private LocalTime horaInicio;

    @NotNull
    private LocalTime horaFin;

    private LocalTime descansoInicio;

    private LocalTime descansoFin;
}
