package com.vento.pos.dto;

import com.vento.pos.entity.Rol;
import jakarta.validation.constraints.NotNull;

public record CambiarRolRequestDTO(@NotNull Rol rol) {
}
