package com.vento.pos.dto;

import com.vento.pos.entity.Rol;

public record UsuarioResponseDTO(
        Long id,
        String username,
        String nombreCompleto,
        Rol rol,
        Boolean activo
) {
}
