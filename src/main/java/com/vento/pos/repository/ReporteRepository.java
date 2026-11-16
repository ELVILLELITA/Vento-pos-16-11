package com.vento.pos.repository;

import com.vento.pos.dto.EmpleadoVentaDTO;
import com.vento.pos.dto.MetodoPagoVentaDTO;
import com.vento.pos.dto.ProductoVentaAggregateDTO;
import com.vento.pos.entity.Venta;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface ReporteRepository extends Repository<Venta, Long> {

    @Query("""
            select coalesce(sum(v.total), 0)
            from Venta v
            where (:desde is null or v.fecha >= :desde)
              and (:hasta is null or v.fecha <= :hasta)
              and v.estado in ('COMPLETADA', 'DEVUELTA', 'DEVOLUCION_PARCIAL')
            """)
    BigDecimal totalVentas(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    @Query("""
            select count(v)
            from Venta v
            where (:desde is null or v.fecha >= :desde)
              and (:hasta is null or v.fecha <= :hasta)
              and v.estado in ('COMPLETADA', 'DEVUELTA', 'DEVOLUCION_PARCIAL')
            """)
    Long totalTransacciones(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    @Query("""
            select new com.vento.pos.dto.EmpleadoVentaDTO(
                coalesce(u.nombreCompleto, u.username, 'Sin cajero'), coalesce(sum(v.total), 0), count(v))
            from Venta v left join v.usuario u
            where (:desde is null or v.fecha >= :desde)
              and (:hasta is null or v.fecha <= :hasta)
              and v.estado in ('COMPLETADA', 'DEVUELTA', 'DEVOLUCION_PARCIAL')
            group by u.id, u.nombreCompleto, u.username
            order by sum(v.total) desc
            """)
    List<EmpleadoVentaDTO> ventasPorEmpleado(
            @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    @Query("""
            select new com.vento.pos.dto.ProductoVentaAggregateDTO(
                p.id,
                sum(d.cantidad - coalesce(d.cantidadDevuelta, 0)),
                coalesce(sum((d.cantidad - coalesce(d.cantidadDevuelta, 0)) * d.precioUnitario), 0))
            from DetalleVenta d join d.producto p join d.venta v
            where (:desde is null or v.fecha >= :desde)
              and (:hasta is null or v.fecha <= :hasta)
              and v.estado in ('COMPLETADA', 'DEVUELTA', 'DEVOLUCION_PARCIAL')
              and d.cantidad > coalesce(d.cantidadDevuelta, 0)
            group by p.id, p.nombre, p.categoria
            """)
    List<ProductoVentaAggregateDTO> productosVendidos(
            @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    @Query("""
            select new com.vento.pos.dto.MetodoPagoVentaDTO(
                coalesce(v.metodoPago, 'No especificado'), coalesce(sum(v.total), 0))
            from Venta v
            where (:desde is null or v.fecha >= :desde)
              and (:hasta is null or v.fecha <= :hasta)
              and v.estado in ('COMPLETADA', 'DEVUELTA', 'DEVOLUCION_PARCIAL')
            group by coalesce(v.metodoPago, 'No especificado')
            order by coalesce(v.metodoPago, 'No especificado')
            """)
    List<MetodoPagoVentaDTO> ventasPorMetodoPago(
            @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);
}
