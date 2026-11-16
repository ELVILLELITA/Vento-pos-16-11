package com.vento.pos.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardSummaryDTO(
        BigDecimal totalVentas,
        Long totalTransacciones,
        BigDecimal ticketMedio,
        BigDecimal totalDevoluciones,
        List<EmpleadoVentaDTO> ventasPorEmpleado,
        List<ProductoVentaDTO> topProductos,
        List<ProductoVentaDTO> menosVendidos,
        List<CategoriaVentaDTO> ventasPorCategoria,
        List<MetodoPagoVentaDTO> ventasPorMetodoPago
) {
}
