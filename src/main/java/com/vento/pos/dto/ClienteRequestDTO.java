package com.vento.pos.dto;

import com.vento.pos.entity.TipoDocumento;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClienteRequestDTO(
        @NotBlank @Size(max = 150) String nombre,
        @Size(max = 30) String documento,
        @Size(max = 30) String nrc,
        @Size(max = 150) String giro,
        @Size(max = 10) String codigoActividad,
        @Size(max = 30) String telefono,
        @Email @Size(max = 150) String email,
        @Size(max = 250) String direccion,
        TipoDocumento tipoDocumento
) {
}
