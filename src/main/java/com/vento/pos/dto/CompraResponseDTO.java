package com.vento.pos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CompraResponseDTO(
        Long id,
        String folioInterno,
        String numeroFacturaProveedor,
        Long proveedorId,
        String proveedor,
        LocalDateTime fecha,
        BigDecimal total,
        Long usuarioId,
        List<DetalleCompraResponseDTO> detalles
) {
}
