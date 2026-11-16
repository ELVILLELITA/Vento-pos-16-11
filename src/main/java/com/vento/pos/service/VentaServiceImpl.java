package com.vento.pos.service;

import com.vento.pos.dto.DetalleVentaDTO;
import com.vento.pos.dto.DetalleVentaResponseDTO;
import com.vento.pos.dto.DevolucionItemDTO;
import com.vento.pos.dto.DevolucionRequestDTO;
import com.vento.pos.dto.DevolucionResponseDTO;
import com.vento.pos.dto.PagoParcialDTO;
import com.vento.pos.dto.VentaRequestDTO;
import com.vento.pos.dto.VentaResponseDTO;
import com.vento.pos.entity.Cliente;
import com.vento.pos.entity.DetalleVenta;
import com.vento.pos.entity.DiscountAuthorization;
import com.vento.pos.entity.DevolucionVenta;
import com.vento.pos.entity.Producto;
import com.vento.pos.entity.Rol;
import com.vento.pos.entity.TipoOperacion;
import com.vento.pos.entity.TipoComprobante;
import com.vento.pos.entity.TipoDocumento;
import com.vento.pos.entity.Turno;
import com.vento.pos.entity.Usuario;
import com.vento.pos.entity.Venta;
import com.vento.pos.repository.DevolucionVentaRepository;
import com.vento.pos.repository.ClienteRepository;
import com.vento.pos.repository.ProductoRepository;
import com.vento.pos.repository.UsuarioRepository;
import com.vento.pos.repository.VentaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class VentaServiceImpl implements VentaService {

    private static final BigDecimal CERO = new BigDecimal("0.00");
    private static final long PRIMER_NUMERO_ORDEN = 327428;
    private static final RoundingMode REDONDEO = RoundingMode.HALF_UP;
    private static final Set<String> METODOS_PAGO = Set.of(
            "Efectivo", "Devolucion", "Devolución", "Cuscatlan", "Nico", "Credomatic", "Mensajeria");

    private final ProductoRepository productoRepository;
    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final VentaRepository ventaRepository;
    private final DevolucionVentaRepository devolucionRepository;
    private final FolioInternoService folioInternoService;
    private final InventarioService inventarioService;
    private final ConfiguracionSistemaService configuracionService;
    private final TurnoService turnoService;
    private final PromotionService promotionService;
    private final DiscountAuthorizationService discountAuthorizationService;
    private final EmailService emailService;

    public VentaServiceImpl(
            ProductoRepository productoRepository,
            ClienteRepository clienteRepository,
            UsuarioRepository usuarioRepository,
            VentaRepository ventaRepository,
            DevolucionVentaRepository devolucionRepository,
            FolioInternoService folioInternoService,
            InventarioService inventarioService,
            ConfiguracionSistemaService configuracionService,
            TurnoService turnoService,
            PromotionService promotionService,
            DiscountAuthorizationService discountAuthorizationService,
            EmailService emailService
    ) {
        this.productoRepository = productoRepository;
        this.clienteRepository = clienteRepository;
        this.usuarioRepository = usuarioRepository;
        this.ventaRepository = ventaRepository;
        this.devolucionRepository = devolucionRepository;
        this.folioInternoService = folioInternoService;
        this.inventarioService = inventarioService;
        this.configuracionService = configuracionService;
        this.turnoService = turnoService;
        this.promotionService = promotionService;
        this.discountAuthorizationService = discountAuthorizationService;
        this.emailService = emailService;
    }

    @Override
    @Transactional
    public VentaResponseDTO crearVenta(VentaRequestDTO request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La venta debe incluir al menos un producto.");
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserDetails principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión para registrar una venta.");
        }
        Turno turno = turnoService.exigirTurnoAbierto(principal.getUsername());
        TipoComprobante tipoComprobante = request.tipoComprobante() == null
                ? TipoComprobante.CONSUMIDOR_FINAL
                : request.tipoComprobante();
        Cliente cliente = request.clienteId() == null
                ? null
                : clienteRepository.findByIdAndActivoTrue(request.clienteId())
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "No existe el cliente " + request.clienteId() + "."));
        if (tipoComprobante == TipoComprobante.CREDITO_FISCAL) {
            if (cliente == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Selecciona un cliente para emitir un crédito fiscal.");
            }
            if (cliente.getNrc() == null || cliente.getNrc().isBlank()
                    || cliente.getGiro() == null || cliente.getGiro().isBlank()
                    || cliente.getDocumento() == null || cliente.getDocumento().isBlank()
                    || (cliente.getTipoDocumento() != TipoDocumento.NIT && cliente.getTipoDocumento() != TipoDocumento.DUI)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "El cliente debe tener NRC, giro y documento DUI o NIT para emitir crédito fiscal.");
            }
        }
        Map<Long, Integer> cantidadesPorProducto = new TreeMap<>();
        Map<Long, BigDecimal> descuentosManualesPorProducto = new TreeMap<>();
        try {
            for (DetalleVentaDTO item : request.items()) {
                cantidadesPorProducto.merge(item.productoId(), item.cantidad(), Math::addExact);
                if (item.descuentoManual() != null && item.descuentoManual().signum() > 0) {
                    descuentosManualesPorProducto.merge(
                            item.productoId(), item.descuentoManual().setScale(2, REDONDEO), BigDecimal::add);
                }
            }
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad solicitada es demasiado grande.");
        }

        BigDecimal descuentoTicketSolicitado = request.descuento() == null
                ? CERO
                : request.descuento().setScale(2, REDONDEO);
        BigDecimal descuentoLineasSolicitado = descuentosManualesPorProducto.values().stream()
                .reduce(CERO, BigDecimal::add).setScale(2, REDONDEO);
        BigDecimal descuentoManualSolicitado = descuentoTicketSolicitado
                .add(descuentoLineasSolicitado).setScale(2, REDONDEO);
        DiscountAuthorization autorizacion = null;
        if (descuentoManualSolicitado.signum() > 0) {
            autorizacion = discountAuthorizationService.consume(
                    request.descuentoAuthorizationToken(), descuentoManualSolicitado);
        } else if (request.descuentoAuthorizationToken() != null
                && !request.descuentoAuthorizationToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La autorización no corresponde a un descuento manual.");
        }
        BigDecimal subtotal = CERO;
        BigDecimal ivaAntesDescuento = CERO;
        BigDecimal tasaIvaConfigurada = obtenerTasaIva();
        BigDecimal tasaIva = request.esExenta() ? CERO : tasaIvaConfigurada;
        List<DetalleVenta> detalles = new ArrayList<>();
        Map<Long, Integer> stockAnteriorPorProducto = new TreeMap<>();
        Map<Long, BigDecimal> basesPorProducto = new TreeMap<>();
        Map<Long, BigDecimal> ivasPorProducto = new TreeMap<>();

        for (Map.Entry<Long, Integer> entrada : cantidadesPorProducto.entrySet()) {
            Producto producto = productoRepository.findByIdForUpdate(entrada.getKey())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "No existe el producto " + entrada.getKey() + "."));
            int cantidad = entrada.getValue();

            if (!Boolean.TRUE.equals(producto.getActivo())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "El producto " + producto.getNombre() + " ya no está disponible.");
            }
            if (producto.getStock() < cantidad) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Stock insuficiente para " + producto.getNombre() + ". Disponible: " + producto.getStock() + ".");
            }

            BigDecimal precioBaseUnitario;
            BigDecimal ivaUnitario;
            BigDecimal precioFinalUnitario;
            if (producto.isPrecioIncluyeIva()) {
                precioBaseUnitario = producto.getPrecio()
                        .divide(BigDecimal.ONE.add(tasaIvaConfigurada), 2, REDONDEO);
                precioFinalUnitario = request.esExenta()
                        ? precioBaseUnitario : producto.getPrecio().setScale(2, REDONDEO);
                ivaUnitario = request.esExenta() ? CERO : precioFinalUnitario.subtract(precioBaseUnitario);
            } else {
                precioBaseUnitario = producto.getPrecio().setScale(2, REDONDEO);
                ivaUnitario = precioBaseUnitario.multiply(tasaIva).setScale(2, REDONDEO);
                precioFinalUnitario = precioBaseUnitario.add(ivaUnitario);
            }
            BigDecimal factorCantidad = BigDecimal.valueOf(cantidad);
            BigDecimal importeBase = precioBaseUnitario.multiply(factorCantidad).setScale(2, REDONDEO);
            BigDecimal importeIva = ivaUnitario.multiply(factorCantidad).setScale(2, REDONDEO);
            BigDecimal importeFinal = precioFinalUnitario.multiply(factorCantidad).setScale(2, REDONDEO);
            subtotal = subtotal.add(importeBase);
            ivaAntesDescuento = ivaAntesDescuento.add(importeIva);
            detalles.add(new DetalleVenta(producto, cantidad, precioFinalUnitario, importeFinal));
            basesPorProducto.put(producto.getId(), importeBase);
            ivasPorProducto.put(producto.getId(), importeIva);
            stockAnteriorPorProducto.put(producto.getId(), producto.getStock());
            producto.setStock(producto.getStock() - cantidad);
        }

        subtotal = subtotal.setScale(2, REDONDEO);
        if (descuentoManualSolicitado.compareTo(subtotal) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El descuento no puede superar el subtotal.");
        }

        List<PromotionEngine.Item> itemsPromocion = detalles.stream()
                .map(detalle -> new PromotionEngine.Item(detalle.getProducto(), detalle.getCantidad(),
                        basesPorProducto.get(detalle.getProducto().getId())
                                .divide(BigDecimal.valueOf(detalle.getCantidad()), 2, REDONDEO)))
                .toList();
        PromotionEngine.Result promocion = promotionService.calculate(itemsPromocion, LocalDateTime.now());
        BigDecimal descuentoAutomatico = promocion.discount().setScale(2, REDONDEO);
        BigDecimal descuento = descuentoAutomatico.add(descuentoManualSolicitado).setScale(2, REDONDEO);
        if (descuento.compareTo(subtotal) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los descuentos no pueden superar el subtotal.");
        }
        Map<Long, BigDecimal> manualPorProducto = asignarDescuentosManuales(
                detalles, basesPorProducto, promocion.discountsByProduct(),
                descuentosManualesPorProducto, descuentoTicketSolicitado);
        detalles.forEach(detalle -> detalle.setDescuentos(
                promocion.discountsByProduct().getOrDefault(detalle.getProducto().getId(), CERO),
                manualPorProducto.getOrDefault(detalle.getProducto().getId(), CERO),
                promocion.promotionName()));

        BigDecimal baseImponible = subtotal.subtract(descuento);
        BigDecimal reduccionIva = detalles.stream().map(detalle -> {
            Long productId = detalle.getProducto().getId();
            BigDecimal base = basesPorProducto.get(productId);
            BigDecimal lineDiscount = detalle.getDescuentoAutomatico().add(detalle.getDescuentoManual());
            return base.signum() == 0 ? CERO : lineDiscount.multiply(ivasPorProducto.get(productId))
                    .divide(base, 2, REDONDEO);
        }).reduce(CERO, BigDecimal::add).setScale(2, REDONDEO);
        BigDecimal iva = ivaAntesDescuento.subtract(reduccionIva).max(CERO).setScale(2, REDONDEO);
        BigDecimal total = baseImponible.add(iva).setScale(2, REDONDEO);
        Venta ventaOrigen = null;
        List<Venta> creditosAplicados = new ArrayList<>();
        validarPagos(request, total, creditosAplicados);
        String folioOrigen = request.folioVentaOrigen() == null ? null : request.folioVentaOrigen().trim();
        String folioDevolucion = request.folioDevolucion() == null ? null : request.folioDevolucion().trim();
        boolean esDevolucion = "Devolucion".equals(request.metodoPago())
                || "Devolución".equals(request.metodoPago());
        String folioCredito = esDevolucion
                ? (folioDevolucion == null || folioDevolucion.isBlank() ? folioOrigen : folioDevolucion)
                : null;
        if (esDevolucion && (folioCredito == null || folioCredito.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El folio de devolución es obligatorio para este método de pago.");
        }
        if (request.pagosParciales() != null && !request.pagosParciales().isEmpty()) {
            ventaOrigen = creditosAplicados.isEmpty() ? null : creditosAplicados.get(0);
            creditosAplicados.forEach(credito -> credito.setCreditoDevolucionConsumido(true));
            if (ventaOrigen == null && folioOrigen != null && !folioOrigen.isBlank()) {
                ventaOrigen = ventaRepository.findByFolioInternoForUpdate(folioOrigen)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "No existe la venta de origen " + folioOrigen + "."));
                validarCreditoDevolucion(ventaOrigen, folioOrigen);
                ventaOrigen.setCreditoDevolucionConsumido(true);
            }
        } else if (esDevolucion) {
            DevolucionVenta devolucionOrigen = devolucionRepository
                    .findByFolioDevolucionForUpdate(folioCredito)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "No existe la devolución " + folioCredito + "."));
            ventaOrigen = devolucionOrigen.getVenta();
            validarCreditoDevolucion(ventaOrigen, folioCredito);
            if (total.compareTo(devolucionOrigen.getImporte()) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El total de la nueva venta debe ser mayor o igual al total devuelto.");
            }
        } else if (folioOrigen != null && !folioOrigen.isBlank()) {
            ventaOrigen = ventaRepository.findByFolioInternoForUpdate(folioOrigen)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "No existe la venta de origen " + folioOrigen + "."));
            validarCreditoDevolucion(ventaOrigen, folioOrigen);
        }
        Venta venta = new Venta(subtotal, iva, descuento, total, "COMPLETADA");
        venta.setDescuentoAutomatico(descuentoAutomatico);
        venta.setDescuentoManual(descuentoManualSolicitado);
        venta.setPromocionAplicada(promocion.promotionName());
        if (autorizacion != null) venta.setDescuentoAprobadoPor(autorizacion.getApprovedBy());
        venta.setMetodoPago(request.metodoPago() == null ? "Efectivo" : request.metodoPago());
        venta.setCompradorNombre(request.compradorNombre() == null || request.compradorNombre().isBlank()
                ? null
                : request.compradorNombre().trim());
        venta.setCliente(cliente);
        venta.setFolioVentaOrigen(ventaOrigen == null ? null : ventaOrigen.getFolioInterno());
        if (ventaOrigen != null) {
            ventaOrigen.setCreditoDevolucionConsumido(true);
        }
        venta.setEsExenta(request.esExenta());
        venta.setTipoComprobante(tipoComprobante);
        Usuario usuarioVenta = turno.getUsuario();
        venta.setUsuario(usuarioVenta);
        String folioVenta = folioInternoService.generar(TipoOperacion.CA);
        venta.setFolioInterno(folioVenta);
        venta.setNumeroOrden("#TMP" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        detalles.forEach(venta::agregarDetalle);
        for (DetalleVenta detalle : detalles) {
            inventarioService.registrarVenta(
                    detalle.getProducto(),
                    detalle.getCantidad(),
                    stockAnteriorPorProducto.get(detalle.getProducto().getId()),
                    folioVenta,
                    usuarioVenta);
        }

        ventaRepository.saveAndFlush(venta);
        venta.setNumeroOrden("#" + (PRIMER_NUMERO_ORDEN + venta.getId()));
        ventaRepository.flush();
        emailService.enviarDte(venta);
        return convertir(venta);
    }

    private void validarPagos(VentaRequestDTO request, BigDecimal total, List<Venta> creditosAplicados) {
        List<PagoParcialDTO> pagos = request.pagosParciales();
        if (pagos == null || pagos.isEmpty()) {
            return;
        }
        if (pagos.size() > 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se permiten hasta 3 pagos por venta.");
        }
        BigDecimal totalPagado = CERO;
        Set<String> foliosUsados = new HashSet<>();
        for (PagoParcialDTO pago : pagos) {
            if (!METODOS_PAGO.contains(pago.metodoPago())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El método de pago no es válido.");
            }
            if (pago.monto() == null || pago.monto().signum() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cada pago debe tener un monto mayor que cero.");
            }
            boolean esTarjeta = Set.of("Cuscatlan", "Nico", "Credomatic").contains(pago.metodoPago());
            if (esTarjeta && (pago.ultimos4Digitos() == null || pago.codigoAutorizacion() == null
                    || pago.codigoAutorizacion().isBlank())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Los pagos con tarjeta requieren los últimos 4 dígitos y el código de autorización.");
            }
            boolean esDevolucion = "Devolucion".equals(pago.metodoPago()) || "Devolución".equals(pago.metodoPago());
            if (esDevolucion) {
                String folio = pago.folioDevolucion() == null ? null : pago.folioDevolucion().trim();
                if (folio == null || folio.isBlank() || !foliosUsados.add(folio)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Cada pago por devolución requiere un folio válido y único.");
                }
                DevolucionVenta devolucion = devolucionRepository.findByFolioDevolucionForUpdate(folio)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "No existe la devolución " + folio + "."));
                validarCreditoDevolucion(devolucion.getVenta(), folio);
                if (pago.monto().compareTo(devolucion.getImporte()) != 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "El monto del pago no coincide con el crédito de devolución.");
                }
                creditosAplicados.add(devolucion.getVenta());
            }
            totalPagado = totalPagado.add(pago.monto()).setScale(2, REDONDEO);
        }
        if (totalPagado.compareTo(total) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Los pagos no cubren el total de la venta.");
        }
    }

    private void validarCreditoDevolucion(Venta ventaOrigen, String folioOrigen) {
        if ("ANULADA".equals(ventaOrigen.getEstado())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Esta venta fue anulada por completo no tiene validez.");
        }
        boolean tieneDevolucion = !devolucionRepository
                .findAllByVentaIdOrderByFechaDesc(ventaOrigen.getId()).isEmpty();
        if (!"DEVUELTA".equals(ventaOrigen.getEstado()) || ventaOrigen.isCreditoDevolucionConsumido()
                || !tieneDevolucion) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El folio " + folioOrigen + " no tiene un crédito de devolución activo o ya fue consumido.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<VentaResponseDTO> obtenerHistorial() {
        return ventaRepository.findAllByOrderByFechaDesc().stream()
                .map(this::convertir)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<VentaResponseDTO> buscarHistorial(
            String folioInterno, LocalDateTime fechaInicio, LocalDateTime fechaFin, Long usuarioId, Pageable pageable) {
        if (fechaInicio != null && fechaFin != null && fechaInicio.isAfter(fechaFin)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha inicial debe ser anterior a la fecha final.");
        }
        String folio = folioInterno == null || folioInterno.isBlank() ? null : folioInterno.trim();
        return ventaRepository.buscarHistorial(folio, fechaInicio, fechaFin, usuarioId, pageable)
                .map(this::convertir);
    }

    @Override
    @Transactional(readOnly = true)
    public VentaResponseDTO obtenerVenta(Long id) {
        return convertir(buscarVenta(id));
    }

    @Override
    @Transactional
    public DevolucionResponseDTO procesarDevolucion(Long ventaId, DevolucionRequestDTO request, String username) {
        Venta venta = ventaRepository.findByIdForUpdate(ventaId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No existe la venta " + ventaId + "."));
        if (!"COMPLETADA".equals(venta.getEstado()) && !"DEVOLUCION_PARCIAL".equals(venta.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La venta ya fue devuelta o no admite devoluciones.");
        }

        if (request == null || request.motivo() == null || request.motivo().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El motivo de devolución es obligatorio.");
        }

        Usuario usuario = usuarioRepository.findByUsername(username)
                .filter(found -> Boolean.TRUE.equals(found.getActivo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "El usuario no está activo."));
        if (usuario.getRol() != Rol.ROLE_ADMIN && usuario.getRol() != Rol.ROLE_USER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El usuario no tiene permiso para procesar devoluciones.");
        }

        List<DetalleVenta> detallesSolicitados = new ArrayList<>();
        Map<Long, Integer> cantidadesSolicitadas = new HashMap<>();
        if (request.items() != null && request.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La devolución debe incluir al menos un producto o utilizar una solicitud completa.");
        }
        if (request.items() == null) {
            venta.getDetalles().forEach(detalle ->
                    cantidadesSolicitadas.put(detalle.getProducto().getId(),
                            detalle.getCantidad() - detalle.getCantidadDevuelta()));
        } else {
            request.items().forEach(item ->
                    cantidadesSolicitadas.merge(item.productoId(), item.cantidad(), Math::addExact));
        }
        BigDecimal subtotalDevuelto = CERO;
        int cantidadPendiente = 0;
        for (DetalleVenta detalle : venta.getDetalles()) {
            int pendiente = detalle.getCantidad() - detalle.getCantidadDevuelta();
            cantidadPendiente += pendiente;
            int cantidad = cantidadesSolicitadas.getOrDefault(detalle.getProducto().getId(), 0);
            if (cantidad < 0 || cantidad > pendiente) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "La cantidad solicitada supera la cantidad pendiente de devolución.");
            }
            if (cantidad > 0) {
                detallesSolicitados.add(detalle);
                subtotalDevuelto = subtotalDevuelto.add(detalle.getSubtotal()
                        .multiply(BigDecimal.valueOf(cantidad))
                        .divide(BigDecimal.valueOf(detalle.getCantidad()), 2, REDONDEO));
            }
        }
        if (detallesSolicitados.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El ticket ya fue devuelto.");
        }
        boolean devolucionCompleta = venta.getDetalles().stream().allMatch(detalle ->
                detalle.getCantidadDevuelta() + cantidadesSolicitadas.getOrDefault(detalle.getProducto().getId(), 0)
                        == detalle.getCantidad());
        BigDecimal importeDevolucion = calcularImporteDevolucion(venta, subtotalDevuelto, devolucionCompleta);
        String folioDevolucion = folioInternoService.generar(TipoOperacion.DE);
        DevolucionVenta devolucion = devolucionRepository.saveAndFlush(
                new DevolucionVenta(folioDevolucion, venta, usuario, importeDevolucion, request.motivo().trim()));

        for (DetalleVenta detalle : detallesSolicitados) {
            int cantidad = cantidadesSolicitadas.get(detalle.getProducto().getId());
            inventarioService.registrarDevolucion(
                    detalle.getProducto().getId(),
                    cantidad,
                    venta.getFolioInterno(),
                    folioDevolucion,
                    request.motivo(),
                    usuario);
            detalle.setCantidadDevuelta(detalle.getCantidadDevuelta() + cantidad);
        }

        venta.setEstado(devolucionCompleta && cantidadPendiente > 0 ? "DEVUELTA" : "DEVOLUCION_PARCIAL");
        return new DevolucionResponseDTO(
                devolucion.getId(),
                devolucion.getFolioDevolucion(),
                venta.getId(),
                devolucion.getImporte(),
                devolucion.getFecha(),
                usuario.getNombreCompleto() == null ? usuario.getUsername() : usuario.getNombreCompleto(),
                devolucion.getMotivo());
    }

    @Override
    @Transactional
    public DevolucionResponseDTO anularVenta(Long ventaId, String motivo, String username) {
        Venta venta = ventaRepository.findByIdForUpdate(ventaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la venta " + ventaId + "."));
        if (!"COMPLETADA".equals(venta.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La venta ya fue devuelta o anulada.");
        }
        Usuario usuario = usuarioRepository.findByUsername(username)
                .filter(found -> Boolean.TRUE.equals(found.getActivo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "El usuario no está activo."));
        if (usuario.getRol() != Rol.ROLE_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un administrador puede anular una venta.");
        }
        String folio = folioInternoService.generar(TipoOperacion.DE);
        DevolucionVenta anulacion = devolucionRepository.saveAndFlush(
                new DevolucionVenta(folio, venta, usuario, venta.getTotal(), motivo.trim()));
        for (DetalleVenta detalle : venta.getDetalles()) {
            if (detalle.getCantidadDevuelta() != 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "La venta ya tiene una devolución.");
            }
            inventarioService.registrarDevolucion(detalle.getProducto().getId(), detalle.getCantidad(),
                    venta.getFolioInterno(), folio, "Anulación: " + motivo, usuario);
            detalle.setCantidadDevuelta(detalle.getCantidad());
        }
        venta.setEstado("ANULADA");
        return new DevolucionResponseDTO(anulacion.getId(), anulacion.getFolioDevolucion(), venta.getId(),
                anulacion.getImporte(), anulacion.getFecha(),
                usuario.getNombreCompleto() == null ? usuario.getUsername() : usuario.getNombreCompleto(),
                anulacion.getMotivo());
    }

    @Override
    public BigDecimal obtenerTasaIva() {
        return configuracionService.obtenerEntidad().getPorcentajeIva()
                .divide(BigDecimal.valueOf(100), 4, REDONDEO);
    }

    private VentaResponseDTO convertir(Venta venta) {
        List<DetalleVentaResponseDTO> detalles = venta.getDetalles().stream()
                .map(detalle -> new DetalleVentaResponseDTO(
                        detalle.getProducto().getId(),
                        detalle.getProducto().getNombre(),
                        detalle.getCantidad(),
                        detalle.getCantidadDevuelta(),
                        detalle.getPrecioUnitario(),
                        detalle.getSubtotal(),
                        detalle.getDescuentoAutomatico(),
                        detalle.getDescuentoManual(),
                        detalle.getPromocionAplicada()))
                .toList();

        return new VentaResponseDTO(
                venta.getId(),
                venta.getNumeroOrden(),
                venta.getFolioInterno(),
                venta.getFecha(),
                venta.getSubtotal(),
                venta.getIva(),
                venta.getDescuento(),
                venta.getDescuentoAutomatico(),
                venta.getDescuentoManual(),
                venta.getPromocionAplicada(),
                venta.getDescuentoAprobadoPor() == null ? null : venta.getDescuentoAprobadoPor().getId(),
                venta.getDescuentoAprobadoPor() == null ? null
                        : (venta.getDescuentoAprobadoPor().getNombreCompleto() == null
                                ? venta.getDescuentoAprobadoPor().getUsername()
                                : venta.getDescuentoAprobadoPor().getNombreCompleto()),
                venta.getTotal(),
                venta.getEstado(),
                venta.getMetodoPago() == null ? "No especificado" : venta.getMetodoPago(),
                venta.getCompradorNombre(),
                venta.isEsExenta(),
                venta.getTipoComprobante(),
                venta.getCliente() == null ? null : venta.getCliente().getId(),
                venta.getCliente() != null
                        ? venta.getCliente().getNombre()
                        : (venta.getCompradorNombre() == null ? "Cliente General / Consumidor Final" : venta.getCompradorNombre()),
                venta.getCliente() == null ? null : venta.getCliente().getDocumento(),
                venta.getCliente() == null ? null : venta.getCliente().getNrc(),
                venta.getCliente() == null ? null : venta.getCliente().getGiro(),
                venta.getCliente() == null ? null : venta.getCliente().getCodigoActividad(),
                venta.getUsuario() == null ? null : venta.getUsuario().getId(),
                venta.getUsuario() == null
                        ? null
                        : (venta.getUsuario().getNombreCompleto() == null
                                ? venta.getUsuario().getUsername()
                                : venta.getUsuario().getNombreCompleto()),
                detalles,
                devolucionRepository.findAllByVentaIdOrderByFechaDesc(venta.getId()).stream()
                        .map(devolucion -> new DevolucionResponseDTO(
                                devolucion.getId(),
                                devolucion.getFolioDevolucion(),
                                venta.getId(),
                                devolucion.getImporte(),
                                devolucion.getFecha(),
                                devolucion.getUsuario() == null
                                        ? null
                                        : (devolucion.getUsuario().getNombreCompleto() == null
                                                ? devolucion.getUsuario().getUsername()
                                                : devolucion.getUsuario().getNombreCompleto()),
                                devolucion.getMotivo()))
                        .toList());
    }

    private Venta buscarVenta(Long id) {
        return ventaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la venta " + id + "."));
    }

    private BigDecimal calcularImporteDevolucion(Venta venta, BigDecimal subtotalDevuelto, boolean devolucionCompleta) {
        BigDecimal yaDevuelto = devolucionRepository.findAllByVentaIdOrderByFechaDesc(venta.getId()).stream()
                .map(DevolucionVenta::getImporte)
                .reduce(CERO, BigDecimal::add);
        if (devolucionCompleta) {
            return venta.getTotal().subtract(yaDevuelto).setScale(2, REDONDEO);
        }
        BigDecimal importeBruto = venta.getDetalles().stream()
                .map(DetalleVenta::getSubtotal)
                .reduce(CERO, BigDecimal::add);
        if (importeBruto.signum() <= 0) return CERO;
        return venta.getTotal().multiply(subtotalDevuelto)
                .divide(importeBruto, 2, REDONDEO)
                .min(venta.getTotal().subtract(yaDevuelto))
                .setScale(2, REDONDEO);
    }

    private Map<Long, BigDecimal> asignarDescuentosManuales(
            List<DetalleVenta> detalles, Map<Long, BigDecimal> bases,
            Map<Long, BigDecimal> automaticos, Map<Long, BigDecimal> descuentosPorLinea,
            BigDecimal descuentoTicket) {
        Map<Long, BigDecimal> resultado = new TreeMap<>();
        if (descuentoTicket.signum() == 0 && descuentosPorLinea.isEmpty()) return resultado;
        for (Map.Entry<Long, BigDecimal> entry : descuentosPorLinea.entrySet()) {
            BigDecimal disponible = bases.getOrDefault(entry.getKey(), CERO)
                    .subtract(automaticos.getOrDefault(entry.getKey(), CERO));
            if (disponible.signum() == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El descuento de línea supera el saldo del producto.");
            }
            if (entry.getValue().compareTo(disponible) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El descuento de línea supera el saldo del producto.");
            }
            resultado.put(entry.getKey(), entry.getValue());
        }
        if (descuentoTicket.signum() == 0) return resultado;
        List<DetalleVenta> disponibles = detalles.stream().filter(detalle -> {
            Long id = detalle.getProducto().getId();
            return bases.get(id).subtract(automaticos.getOrDefault(id, CERO))
                    .subtract(descuentosPorLinea.getOrDefault(id, CERO)).signum() > 0;
        }).toList();
        BigDecimal baseDisponibleTotal = disponibles.stream().map(detalle -> {
            Long id = detalle.getProducto().getId();
            return bases.get(id).subtract(automaticos.getOrDefault(id, CERO))
                    .subtract(descuentosPorLinea.getOrDefault(id, CERO));
        }).reduce(CERO, BigDecimal::add);
        if (baseDisponibleTotal.compareTo(descuentoTicket) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El descuento manual supera el saldo de la venta.");
        }
        BigDecimal asignado = CERO;
        for (int i = 0; i < disponibles.size(); i++) {
            DetalleVenta detalle = disponibles.get(i);
            Long id = detalle.getProducto().getId();
            BigDecimal restante = bases.get(id).subtract(automaticos.getOrDefault(id, CERO))
                    .subtract(descuentosPorLinea.getOrDefault(id, CERO)).max(CERO);
            BigDecimal parte = i == disponibles.size() - 1
                    ? descuentoTicket.subtract(asignado)
                    : descuentoTicket.multiply(restante).divide(baseDisponibleTotal, 2, RoundingMode.DOWN);
            parte = parte.min(restante);
            resultado.merge(id, parte, BigDecimal::add);
            asignado = asignado.add(parte);
        }
        if (asignado.compareTo(descuentoTicket) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El descuento manual supera el saldo de la venta.");
        }
        return resultado;
    }
}
