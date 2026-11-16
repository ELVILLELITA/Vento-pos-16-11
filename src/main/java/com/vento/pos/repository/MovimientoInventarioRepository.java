package com.vento.pos.repository;

import com.vento.pos.entity.MovimientoInventario;
import com.vento.pos.entity.TipoMovimiento;
import com.vento.pos.entity.TipoOperacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Long> {

    @Query("""
            select m from MovimientoInventario m
            join fetch m.producto p
            left join fetch m.usuario u
            where (:productoId is null or p.id = :productoId)
              and (:tipo is null or m.tipo = :tipo)
              and (:tipoOperacion is null or m.tipoOperacion = :tipoOperacion)
              and (:desde is null or m.fecha >= :desde)
              and (:hasta is null or m.fecha <= :hasta)
            order by m.fecha desc, m.id desc
            """)
    List<MovimientoInventario> buscar(
            @Param("productoId") Long productoId,
            @Param("tipo") TipoMovimiento tipo,
            @Param("tipoOperacion") TipoOperacion tipoOperacion,
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);

    boolean existsByFolioMovimiento(String folioMovimiento);
}
