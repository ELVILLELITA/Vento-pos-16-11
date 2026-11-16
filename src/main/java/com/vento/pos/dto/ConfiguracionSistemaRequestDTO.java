package com.vento.pos.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ConfiguracionSistemaRequestDTO(
        @NotBlank @Size(max = 150) String nombreEstablecimiento,
        @Size(max = 250) String direccion,
        @Size(max = 40) String telefono,
        @NotBlank @Pattern(regexp = "[A-Z0-9]{1,10}") String codigoSucursal,
        @NotBlank @Pattern(regexp = "[A-Z0-9]{1,10}") String codigoEstacion,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal porcentajeIva,
        @NotBlank @Size(max = 5) String monedaSimbolo,
        @NotBlank @Size(max = 250) String piePaginaTicket
) {
}
