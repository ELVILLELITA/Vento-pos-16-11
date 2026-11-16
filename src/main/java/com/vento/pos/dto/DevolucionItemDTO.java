package com.vento.pos.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DevolucionItemDTO(
        @NotNull Long productoId,
        @NotNull @Min(1) Integer cantidad
) {
}
