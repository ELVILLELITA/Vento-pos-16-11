package com.vento.pos.dto;

import com.vento.pos.entity.Rol;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ActualizarUsuarioRequestDTO(
        @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._-]+") String username,
        @NotBlank @Size(max = 150) String nombreCompleto,
        @NotNull Rol rol
) {
}
