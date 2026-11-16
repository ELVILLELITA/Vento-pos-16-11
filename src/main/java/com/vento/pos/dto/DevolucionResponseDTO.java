package com.vento.pos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DevolucionResponseDTO(
        Long id,
        String folioDevolucion,
        Long ventaId,
        BigDecimal importe,
        LocalDateTime fecha,
        String usuario,
        String motivo
) {
}
