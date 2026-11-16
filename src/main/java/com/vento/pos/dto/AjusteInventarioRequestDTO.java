package com.vento.pos.dto;

import com.vento.pos.entity.TipoMovimiento;
import com.vento.pos.entity.TipoOperacion;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AjusteInventarioRequestDTO(
        @NotNull Long productoId,
        @NotNull @Min(1) Integer cantidad,
        @NotNull TipoMovimiento tipo,
        @NotNull TipoOperacion tipoOperacion,
        @NotBlank @Size(max = 250) String motivo
) {
}
