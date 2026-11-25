package com.milan.barberia.entidades;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Solicitud de restablecimiento de contraseña (token de un solo uso con vencimiento). */
@Entity
@Table(name = "recuperacion_contrasena")
@Getter
@Setter
@NoArgsConstructor
public class RecuperacionContrasena {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idRecuperacion;

    private Integer idUsuario;

    private String tokenHash;

    private LocalDateTime fechaCreacion = LocalDateTime.now();

    private LocalDateTime fechaExpiracion;

    private Boolean usado = false;
}
