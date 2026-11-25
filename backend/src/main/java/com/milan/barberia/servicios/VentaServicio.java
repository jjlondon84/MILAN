package com.milan.barberia.servicios;

import com.milan.barberia.excepciones.ExcepcionNegocio;
import com.milan.barberia.entidades.*;
import com.milan.barberia.repositorios.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/** Cobro de una atención (cita) y/o de productos, con uno o varios medios de pago. */
@Service
@RequiredArgsConstructor
public class VentaServicio {

    private static final Set<String> MEDIOS = Set.of("efectivo", "transferencia", "tarjeta");

    public record ItemProducto(@NotNull Integer idProducto, @NotNull @Min(1) Integer cantidad) {}

    public record PagoRequest(@NotBlank String medioPago,
                              @NotNull @DecimalMin("0.01") BigDecimal monto,
                              String referencia) {}

    public record VentaRequest(@NotNull Integer idCaja,
                               Integer idCita,
                               Integer idCliente,
                               Integer idBarbero,
                               List<@Valid ItemProducto> productos,
                               @NotEmpty List<@Valid PagoRequest> pagos) {}

    public record VentaResponse(Venta venta, List<DetalleVenta> detalles, List<Pago> pagos) {}

    private final VentaRepositorio ventaRepo;
    private final DetalleVentaRepositorio detalleRepo;
    private final PagoRepositorio pagoRepo;
    private final CajaRepositorio cajaRepo;
    private final CitaRepositorio citaRepo;
    private final CitaServicioRepositorio citaServicioRepo;
    private final ProductoRepositorio productoRepo;

    @Transactional
    public VentaResponse registrar(VentaRequest r) {
        Caja caja = cajaRepo.findById(r.idCaja())
                .orElseThrow(() -> new ExcepcionNegocio("La caja no existe"));
        if (!"abierta".equals(caja.getEstado())) {
            throw new ExcepcionNegocio("La caja está cerrada. Abre una caja para cobrar");
        }

        BigDecimal total = BigDecimal.ZERO;
        Integer idCliente = r.idCliente();
        Integer idBarbero = r.idBarbero();
        Cita cita = null;

        // 1) Servicios de la cita
        if (r.idCita() != null) {
            cita = citaRepo.findById(r.idCita())
                    .orElseThrow(() -> new ExcepcionNegocio("La cita no existe"));
            if (ventaRepo.existsByIdCitaAndEstado(cita.getIdCita(), "pagada")) {
                throw new ExcepcionNegocio("Esa cita ya fue cobrada");
            }
            if (!List.of("programada", "confirmada", "en_atencion", "completada").contains(cita.getEstado())) {
                throw new ExcepcionNegocio("No se puede cobrar una cita " + cita.getEstado());
            }
            for (CitaServicio cs : citaServicioRepo.findByIdCita(cita.getIdCita())) {
                total = total.add(cs.getPrecioAplicado());
            }
            if (idCliente == null) idCliente = cita.getIdCliente();
            if (idBarbero == null) idBarbero = cita.getIdBarbero();
        }

        // 2) Productos
        List<DetalleVenta> detalles = new ArrayList<>();
        if (r.productos() != null) {
            for (ItemProducto item : r.productos()) {
                Producto p = productoRepo.findById(item.idProducto())
                        .orElseThrow(() -> new ExcepcionNegocio("El producto " + item.idProducto() + " no existe"));
                if (!"venta".equals(p.getTipo()) || !Boolean.TRUE.equals(p.getActivo())) {
                    throw new ExcepcionNegocio("«" + p.getNombre() + "» no está disponible para la venta");
                }
                if (p.getStockActual() < item.cantidad()) {
                    throw new ExcepcionNegocio("Stock insuficiente de «" + p.getNombre()
                            + "»: quedan " + p.getStockActual());
                }
                DetalleVenta d = new DetalleVenta();
                d.setIdProducto(p.getIdProducto());
                d.setCantidad(item.cantidad());
                d.setPrecioUnitario(p.getPrecioVenta());
                d.setPorcentajeComisionAplicado(p.getPorcentajeComision());
                d.setSubtotal(p.getPrecioVenta().multiply(BigDecimal.valueOf(item.cantidad())));
                total = total.add(d.getSubtotal());
                detalles.add(d);
            }
        }

        if (total.signum() <= 0) {
            throw new ExcepcionNegocio("La venta no tiene servicios ni productos para cobrar");
        }

        // 3) Pagos: deben sumar exactamente el total
        BigDecimal pagado = BigDecimal.ZERO;
        for (PagoRequest p : r.pagos()) {
            if (!MEDIOS.contains(p.medioPago())) {
                throw new ExcepcionNegocio("Medio de pago inválido. Usa: efectivo, transferencia o tarjeta");
            }
            pagado = pagado.add(p.monto());
        }
        if (pagado.compareTo(total) != 0) {
            throw new ExcepcionNegocio("La suma de los pagos (" + pagado + ") no coincide con el total (" + total + ")");
        }

        // 4) Guardar todo (si algo falla, no se guarda nada)
        Venta venta = new Venta();
        venta.setIdCaja(caja.getIdCaja());
        venta.setIdCita(cita != null ? cita.getIdCita() : null);
        venta.setIdCliente(idCliente);
        venta.setIdBarbero(idBarbero);
        venta.setTotal(total);
        venta = ventaRepo.save(venta);

        for (DetalleVenta d : detalles) {
            d.setIdVenta(venta.getIdVenta());
            detalleRepo.save(d); // el trigger descuenta el stock
        }
        List<Pago> pagos = new ArrayList<>();
        for (PagoRequest p : r.pagos()) {
            Pago pago = new Pago();
            pago.setIdVenta(venta.getIdVenta());
            pago.setMedioPago(p.medioPago());
            pago.setMonto(p.monto());
            pago.setReferencia(p.referencia());
            pagos.add(pagoRepo.save(pago));
        }
        if (cita != null && !"completada".equals(cita.getEstado())) {
            cita.setEstado("completada");
            citaRepo.save(cita);
        }
        return new VentaResponse(venta, detalles, pagos);
    }

    @Transactional
    public Venta anular(Integer idVenta) {
        Venta v = ventaRepo.findById(idVenta)
                .orElseThrow(() -> new NoSuchElementException("No existe la venta " + idVenta));
        if (!"pagada".equals(v.getEstado())) {
            throw new ExcepcionNegocio("La venta ya está anulada");
        }
        Caja caja = cajaRepo.findById(v.getIdCaja()).orElseThrow();
        if (!"abierta".equals(caja.getEstado())) {
            throw new ExcepcionNegocio("No se puede anular: la caja de esa venta ya fue cerrada");
        }
        v.setEstado("anulada"); // el trigger devuelve los productos al stock
        return ventaRepo.save(v);
    }

    public VentaResponse obtener(Integer idVenta) {
        Venta v = ventaRepo.findById(idVenta)
                .orElseThrow(() -> new NoSuchElementException("No existe la venta " + idVenta));
        return new VentaResponse(v, detalleRepo.findByIdVenta(idVenta), pagoRepo.findByIdVenta(idVenta));
    }

    public List<Venta> listar(Integer idCaja) {
        return idCaja != null ? ventaRepo.findByIdCajaOrderByFechaHoraDesc(idCaja)
                : ventaRepo.findAll(org.springframework.data.domain.Sort.by("fechaHora").descending());
    }
}
