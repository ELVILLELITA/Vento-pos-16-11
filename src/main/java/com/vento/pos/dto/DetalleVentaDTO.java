package com.vento.pos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record DetalleVentaDTO(
        @NotNull @Positive Long productoId,
        @NotNull @Positive Integer cantidad,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal descuentoManual
) {
}
