package com.vento.pos.dto;

import java.math.BigDecimal;

public record ProductoVentaDTO(
        String productName,
        String category,
        Long unitsSold,
        BigDecimal totalRevenue
) {
}
