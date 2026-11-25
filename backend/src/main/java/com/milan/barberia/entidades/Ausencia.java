package com.milan.barberia.entidades;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "ausencia")
@Getter
@Setter
@NoArgsConstructor
public class Ausencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idAusencia;

    @NotNull
    private Integer idBarbero;

    @NotBlank
    private String tipo;

    @NotNull
    private LocalDate fechaInicio;

    @NotNull
    private LocalDate fechaFin;

    private String motivo;
}
