package com.vento.pos.dto;

import java.math.BigDecimal;

public record ConfiguracionSistemaDTO(
        Long id,
        String nombreEstablecimiento,
        String direccion,
        String telefono,
        String codigoSucursal,
        String codigoEstacion,
        BigDecimal porcentajeIva,
        String monedaSimbolo,
        String piePaginaTicket,
        String ambienteDte,
        String nitEmisor,
        String nrcEmisor,
        String codigoActividadEmisor,
        String clavePrivadaCertificado,
        boolean clavePrivadaCertificadoConfigurada,
        String passwordCertificado,
        boolean passwordCertificadoConfigurado,
        String urlFirmador,
        String codigoPuntoVentaMh
) {
}
