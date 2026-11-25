package com.milan.barberia.entidades;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "detalle_venta")
@Getter
@Setter
@NoArgsConstructor
public class DetalleVenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idDetalle;

    private Integer idVenta;

    private Integer idProducto;

    private Integer cantidad;

    private BigDecimal precioUnitario;

    private BigDecimal porcentajeComisionAplicado = BigDecimal.ZERO;

    @Column(insertable = false, updatable = false)
    private BigDecimal subtotal;
}
