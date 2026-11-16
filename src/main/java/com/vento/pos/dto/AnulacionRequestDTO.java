package com.vento.pos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnulacionRequestDTO(
        @NotBlank @Size(max = 250) String motivo
) {
}
