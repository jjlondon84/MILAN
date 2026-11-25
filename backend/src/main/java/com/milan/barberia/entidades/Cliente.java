package com.milan.barberia.entidades;

import com.milan.barberia.seguridad.ConvertidorCampoCifrado;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "cliente")
@Getter
@Setter
@NoArgsConstructor
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idCliente;

    @NotBlank
    private String nombres;

    @NotBlank
    private String apellidos;

    @NotBlank
    private String telefono;

    /** Correo cifrado con AES-256-GCM en la base de datos. */
    @Email(message = "Correo inválido")
    @Convert(converter = ConvertidorCampoCifrado.class)
    private String email;

    private String preferenciasCorte;

    private LocalDate fechaRegistro = LocalDate.now();
}
