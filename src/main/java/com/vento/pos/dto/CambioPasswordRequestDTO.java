package com.vento.pos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CambioPasswordRequestDTO(
        @NotNull String passwordActual,
        @NotBlank @Size(min = 8, max = 72) String nuevaPassword
) {
}
