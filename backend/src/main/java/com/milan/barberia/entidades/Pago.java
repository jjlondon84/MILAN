package com.milan.barberia.entidades;

import com.milan.barberia.seguridad.ConvertidorCampoCifrado;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "pago")
@Getter
@Setter
@NoArgsConstructor
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idPago;

    private Integer idVenta;

    private String medioPago;

    private BigDecimal monto;

    /** N° de transferencia o voucher: dato financiero, se guarda cifrado (AES-256-GCM). */
    @Convert(converter = ConvertidorCampoCifrado.class)
    private String referencia;
}
