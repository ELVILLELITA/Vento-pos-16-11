package com.vento.pos.dto;

import java.math.BigDecimal;

public record ProductoVentaAggregateDTO(
        Long productId,
        Long unitsSold,
        BigDecimal totalRevenue
) {
}
