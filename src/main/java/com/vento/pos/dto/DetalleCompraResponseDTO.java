package com.vento.pos.dto;

import java.math.BigDecimal;

public record DetalleCompraResponseDTO(
        Long id,
        Long productoId,
        String producto,
        Integer cantidad,
        BigDecimal costoUnitario,
        BigDecimal subtotal
) {
}
