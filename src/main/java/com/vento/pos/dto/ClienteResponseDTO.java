package com.vento.pos.dto;

import com.vento.pos.entity.TipoDocumento;

import java.time.LocalDateTime;

public record ClienteResponseDTO(
        Long id,
        String nombre,
        String documento,
        String nrc,
        String giro,
        String codigoActividad,
        String telefono,
        String email,
        String direccion,
        TipoDocumento tipoDocumento,
        LocalDateTime fechaRegistro,
        boolean activo
) {
}
