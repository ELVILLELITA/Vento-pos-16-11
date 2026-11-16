package com.vento.pos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vento.pos.entity.ConfiguracionSistema;
import com.vento.pos.entity.DetalleVenta;
import com.vento.pos.entity.Venta;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final String EMAIL_PATTERN = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final ConfiguracionSistemaService configuracionService;
    private final DteService dteService;
    private final ObjectMapper objectMapper;
    private final String defaultConsumerEmail;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                        ConfiguracionSistemaService configuracionService,
                        DteService dteService,
                        ObjectMapper objectMapper,
                        @Value("${app.mail.default-consumer:}") String defaultConsumerEmail) {
        this.mailSenderProvider = mailSenderProvider;
        this.configuracionService = configuracionService;
        this.dteService = dteService;
        this.objectMapper = objectMapper;
        this.defaultConsumerEmail = defaultConsumerEmail;
    }

    public void enviarDte(Venta venta) {
        String recipient = venta.getCliente() != null && isEmail(venta.getCliente().getEmail())
                ? venta.getCliente().getEmail().trim()
                : defaultConsumerEmail.trim();
        if (!isEmail(recipient)) {
            log.warn("No se envió el DTE de venta {}: no existe un correo válido.", venta.getId());
            return;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("No se envió el DTE de venta {}: el correo SMTP no está configurado.", venta.getId());
            return;
        }
        try {
            ConfiguracionSistema config = configuracionService.obtenerEntidad();
            String tipoDte = venta.getTipoComprobante().name().equals("CREDITO_FISCAL") ? "03" : "01";
            ObjectNode dte = dteService.construirDocumentoJson(
                    venta, config, tipoDte, java.util.UUID.randomUUID().toString().toUpperCase(Locale.ROOT));
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setTo(recipient);
            helper.setSubject("DTE " + venta.getFolioInterno() + " - " + config.getNombreEstablecimiento());
            helper.setText(renderLetterRepresentation(venta, config, dte), true);
            helper.addAttachment("dte-" + venta.getFolioInterno() + ".json",
                    new org.springframework.core.io.ByteArrayResource(
                            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(dte)));
            sender.send(message);
        } catch (Exception exception) {
            log.error("No se pudo enviar el DTE de venta {} a {}.", venta.getId(), recipient, exception);
        }
    }

    private String renderLetterRepresentation(Venta venta, ConfiguracionSistema config, ObjectNode dte) {
        ObjectNode emisor = dte.path("emisor").isObject() ? (ObjectNode) dte.get("emisor") : null;
        ObjectNode receptor = dte.path("receptor").isObject() ? (ObjectNode) dte.get("receptor") : null;
        StringBuilder rows = new StringBuilder();
        for (DetalleVenta item : venta.getDetalles()) {
            rows.append("<tr><td>").append(item.getCantidad()).append("</td><td>")
                    .append(escape(item.getProducto().getNombre())).append("</td><td class=\"right\">")
                    .append(money(item.getPrecioUnitario())).append("</td><td class=\"right\">")
                    .append(money(item.getSubtotal())).append("</td></tr>");
        }
        return """
                <!doctype html><html lang="es"><head><meta charset="UTF-8"><style>
                @page{size:Letter;margin:14mm}body{font-family:Arial,sans-serif;color:#20242a;font-size:11px}
                h1{text-align:center;font-size:18px;margin:0 0 4px}h2{font-size:12px;margin:0 0 6px}
                .muted{color:#606873}.box{border:1px solid #9aa2ad;padding:9px;margin:8px 0}
                .grid{display:grid;grid-template-columns:1fr 1fr;gap:8px}.row{display:flex;justify-content:space-between}
                table{width:100%%;border-collapse:collapse;margin-top:12px}th,td{border:1px solid #9aa2ad;padding:5px}
                th{background:#e9edf1}.right{text-align:right}.summary{width:42%%;margin-left:auto}
                .stamp{font-size:9px;overflow-wrap:anywhere}
                </style></head><body>
                <h1>%s</h1><div style="text-align:center" class="muted">DOCUMENTO TRIBUTARIO ELECTRÓNICO</div>
                <div class="box grid"><div><h2>EMISOR</h2><div>NIT: %s</div><div>NRC: %s</div><div>Dirección: %s</div></div>
                <div><h2>RECEPTOR</h2><div>NIT/DUI: %s</div><div>NRC: %s</div><div>Dirección: %s</div></div></div>
                <div class="box"><div class="row"><span>Folio: %s</span><span>Fecha: %s</span></div>
                <div class="stamp">Código de generación: %s</div><div class="stamp">Número de control: %s</div></div>
                <table><thead><tr><th>Cant.</th><th>Descripción</th><th>Precio unitario</th><th>Subtotal</th></tr></thead>
                <tbody>%s</tbody></table>
                <div class="summary box"><div class="row"><span>No sujetas</span><b>$0.00</b></div>
                <div class="row"><span>Exentas</span><b>$0.00</b></div><div class="row"><span>Gravadas</span><b>%s</b></div>
                <div class="row"><span>Descuentos</span><b>-%s</b></div><div class="row"><span>IVA 13%%</span><b>%s</b></div>
                <div class="row"><strong>Total a pagar</strong><strong>%s</strong></div></div>
                <div class="box"><b>Total en letras:</b> %s</div>
                </body></html>
                """.formatted(escape(config.getNombreEstablecimiento()), value(emisor, "nit"), value(emisor, "nrc"),
                value(emisor, "direccion", "complemento"), value(receptor, "numDocumento"),
                value(receptor, "nrc"), value(receptor, "direccion", "complemento"),
                escape(venta.getFolioInterno()), venta.getFecha(), value(dte, "identificacion", "codigoGeneracion"),
                value(dte, "identificacion", "numeroControl"), rows, money(venta.getSubtotal().subtract(venta.getDescuento())),
                money(venta.getDescuento()), money(venta.getIva()), money(venta.getTotal()), totalEnLetras(venta.getTotal()));
    }

    private static String value(ObjectNode node, String... path) {
        if (node == null) return "";
        var current = node;
        for (String part : path) {
            if (!current.path(part).isObject() && path[path.length - 1].equals(part)) return escape(current.path(part).asText(""));
            if (!current.path(part).isObject()) return "";
            current = (ObjectNode) current.get(part);
        }
        return "";
    }

    private static String money(BigDecimal value) {
        return "$" + value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String totalEnLetras(BigDecimal value) {
        return DteService.totalEnLetras(value);
    }

    private static boolean isEmail(String value) {
        return value != null && value.matches(EMAIL_PATTERN);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
