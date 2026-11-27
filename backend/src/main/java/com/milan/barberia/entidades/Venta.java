package com.milan.barberia.entidades;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "venta")
@Getter
@Setter
@NoArgsConstructor
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idVenta;

    private Integer idCaja;

    private Integer idCita;

    private Integer idCliente;

    private Integer idBarbero;

    private LocalDateTime fechaHora = LocalDateTime.now();

    private BigDecimal total;

    /** Propina cobrada en caja; es 100% del barbero. Los pagos suman total + propina. */
    private BigDecimal propina = BigDecimal.ZERO;

    private String estado = "pagada";
}
