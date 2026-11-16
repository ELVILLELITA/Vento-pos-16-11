package com.vento.pos.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProveedorRequestDTO(
        @NotBlank @Size(max = 150) String nombreComercial,
        @Size(max = 180) String razonSocial,
        @Size(max = 30) String nrc,
        @Size(max = 30) String nit,
        @Size(max = 30) String telefono,
        @Email @Size(max = 150) String email,
        @Size(max = 250) String direccion
) {
}
