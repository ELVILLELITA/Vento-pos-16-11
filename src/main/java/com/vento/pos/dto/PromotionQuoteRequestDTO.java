package com.vento.pos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PromotionQuoteRequestDTO(@NotEmpty List<@NotNull @Valid DetalleVentaDTO> items) {
}
