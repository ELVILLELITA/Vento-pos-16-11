package com.vento.pos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vento.pos.dto.DteDocumentoResponseDTO;
import com.vento.pos.entity.Cliente;
import com.vento.pos.entity.ConfiguracionSistema;
import com.vento.pos.entity.DteDocumento;
import com.vento.pos.entity.DetalleVenta;
import com.vento.pos.entity.TipoComprobante;
import com.vento.pos.entity.Venta;
import com.vento.pos.repository.DteDocumentoRepository;
import com.vento.pos.repository.VentaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class DteService {
    private final VentaRepository ventaRepository;
    private final DteDocumentoRepository documentoRepository;
    private final ConfiguracionSistemaService configuracionService;
    private final DteSignerClient signerClient;
    private final ObjectMapper objectMapper;

    public DteService(VentaRepository ventaRepository, DteDocumentoRepository documentoRepository,
                      ConfiguracionSistemaService configuracionService, DteSignerClient signerClient,
                      ObjectMapper objectMapper) {
        this.ventaRepository = ventaRepository;
        this.documentoRepository = documentoRepository;
        this.configuracionService = configuracionService;
        this.signerClient = signerClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DteDocumentoResponseDTO firmarVenta(Long ventaId) {
        Venta venta = ventaRepository.findByIdForUpdate(ventaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la venta."));
        DteDocumento existente = documentoRepository.findByVentaId(ventaId).orElse(null);
        if (existente != null) return DteDocumentoResponseDTO.from(existente);

        ConfiguracionSistema configuracion = configuracionService.obtenerEntidad();
        validarConfiguracion(configuracion);
        if (venta.getTipoComprobante() == TipoComprobante.CREDITO_FISCAL
                && (venta.getCliente() == null || venta.getCliente().getNrc() == null
                || venta.getCliente().getGiro() == null || venta.getCliente().getCodigoActividad() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El cliente no tiene los datos fiscales requeridos para CCF.");
        }

        String tipoDte = venta.getTipoComprobante() == TipoComprobante.CREDITO_FISCAL ? "03" : "01";
        String codigoGeneracion = UUID.randomUUID().toString().toUpperCase();
        ObjectNode documento = construirDocumentoJson(venta, configuracion, tipoDte, codigoGeneracion);
        String firmado = signerClient.sign(
                configuracion.getUrlFirmador(),
                configuracion.getNitEmisor(),
                configuracion.getPasswordCertificado(),
                documento);
        DteDocumento entity = documentoRepository.saveAndFlush(new DteDocumento(
                venta.getId(), tipoDte, codigoGeneracion, LocalDateTime.now(), firmado));
        return DteDocumentoResponseDTO.from(entity);
    }

    public ObjectNode construirDocumentoJson(Venta venta, ConfiguracionSistema configuracion,
                                              String tipoDte, String codigoGeneracion) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode identificacion = root.putObject("identificacion");
        identificacion.put("version", "03".equals(tipoDte) ? 3 : 1);
        identificacion.put("ambiente", configuracion.getAmbienteDte());
        identificacion.put("tipoDte", tipoDte);
        identificacion.put("numeroControl", numeroControl(venta, tipoDte, configuracion));
        identificacion.put("codigoGeneracion", codigoGeneracion);
        identificacion.put("tipoModelo", 1);
        identificacion.put("tipoOperacion", 1);
        identificacion.put("fecEmi", venta.getFecha().toLocalDate().toString());
        identificacion.put("horEmi", venta.getFecha().toLocalTime().withNano(0).toString());
        identificacion.put("tipoMoneda", "USD");

        root.putNull("documentoRelacionado");
        root.putNull("otrosDocumentos");
        root.putNull("ventaTercero");
        root.set("emisor", crearEmisor(configuracion));
        root.set("receptor", crearReceptor(venta.getCliente(), venta));
        BigDecimal tasaIva = configuracion.getPorcentajeIva()
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        ArrayNode cuerpo = root.putArray("cuerpoDocumento");
        int numeroItem = 1;
        for (DetalleVenta detalle : venta.getDetalles()) {
            BigDecimal baseUnitario = detalle.getPrecioUnitario()
                    .divide(BigDecimal.ONE.add(tasaIva), 2, RoundingMode.HALF_UP);
            BigDecimal base = baseUnitario.multiply(BigDecimal.valueOf(detalle.getCantidad()))
                    .subtract(detalle.getDescuentoAutomatico()).subtract(detalle.getDescuentoManual())
                    .setScale(2, RoundingMode.HALF_UP);
            ObjectNode item = cuerpo.addObject();
            item.put("numItem", numeroItem++);
            item.put("tipoItem", 1);
            item.put("numeroDocumento", (String) null);
            item.put("cantidad", detalle.getCantidad());
            item.put("codigo", detalle.getProducto().getCodigoBarras() == null
                    ? detalle.getProducto().getId().toString() : detalle.getProducto().getCodigoBarras());
            item.put("uniMedida", 59);
            item.put("descripcion", detalle.getProducto().getNombre());
            item.put("precioUni", baseUnitario);
            item.put("montoDescu", detalle.getDescuentoAutomatico().add(detalle.getDescuentoManual()));
            item.put("ventaNoSuj", 0);
            item.put("ventaExenta", venta.isEsExenta() ? base : BigDecimal.ZERO);
            item.put("ventaGravada", venta.isEsExenta() ? BigDecimal.ZERO : base);
            item.putNull("tributos");
            item.put("psv", 0);
            item.put("noGravado", 0);
        }

        ObjectNode resumen = root.putObject("resumen");
        resumen.put("totalNoSuj", 0);
        resumen.put("totalExenta", 0);
        BigDecimal baseVenta = venta.getSubtotal().subtract(venta.getDescuento()).setScale(2, RoundingMode.HALF_UP);
        resumen.put("totalGravada", venta.isEsExenta() ? BigDecimal.ZERO : baseVenta);
        resumen.put("totalExenta", venta.isEsExenta() ? baseVenta : BigDecimal.ZERO);
        resumen.put("subTotalVentas", venta.getSubtotal());
        resumen.put("descuNoSuj", 0);
        resumen.put("descuExenta", 0);
        resumen.put("descuGravada", venta.getDescuento());
        resumen.put("porcentajeDescuento", venta.getSubtotal().signum() == 0 ? BigDecimal.ZERO
                : venta.getDescuento().multiply(BigDecimal.valueOf(100))
                        .divide(venta.getSubtotal(), 2, RoundingMode.HALF_UP));
        resumen.put("totalDescu", venta.getDescuento());
        resumen.putNull("tributos");
        resumen.put("subTotal", venta.getSubtotal().subtract(venta.getDescuento()).setScale(2, RoundingMode.HALF_UP));
        resumen.put("ivaRete1", 0);
        resumen.put("reteRenta", 0);
        resumen.put("montoTotalOperacion", venta.getTotal());
        resumen.put("totalNoGravado", 0);
        resumen.put("totalPagar", venta.getTotal());
        resumen.put("totalLetras", totalEnLetras(venta.getTotal()));
        resumen.put("totalIva", venta.isEsExenta() ? BigDecimal.ZERO : venta.getIva());
        resumen.put("saldoFavor", 0);
        resumen.put("condicionOperacion", 1);
        ArrayNode pagos = resumen.putArray("pagos");
        ObjectNode pago = pagos.addObject();
        pago.put("codigo", "Efectivo".equalsIgnoreCase(venta.getMetodoPago()) ? "01" : "03");
        pago.put("montoPago", venta.getTotal());
        pago.putNull("referencia");
        pago.putNull("plazo");
        pago.putNull("periodo");
        resumen.putNull("numPagoElectronico");
        ObjectNode extension = root.putObject("extension");
        extension.putNull("nombEntrega");
        extension.putNull("docuEntrega");
        extension.putNull("nombRecibe");
        extension.putNull("docuRecibe");
        extension.putNull("observaciones");
        root.putNull("apendice");
        return root;
    }

    private ObjectNode crearEmisor(ConfiguracionSistema config) {
        ObjectNode emisor = objectMapper.createObjectNode();
        emisor.put("nit", config.getNitEmisor());
        emisor.put("nrc", config.getNrcEmisor());
        emisor.put("nombre", config.getNombreEstablecimiento());
        emisor.put("codActividad", config.getCodigoActividadEmisor());
        emisor.put("descActividad", config.getCodigoActividadEmisor());
        emisor.put("nombreComercial", config.getNombreEstablecimiento());
        emisor.put("tipoEstablecimiento", "01");
        ObjectNode direccion = emisor.putObject("direccion");
        direccion.put("departamento", "");
        direccion.put("municipio", "");
        direccion.put("complemento", config.getDireccion() == null ? "" : config.getDireccion());
        emisor.put("telefono", config.getTelefono());
        emisor.putNull("correo");
        emisor.put("codEstableMH", config.getCodigoPuntoVentaMh());
        emisor.put("codEstable", config.getCodigoSucursal());
        emisor.put("codPuntoVentaMH", config.getCodigoPuntoVentaMh());
        emisor.put("codPuntoVenta", config.getCodigoEstacion());
        return emisor;
    }

    private ObjectNode crearReceptor(Cliente cliente, Venta venta) {
        ObjectNode receptor = objectMapper.createObjectNode();
        if (cliente == null) {
            receptor.putNull("tipoDocumento");
            receptor.putNull("numDocumento");
            receptor.putNull("nrc");
            receptor.put("nombre", venta.getCompradorNombre() == null ? "Consumidor Final" : venta.getCompradorNombre());
            receptor.putNull("codActividad");
            receptor.putNull("descActividad");
            receptor.putNull("nombreComercial");
            receptor.putNull("direccion");
            receptor.putNull("telefono");
            receptor.putNull("correo");
            return receptor;
        }
        receptor.put("tipoDocumento", switch (cliente.getTipoDocumento()) {
            case DUI -> "13";
            case NIT -> "36";
            case PASAPORTE -> "03";
            case NRC -> "37";
        });
        receptor.put("numDocumento", cliente.getDocumento());
        receptor.put("nrc", cliente.getNrc());
        receptor.put("nombre", cliente.getNombre());
        receptor.put("codActividad", cliente.getCodigoActividad());
        receptor.put("descActividad", cliente.getGiro());
        receptor.put("nombreComercial", cliente.getNombre());
        ObjectNode direccion = receptor.putObject("direccion");
        direccion.put("departamento", "");
        direccion.put("municipio", "");
        direccion.put("complemento", cliente.getDireccion() == null ? "" : cliente.getDireccion());
        receptor.put("telefono", cliente.getTelefono());
        receptor.put("correo", cliente.getEmail());
        return receptor;
    }

    private String numeroControl(Venta venta, String tipoDte, ConfiguracionSistema config) {
        return "DTE-" + tipoDte + "-" + config.getCodigoSucursal() + config.getCodigoEstacion()
                + "-" + String.format("%015d", venta.getId());
    }

    static String totalEnLetras(BigDecimal value) {
        long entero = value.setScale(0, RoundingMode.DOWN).longValue();
        int centavos = value.remainder(BigDecimal.ONE).movePointRight(2)
                .abs().setScale(0, RoundingMode.HALF_UP).intValue();
        return numero(entero).toUpperCase() + " DÓLARES CON "
                + String.format("%02d", centavos) + "/100";
    }

    private static String numero(long value) {
        if (value == 0) return "cero";
        if (value >= 1_000_000) {
            long millones = value / 1_000_000;
            return (millones == 1 ? "un millón" : numero(millones) + " millones")
                    + restoNumero(value % 1_000_000);
        }
        if (value >= 1_000) {
            long miles = value / 1_000;
            return (miles == 1 ? "mil" : numero(miles) + " mil") + restoNumero(value % 1_000);
        }
        if (value >= 100) {
            String[] centenas = {"", "ciento", "doscientos", "trescientos", "cuatrocientos",
                    "quinientos", "seiscientos", "setecientos", "ochocientos", "novecientos"};
            return value == 100 ? "cien" : centenas[(int) value / 100] + restoNumero(value % 100);
        }
        if (value >= 20) {
            String[] decenas = {"", "", "veinte", "treinta", "cuarenta", "cincuenta",
                    "sesenta", "setenta", "ochenta", "noventa"};
            return decenas[(int) value / 10] + (value % 10 == 0 ? "" : " y " + numero(value % 10));
        }
        String[] unidades = {"cero", "uno", "dos", "tres", "cuatro", "cinco", "seis",
                "siete", "ocho", "nueve", "diez", "once", "doce", "trece", "catorce",
                "quince", "dieciséis", "diecisiete", "dieciocho", "diecinueve"};
        return unidades[(int) value];
    }

    private static String restoNumero(long value) {
        return value == 0 ? "" : " " + numero(value);
    }

    private void validarConfiguracion(ConfiguracionSistema config) {
        if (config.getAmbienteDte() == null || config.getNitEmisor() == null || config.getNitEmisor().isBlank()
                || config.getNrcEmisor() == null || config.getNrcEmisor().isBlank()
                || config.getCodigoActividadEmisor() == null || config.getCodigoActividadEmisor().isBlank()
                || config.getClavePrivadaCertificado() == null || config.getClavePrivadaCertificado().isBlank()
                || config.getPasswordCertificado() == null || config.getPasswordCertificado().isBlank()
                || config.getUrlFirmador() == null || config.getCodigoPuntoVentaMh() == null
                || config.getCodigoPuntoVentaMh().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Completa la configuración del emisor y del firmador DTE.");
        }
    }
}
