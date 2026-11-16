package com.vento.pos.service;

import com.vento.pos.dto.CompraRequestDTO;
import com.vento.pos.dto.CompraResponseDTO;
import com.vento.pos.dto.DetalleCompraRequestDTO;
import com.vento.pos.dto.DetalleCompraResponseDTO;
import com.vento.pos.entity.Compra;
import com.vento.pos.entity.DetalleCompra;
import com.vento.pos.entity.MovimientoInventario;
import com.vento.pos.entity.Producto;
import com.vento.pos.entity.Proveedor;
import com.vento.pos.entity.TipoMovimiento;
import com.vento.pos.entity.TipoOperacion;
import com.vento.pos.entity.Usuario;
import com.vento.pos.repository.CompraRepository;
import com.vento.pos.repository.MovimientoInventarioRepository;
import com.vento.pos.repository.ProductoRepository;
import com.vento.pos.repository.ProveedorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class CompraService {

    private final CompraRepository compraRepository;
    private final ProveedorRepository proveedorRepository;
    private final ProductoRepository productoRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final FolioInternoService folioInternoService;

    public CompraService(
            CompraRepository compraRepository,
            ProveedorRepository proveedorRepository,
            ProductoRepository productoRepository,
            MovimientoInventarioRepository movimientoRepository,
            FolioInternoService folioInternoService) {
        this.compraRepository = compraRepository;
        this.proveedorRepository = proveedorRepository;
        this.productoRepository = productoRepository;
        this.movimientoRepository = movimientoRepository;
        this.folioInternoService = folioInternoService;
    }

    @Transactional
    public CompraResponseDTO registrar(CompraRequestDTO request, Usuario usuario) {
        Proveedor proveedor = proveedorRepository.findByIdAndActivoTrue(request.proveedorId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No existe el proveedor activo " + request.proveedorId() + "."));
        String folio = folioInternoService.generar(TipoOperacion.CP);
        BigDecimal total = BigDecimal.ZERO;
        List<DetalleCompra> detalles = new ArrayList<>();
        List<MovimientoInventario> movimientos = new ArrayList<>();

        List<DetalleCompraRequestDTO> items = request.items().stream()
                .sorted(Comparator.comparing(DetalleCompraRequestDTO::productoId))
                .toList();
        for (DetalleCompraRequestDTO item : items) {
            Producto producto = productoRepository.findByIdForUpdate(item.productoId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "No existe el producto " + item.productoId() + "."));
            if (!Boolean.TRUE.equals(producto.getActivo())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "No se puede comprar el producto inactivo " + producto.getNombre() + ".");
            }
            int stockAnterior = producto.getStock();
            int stockNuevo;
            try {
                stockNuevo = Math.addExact(stockAnterior, item.cantidad());
            } catch (ArithmeticException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad solicitada es demasiado grande.");
            }

            BigDecimal costo = item.costoUnitario().setScale(2, RoundingMode.HALF_UP);
            BigDecimal subtotal = costo.multiply(BigDecimal.valueOf(item.cantidad())).setScale(2, RoundingMode.HALF_UP);
            total = total.add(subtotal);
            producto.setStock(stockNuevo);
            detalles.add(new DetalleCompra(producto, item.cantidad(), costo, subtotal));
            movimientos.add(new MovimientoInventario(
                    folioInternoService.generar(TipoOperacion.TME),
                    folio,
                    producto,
                    usuario,
                    item.cantidad(),
                    TipoMovimiento.ENTRADA,
                    TipoOperacion.CP,
                    "Compra a proveedor " + proveedor.getNombreComercial(),
                    stockAnterior,
                    stockNuevo));
        }

        Compra compra = new Compra(
                folio,
                limpiar(request.numeroFacturaProveedor()),
                proveedor,
                total.setScale(2, RoundingMode.HALF_UP),
                usuario.getId());
        detalles.forEach(compra::agregarDetalle);
        compra = compraRepository.save(compra);
        movimientoRepository.saveAll(movimientos);
        return convertir(compra);
    }

    @Transactional(readOnly = true)
    public List<CompraResponseDTO> listar() {
        return compraRepository.findAllByOrderByFechaDescIdDesc().stream().map(CompraService::convertir).toList();
    }

    @Transactional(readOnly = true)
    public CompraResponseDTO obtener(Long id) {
        Compra compra = compraRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la compra " + id + "."));
        return convertir(compra);
    }

    private static String limpiar(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static CompraResponseDTO convertir(Compra compra) {
        List<DetalleCompraResponseDTO> detalles = compra.getDetalles().stream()
                .map(detalle -> new DetalleCompraResponseDTO(
                        detalle.getId(),
                        detalle.getProducto().getId(),
                        detalle.getProducto().getNombre(),
                        detalle.getCantidad(),
                        detalle.getCostoUnitario(),
                        detalle.getSubtotal()))
                .toList();
        return new CompraResponseDTO(
                compra.getId(), compra.getFolioInterno(), compra.getNumeroFacturaProveedor(),
                compra.getProveedor().getId(), compra.getProveedor().getNombreComercial(),
                compra.getFecha(), compra.getTotal(), compra.getUsuarioId(), detalles);
    }
}
