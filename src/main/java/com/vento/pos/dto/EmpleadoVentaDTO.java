package com.vento.pos.dto;

import java.math.BigDecimal;

public record EmpleadoVentaDTO(
        String cashierName,
        BigDecimal totalAmount,
        Long salesCount
) {
}
