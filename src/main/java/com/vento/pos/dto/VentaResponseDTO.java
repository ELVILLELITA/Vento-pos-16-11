package com.vento.pos.dto;

import com.vento.pos.entity.TipoComprobante;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record VentaResponseDTO(
        Long id,
        String numeroOrden,
        String folioInterno,
        LocalDateTime fecha,
        BigDecimal subtotal,
        BigDecimal iva,
        BigDecimal descuento,
        BigDecimal descuentoAutomatico,
        BigDecimal descuentoManual,
        String promocionAplicada,
        Long descuentoAprobadoPorId,
        String descuentoAprobadoPorNombre,
        BigDecimal total,
        String estado,
        String metodoPago,
        String compradorNombre,
        boolean esExenta,
        TipoComprobante tipoComprobante,
        Long clienteId,
        String clienteNombre,
        String clienteDocumento,
        String clienteNrc,
        String clienteGiro,
        String clienteCodigoActividad,
        Long cajeroId,
        String cajeroNombre,
        List<DetalleVentaResponseDTO> detalles,
        List<DevolucionResponseDTO> devoluciones
) {
}
