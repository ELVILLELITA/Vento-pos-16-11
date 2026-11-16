package com.vento.pos.dto;

import java.math.BigDecimal;

public record DetalleVentaResponseDTO(
        Long productoId,
        String nombreProducto,
        Integer cantidad,
        Integer cantidadDevuelta,
        BigDecimal precioUnitario,
        BigDecimal subtotal,
        BigDecimal descuentoAutomatico,
        BigDecimal descuentoManual,
        String promocionAplicada
) {
}
