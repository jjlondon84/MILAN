package com.milan.barberia.entidades;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "caja")
@Getter
@Setter
@NoArgsConstructor
public class Caja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idCaja;

    private Integer idUsuario;

    private LocalDateTime fechaHoraApertura = LocalDateTime.now();

    private BigDecimal montoApertura = BigDecimal.ZERO;

    private LocalDateTime fechaHoraCierre;

    private BigDecimal efectivoContado;

    private String estado = "abierta";

    private String observaciones;
}
