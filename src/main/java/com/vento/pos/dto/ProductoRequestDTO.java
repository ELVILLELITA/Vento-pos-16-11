package com.vento.pos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductoRequestDTO(
        @NotBlank @Size(max = 150) String nombre,
        @NotNull @DecimalMin(value = "0.00") @Digits(integer = 10, fraction = 2) BigDecimal precio,
        @NotBlank @Size(max = 60) String categoria,
        Boolean favorito,
        @NotNull @Min(0) Integer stock,
        Boolean activo,
        Boolean precioIncluyeIva,
        @Size(max = 80) String codigoBarras
) {
}
