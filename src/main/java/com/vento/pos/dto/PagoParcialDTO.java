package com.vento.pos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PagoParcialDTO(
        @NotBlank
        @Pattern(regexp = "Efectivo|Devolucion|Devolución|Cuscatlan|Nico|Credomatic|Mensajeria")
        String metodoPago,
        @DecimalMin(value = "0.01")
        @Digits(integer = 10, fraction = 2)
        BigDecimal monto,
        @Pattern(regexp = "\\d{4}")
        String ultimos4Digitos,
        @Size(max = 100)
        String codigoAutorizacion,
        @Size(max = 32)
        String folioDevolucion
) {
}
