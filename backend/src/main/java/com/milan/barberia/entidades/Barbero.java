package com.milan.barberia.entidades;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "barbero")
@Getter
@Setter
@NoArgsConstructor
public class Barbero {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idBarbero;

    private Integer idUsuario;

    /** Puede quedar vacío si la ficha se creó desde "Crear cuenta"; lo completa el administrador. */
    private String documento;

    @NotBlank
    private String nombres;

    @NotBlank
    private String apellidos;

    private String telefono;

    @NotNull
    private LocalDate fechaIngreso;

    private String estado = "activo";
}
