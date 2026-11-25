package com.milan.barberia.entidades;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Sesión iniciada. Solo se guarda el SHA-256 del token, nunca el token. */
@Entity
@Table(name = "sesion")
@Getter
@Setter
@NoArgsConstructor
public class Sesion {

    @Id
    private String tokenHash;

    private Integer idUsuario;

    private LocalDateTime fechaCreacion = LocalDateTime.now();

    private LocalDateTime fechaExpiracion;
}
