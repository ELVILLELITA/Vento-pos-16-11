package com.vento.pos.dto;

import com.vento.pos.entity.EstadoTurno;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TurnoResponseDTO(
        Long id,
        Long usuarioId,
        String cajero,
        LocalDateTime fechaApertura,
        LocalDateTime fechaCierre,
        BigDecimal montoInicial,
        BigDecimal ventasEfectivo,
        BigDecimal devolucionesEfectivo,
        BigDecimal efectivoEsperado,
        BigDecimal efectivoReal,
        BigDecimal variacion,
        EstadoTurno estado,
        String observaciones
) {
}
