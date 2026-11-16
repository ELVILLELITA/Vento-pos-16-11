package com.vento.pos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CompraRequestDTO(
        @NotNull Long proveedorId,
        @Size(max = 80) String numeroFacturaProveedor,
        @NotEmpty List<@NotNull @Valid DetalleCompraRequestDTO> items
) {
}
