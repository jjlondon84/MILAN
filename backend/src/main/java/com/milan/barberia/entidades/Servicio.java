package com.milan.barberia.entidades;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "servicio")
@Getter
@Setter
@NoArgsConstructor
public class Servicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idServicio;

    @NotBlank
    private String nombre;

    @NotBlank
    private String categoria;

    @NotNull
    @DecimalMin("0")
    private BigDecimal precio;

    @NotNull
    @Min(1)
    private Integer duracionMin;

    private BigDecimal porcentajeComision = BigDecimal.ZERO;

    private Boolean activo = true;
}
