package com.vento.pos.dto;

import com.vento.pos.entity.DteDocumento;

import java.time.LocalDateTime;

public record DteDocumentoResponseDTO(
        Long id,
        Long ventaId,
        String tipoDte,
        String codigoGeneracion,
        LocalDateTime firmadoEn,
        String documentoFirmado
) {
    public static DteDocumentoResponseDTO from(DteDocumento document) {
        return new DteDocumentoResponseDTO(
                document.getId(), document.getVentaId(), document.getTipoDte(),
                document.getCodigoGeneracion(), document.getFirmadoEn(), document.getDocumentoFirmado());
    }
}
