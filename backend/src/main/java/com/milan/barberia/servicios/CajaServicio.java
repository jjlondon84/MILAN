package com.milan.barberia.servicios;

import com.milan.barberia.excepciones.ExcepcionNegocio;
import com.milan.barberia.entidades.Caja;
import com.milan.barberia.repositorios.CajaRepositorio;
import lombok.RequiredArgsConstructor;
import com.milan.barberia.repositorios.ReporteRepositorio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/** Apertura y cierre de caja con arqueo (efectivo esperado vs. contado). */
@Service
@RequiredArgsConstructor
public class CajaServicio {

    private final CajaRepositorio cajaRepo;
    private final ReporteRepositorio reportes;

    @Transactional
    public Caja abrir(Integer idUsuario, BigDecimal montoApertura) {
        if (cajaRepo.findFirstByEstado("abierta").isPresent()) {
            throw new ExcepcionNegocio("Ya hay una caja abierta. Ciérrala antes de abrir otra");
        }
        Caja c = new Caja();
        c.setIdUsuario(idUsuario);
        c.setMontoApertura(montoApertura == null ? BigDecimal.ZERO : montoApertura);
        return cajaRepo.save(c);
    }

    /** Caja abierta o null (para el frontend, que no debe recibir un error si no hay caja). */
    public Caja actualOpcional() {
        return cajaRepo.findFirstByEstado("abierta").orElse(null);
    }

    public Caja actual() {
        return cajaRepo.findFirstByEstado("abierta")
                .orElseThrow(() -> new NoSuchElementException("No hay ninguna caja abierta"));
    }

    public List<Caja> listar() {
        return cajaRepo.findAllByOrderByFechaHoraAperturaDesc();
    }

    @Transactional
    public Map<String, Object> cerrar(Integer idCaja, BigDecimal efectivoContado, String observaciones) {
        Caja c = cajaRepo.findById(idCaja)
                .orElseThrow(() -> new NoSuchElementException("No existe la caja " + idCaja));
        if (!"abierta".equals(c.getEstado())) {
            throw new ExcepcionNegocio("Esa caja ya está cerrada");
        }
        c.setEfectivoContado(efectivoContado);
        c.setFechaHoraCierre(LocalDateTime.now());
        c.setEstado("cerrada");
        c.setObservaciones(observaciones);
        cajaRepo.saveAndFlush(c);
        return arqueo(idCaja);
    }

    /** Lee la vista v_arqueo_caja de la base de datos. */
    public Map<String, Object> arqueo(Integer idCaja) {
        List<Map<String, Object>> filas = reportes.arqueo(idCaja);
        if (filas.isEmpty()) {
            throw new NoSuchElementException("No existe la caja " + idCaja);
        }
        return filas.get(0);
    }
}
