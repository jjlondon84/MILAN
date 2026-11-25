package com.milan.barberia.entidades;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "producto")
@Getter
@Setter
@NoArgsConstructor
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idProducto;

    @NotBlank
    private String nombre;

    @NotBlank
    private String tipo;

    private BigDecimal precioVenta;

    private BigDecimal porcentajeComision = BigDecimal.ZERO;

    private Integer stockActual = 0;

    private Integer stockMinimo = 0;

    private Boolean activo = true;
}
