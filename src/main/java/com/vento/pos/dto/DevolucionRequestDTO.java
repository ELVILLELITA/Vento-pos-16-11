package com.vento.pos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DevolucionRequestDTO(
        List<@Valid DevolucionItemDTO> items,
        @NotBlank @Size(max = 250) String motivo
) {
}
