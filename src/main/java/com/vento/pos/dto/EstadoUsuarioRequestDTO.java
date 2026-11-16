package com.vento.pos.dto;

import jakarta.validation.constraints.NotNull;

public record EstadoUsuarioRequestDTO(@NotNull Boolean activo) {
}
