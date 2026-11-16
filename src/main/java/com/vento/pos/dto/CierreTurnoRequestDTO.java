package com.vento.pos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CierreTurnoRequestDTO(
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal efectivoReal,
        @Size(max = 500) String observaciones
) {
}
