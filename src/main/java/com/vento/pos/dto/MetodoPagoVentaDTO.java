package com.vento.pos.dto;

import java.math.BigDecimal;

public record MetodoPagoVentaDTO(
        String paymentMethod,
        BigDecimal totalAmount
) {
}
