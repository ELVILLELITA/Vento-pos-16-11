package com.vento.pos.dto;

import java.math.BigDecimal;

public record ProductoResponseDTO(
        Long id,
        String nombre,
        BigDecimal precio,
        String categoria,
        Boolean favorito,
        Integer stock,
        Boolean activo,
        boolean precioIncluyeIva,
        String codigoBarras
) {
}
