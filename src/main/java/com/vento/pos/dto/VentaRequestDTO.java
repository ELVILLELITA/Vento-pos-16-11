package com.vento.pos.dto;

import com.vento.pos.entity.TipoComprobante;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record VentaRequestDTO(
        List<@NotNull @Valid DetalleVentaDTO> items,
        @DecimalMin(value = "0.00") @Digits(integer = 10, fraction = 2) BigDecimal descuento,
        String descuentoAuthorizationToken,
        @Size(max = 150) String compradorNombre,
        @Pattern(regexp = "Efectivo|Tarjeta|Mixto|Devolucion|Devolución|Cuscatlan|Nico|Credomatic|Mensajeria") String metodoPago,
        Long clienteId,
        TipoComprobante tipoComprobante,
        @Size(max = 32) String folioVentaOrigen,
        @Size(max = 32) String folioDevolucion,
        @Size(max = 3) List<@NotNull @Valid PagoParcialDTO> pagosParciales,
        boolean esExenta
) {
}
