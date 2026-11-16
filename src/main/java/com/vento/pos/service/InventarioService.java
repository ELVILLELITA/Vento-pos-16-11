package com.vento.pos.service;

import com.vento.pos.dto.AjusteInventarioRequestDTO;
import com.vento.pos.dto.MovimientoInventarioResponseDTO;
import com.vento.pos.entity.MovimientoInventario;
import com.vento.pos.entity.Producto;
import com.vento.pos.entity.TipoMovimiento;
import com.vento.pos.entity.TipoOperacion;
import com.vento.pos.entity.Usuario;
import com.vento.pos.repository.MovimientoInventarioRepository;
import com.vento.pos.repository.ProductoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InventarioService {

    private final ProductoRepository productoRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final FolioInternoService folioInternoService;

    public InventarioService(
            ProductoRepository productoRepository,
            MovimientoInventarioRepository movimientoRepository,
            FolioInternoService folioInternoService) {
        this.productoRepository = productoRepository;
        this.movimientoRepository = movimientoRepository;
        this.folioInternoService = folioInternoService;
    }

    @Transactional
    public MovimientoInventarioResponseDTO registrarAjuste(
            Long productoId,
            Integer cantidad,
            TipoMovimiento tipoMov,
            TipoOperacion tipoOp,
            String motivo,
            Usuario admin) {
        if (cantidad == null || cantidad <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad debe ser mayor que cero.");
        }
        if (tipoMov == null || tipoMov == TipoMovimiento.VENTA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El ajuste debe ser de tipo ENTRADA o SALIDA.");
        }
        if (tipoOp == null || tipoOp == TipoOperacion.CA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La operación no es válida para un ajuste manual.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El motivo del movimiento es obligatorio.");
        }

        Producto producto = productoRepository.findByIdForUpdate(productoId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No existe el producto " + productoId + "."));
        if (!Boolean.TRUE.equals(producto.getActivo())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede ajustar un producto inactivo.");
        }

        int stockAnterior = producto.getStock();
        int stockNuevo;
        try {
            stockNuevo = tipoMov == TipoMovimiento.ENTRADA
                    ? Math.addExact(stockAnterior, cantidad)
                    : Math.subtractExact(stockAnterior, cantidad);
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad solicitada es demasiado grande.");
        }
        if (stockNuevo < 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Stock insuficiente para realizar la salida. Disponible: " + stockAnterior + ".");
        }
        producto.setStock(stockNuevo);

        MovimientoInventario movimiento = new MovimientoInventario(
                folioInternoService.generar(tipoOp),
                null,
                producto,
                admin,
                cantidad,
                tipoMov,
                tipoOp,
                motivo.trim(),
                stockAnterior,
                stockNuevo);
        return convertir(movimientoRepository.save(movimiento));
    }

    @Transactional
    public void registrarVenta(Producto producto, int cantidad, int stockAnterior, String folioVenta, Usuario usuario) {
        MovimientoInventario movimiento = new MovimientoInventario(
                folioInternoService.generar(TipoOperacion.TME),
                folioVenta,
                producto,
                usuario,
                cantidad,
                TipoMovimiento.VENTA,
                TipoOperacion.CA,
                "Venta " + folioVenta,
                stockAnterior,
                producto.getStock());
        movimientoRepository.save(movimiento);
    }

    @Transactional
    public void registrarDevolucion(
            Long productoId,
            int cantidad,
            String folioVenta,
            String folioDevolucion,
            String motivo,
            Usuario usuario) {
        Producto producto = productoRepository.findByIdForUpdate(productoId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No existe el producto " + productoId + "."));
        int stockAnterior = producto.getStock();
        int stockNuevo;
        try {
            stockNuevo = Math.addExact(stockAnterior, cantidad);
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad solicitada es demasiado grande.");
        }
        producto.setStock(stockNuevo);
        movimientoRepository.save(new MovimientoInventario(
                folioInternoService.generar(TipoOperacion.TME),
                folioDevolucion == null ? folioVenta : folioDevolucion,
                producto,
                usuario,
                cantidad,
                TipoMovimiento.ENTRADA,
                TipoOperacion.DE,
                "Devolución " + folioDevolucion + " de venta " + folioVenta + ": " + motivo.trim(),
                stockAnterior,
                stockNuevo));
    }

    @Transactional(readOnly = true)
    public List<MovimientoInventarioResponseDTO> listarMovimientos(
            Long productoId,
            TipoMovimiento tipo,
            TipoOperacion tipoOperacion,
            LocalDateTime desde,
            LocalDateTime hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha inicial debe ser anterior a la fecha final.");
        }
        return movimientoRepository.buscar(productoId, tipo, tipoOperacion, desde, hasta)
                .stream()
                .map(InventarioService::convertir)
                .toList();
    }

    private static MovimientoInventarioResponseDTO convertir(MovimientoInventario movimiento) {
        return new MovimientoInventarioResponseDTO(
                movimiento.getId(),
                movimiento.getFolioMovimiento(),
                movimiento.getFolioReferencia(),
                movimiento.getProducto().getId(),
                movimiento.getProducto().getNombre(),
                movimiento.getTipo(),
                movimiento.getTipoOperacion(),
                movimiento.getCantidad(),
                movimiento.getStockAnterior(),
                movimiento.getStockNuevo(),
                movimiento.getMotivo(),
                movimiento.getFecha(),
                movimiento.getUsuario() == null ? null : movimiento.getUsuario().getUsername());
    }
}
