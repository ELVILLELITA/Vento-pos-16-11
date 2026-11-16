package com.vento.pos.dto;

import com.vento.pos.entity.TipoMovimiento;
import com.vento.pos.entity.TipoOperacion;

import java.time.LocalDateTime;

public record MovimientoInventarioResponseDTO(
        Long id,
        String folioMovimiento,
        String folioReferencia,
        Long productoId,
        String producto,
        TipoMovimiento tipo,
        TipoOperacion tipoOperacion,
        Integer cantidad,
        Integer stockAnterior,
        Integer stockNuevo,
        String motivo,
        LocalDateTime fecha,
        String usuario
) {
}
