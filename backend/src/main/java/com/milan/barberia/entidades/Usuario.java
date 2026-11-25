package com.milan.barberia.entidades;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.milan.barberia.seguridad.ConvertidorCampoCifrado;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idUsuario;

    @NotNull
    private Integer idRol;

    @NotBlank
    private String nombreUsuario;

    private String nombreCompleto;

    /** Correo cifrado con AES-256-GCM en la base de datos. */
    @Convert(converter = ConvertidorCampoCifrado.class)
    private String email;

    /** SHA-256 del correo en minúsculas: permite buscarlo sin descifrar. */
    @JsonIgnore
    private String emailHash;

    @JsonIgnore
    private String contrasenaHash;

    private Boolean activo = true;

    private LocalDateTime fechaRegistro = LocalDateTime.now();
}
