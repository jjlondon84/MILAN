package com.milan.barberia.seguridad;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Cifra el campo al guardarlo en MySQL y lo descifra al leerlo.
 * Uso en una entidad:  {@code @Convert(converter = ConvertidorCampoCifrado.class)}
 */
@Converter
public class ConvertidorCampoCifrado implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String valor) {
        return CifradoServicio.instancia().cifrar(valor);
    }

    @Override
    public String convertToEntityAttribute(String columna) {
        return CifradoServicio.instancia().descifrar(columna);
    }
}
