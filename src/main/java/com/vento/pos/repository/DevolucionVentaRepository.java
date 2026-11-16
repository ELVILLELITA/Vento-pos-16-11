package com.vento.pos.repository;

import com.vento.pos.entity.DevolucionVenta;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DevolucionVentaRepository extends JpaRepository<DevolucionVenta, Long> {

    List<DevolucionVenta> findAllByVentaIdOrderByFechaDesc(Long ventaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select d from DevolucionVenta d
            join fetch d.venta
            where d.folioDevolucion = :folio
            """)
    Optional<DevolucionVenta> findByFolioDevolucionForUpdate(@Param("folio") String folio);

    @Query("""
            select coalesce(sum(d.importe), 0)
            from DevolucionVenta d
            where (:desde is null or d.fecha >= :desde)
              and (:hasta is null or d.fecha <= :hasta)
            """)
    BigDecimal totalPorFecha(
            @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    @Query("""
            select coalesce(sum(d.importe), 0)
            from DevolucionVenta d join d.venta v
            where d.usuario.id = :usuarioId
              and lower(v.metodoPago) = 'efectivo'
              and d.fecha >= :desde and d.fecha <= :hasta
            """)
    BigDecimal totalEfectivoDevueltoPorCajero(
            @Param("usuarioId") Long usuarioId,
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);
}
