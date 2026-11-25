package com.milan.barberia.servicios;

import com.milan.barberia.entidades.Cliente;
import com.milan.barberia.repositorios.ClienteRepositorio;
import com.milan.barberia.repositorios.ReporteRepositorio;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Ficha de clientes con historial (visitas, última visita y próxima cita). */
@Service
@RequiredArgsConstructor
public class ClienteServicio {

    public record ClienteResumen(Integer idCliente, String nombres, String apellidos, String telefono,
                                 String email, String preferenciasCorte, LocalDate fechaRegistro,
                                 long visitas, LocalDateTime ultimaVisita, LocalDateTime proximaCita) {}

    private final ClienteRepositorio clienteRepo;
    private final ReporteRepositorio reportes;

    public List<ClienteResumen> resumen(String q) {
        List<Cliente> clientes = (q == null || q.isBlank())
                ? clienteRepo.findAll()
                : clienteRepo.findByNombresContainingIgnoreCaseOrApellidosContainingIgnoreCaseOrTelefonoContaining(q, q, q);

        Map<Integer, Map<String, Object>> datos = new HashMap<>();
        for (Map<String, Object> fila : reportes.resumenClientes()) {
            datos.put((int) ConversorDatos.aLong(fila.get("id_cliente")), fila);
        }
        return clientes.stream()
                .map(c -> {
                    Map<String, Object> f = datos.getOrDefault(c.getIdCliente(), Map.of());
                    return new ClienteResumen(c.getIdCliente(), c.getNombres(), c.getApellidos(), c.getTelefono(),
                            c.getEmail(), c.getPreferenciasCorte(), c.getFechaRegistro(),
                            ConversorDatos.aLong(f.get("visitas")), ConversorDatos.aFechaHora(f.get("ultima_visita")),
                            ConversorDatos.aFechaHora(f.get("proxima_cita")));
                })
                .sorted(Comparator.comparing(ClienteResumen::ultimaVisita,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }
}
