package com.vento.pos.repository;

import com.vento.pos.entity.Venta;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface VentaRepository extends JpaRepository<Venta, Long> {

    Optional<Venta> findTopByOrderByIdDesc();

    Optional<Venta> findByFolioInterno(String folioInterno);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Venta v where v.folioInterno = :folio")
    Optional<Venta> findByFolioInternoForUpdate(@Param("folio") String folio);

    @Query("""
            select coalesce(sum(v.total), 0) from Venta v
            where v.usuario.id = :usuarioId
              and lower(v.metodoPago) = 'efectivo'
              and v.fecha >= :desde and v.fecha <= :hasta
            """)
    BigDecimal totalEfectivoPorCajero(
            @Param("usuarioId") Long usuarioId,
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);

    List<Venta> findAllByOrderByFechaDesc();

    @Query(value = """
            select v from Venta v left join fetch v.usuario u
            where (:folio is null or lower(v.folioInterno) like lower(concat('%', :folio, '%')))
              and (:desde is null or v.fecha >= :desde)
              and (:hasta is null or v.fecha <= :hasta)
              and (:usuarioId is null or u.id = :usuarioId)
            order by v.fecha desc, v.id desc
            """,
            countQuery = """
            select count(v) from Venta v left join v.usuario u
            where (:folio is null or lower(v.folioInterno) like lower(concat('%', :folio, '%')))
              and (:desde is null or v.fecha >= :desde)
              and (:hasta is null or v.fecha <= :hasta)
              and (:usuarioId is null or u.id = :usuarioId)
            """)
    Page<Venta> buscarHistorial(
            @Param("folio") String folio,
            @Param("desde") java.time.LocalDateTime desde,
            @Param("hasta") java.time.LocalDateTime hasta,
            @Param("usuarioId") Long usuarioId,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Venta v where v.id = :id")
    Optional<Venta> findByIdForUpdate(@Param("id") Long id);
}
