package com.vento.pos.dto;

import com.vento.pos.entity.Rol;

import java.util.List;

public record UserProfileDTO(
        String username,
        String nombreCompleto,
        Rol rol,
        List<String> allowedModules
) {
}
