package com.milan.barberia.entidades;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "movimiento_inventario")
@Getter
@Setter
@NoArgsConstructor
public class MovimientoInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idMovimiento;

    private Integer idProducto;

    private Integer idUsuario;

    private String tipo;

    private Integer cantidad;

    private String motivo;

    private LocalDateTime fechaHora = LocalDateTime.now();
}
