package com.milan.barberia.entidades;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Servicio incluido en una cita, con el precio y la comisión vigentes ese día. */
@Entity
@Table(name = "cita_servicio")
@IdClass(CitaServicioId.class)
@Getter
@Setter
@NoArgsConstructor
public class CitaServicio {

    @Id
    private Integer idCita;

    @Id
    private Integer idServicio;

    private BigDecimal precioAplicado;

    private BigDecimal porcentajeComisionAplicado;
}
