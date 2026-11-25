package com.milan.barberia.servicios;

import com.milan.barberia.repositorios.BarberoRepositorio;
import com.milan.barberia.repositorios.ClienteRepositorio;
import com.milan.barberia.repositorios.ProductoRepositorio;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Buscador global de la barra superior: clientes, barberos y productos. */
@Service
@RequiredArgsConstructor
public class BusquedaServicio {

    private final ClienteRepositorio clienteRepo;
    private final BarberoRepositorio barberoRepo;
    private final ProductoRepositorio productoRepo;

    public Map<String, Object> buscar(String q) {
        String t = q == null ? "" : q.trim();
        Map<String, Object> r = new LinkedHashMap<>();
        if (t.length() < 2) {
            r.put("clientes", List.of());
            r.put("barberos", List.of());
            r.put("productos", List.of());
            return r;
        }
        r.put("clientes", clienteRepo
                .findByNombresContainingIgnoreCaseOrApellidosContainingIgnoreCaseOrTelefonoContaining(t, t, t)
                .stream().limit(6).toList());
        r.put("barberos", barberoRepo.findByNombresContainingIgnoreCaseOrApellidosContainingIgnoreCase(t, t)
                .stream().limit(6).toList());
        r.put("productos", productoRepo.findByNombreContainingIgnoreCase(t).stream().limit(6).toList());
        return r;
    }
}
