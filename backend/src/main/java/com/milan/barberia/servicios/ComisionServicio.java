package com.milan.barberia.servicios;

import com.milan.barberia.excepciones.ExcepcionNegocio;
import com.milan.barberia.repositorios.ReporteRepositorio;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Liquidación de comisiones (procedimiento sp_comisiones). */
@Service
@RequiredArgsConstructor
public class ComisionServicio {

    private final ReporteRepositorio reportes;

    public List<Map<String, Object>> calcular(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new ExcepcionNegocio("Indica las fechas desde y hasta");
        }
        if (hasta.isBefore(desde)) {
            throw new ExcepcionNegocio("La fecha final no puede ser anterior a la inicial");
        }
        return reportes.comisiones(desde, hasta);
    }

    /** Lo mismo que {@link #calcular} más los totales que muestra la pantalla de Comisiones. */
    public Map<String, Object> resumen(LocalDate desde, LocalDate hasta) {
        List<Map<String, Object>> filas = calcular(desde, hasta);
        BigDecimal generado = BigDecimal.ZERO;
        BigDecimal liquidar = BigDecimal.ZERO;
        for (Map<String, Object> f : filas) {
            generado = generado.add(ConversorDatos.aDecimal(f.get("ventas_generadas")));
            liquidar = liquidar.add(ConversorDatos.aDecimal(f.get("comision_total")));
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("desde", desde);
        r.put("hasta", hasta);
        r.put("totalGenerado", generado);
        r.put("totalALiquidar", liquidar);
        r.put("barberos", filas);
        return r;
    }
}
