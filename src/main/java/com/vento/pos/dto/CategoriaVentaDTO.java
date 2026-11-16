package com.vento.pos.dto;

import java.math.BigDecimal;

public record CategoriaVentaDTO(
        String categoryName,
        BigDecimal totalAmount,
        BigDecimal percentage
) {
}
