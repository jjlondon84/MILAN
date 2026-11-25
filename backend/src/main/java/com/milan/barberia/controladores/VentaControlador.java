package com.milan.barberia.controladores;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.milan.barberia.entidades.Venta;
import com.milan.barberia.servicios.VentaServicio;
import com.milan.barberia.servicios.VentaServicio.VentaRequest;
import com.milan.barberia.servicios.VentaServicio.VentaResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ventas")
@Tag(name = "Ventas", description = "Cobro de citas y productos con pagos múltiples")
@RequiredArgsConstructor
public class VentaControlador {

    private final VentaServicio servicio;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VentaResponse registrar(@Valid @RequestBody VentaRequest r) {
        return servicio.registrar(r);
    }

    @GetMapping
    public List<Venta> listar(@RequestParam(required = false) Integer idCaja) {
        return servicio.listar(idCaja);
    }

    @GetMapping("/{id}")
    public VentaResponse obtener(@PathVariable Integer id) {
        return servicio.obtener(id);
    }

    @PatchMapping("/{id}/anular")
    public Venta anular(@PathVariable Integer id) {
        return servicio.anular(id);
    }
}
