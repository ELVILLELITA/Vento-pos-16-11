package com.vento.pos.dto;

public record ProveedorResponseDTO(
        Long id,
        String nombreComercial,
        String razonSocial,
        String nrc,
        String nit,
        String telefono,
        String email,
        String direccion,
        boolean activo
) {
}
