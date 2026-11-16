package com.vento.pos.repository;

import com.vento.pos.entity.CorrelativoOperacion;
import com.vento.pos.entity.TipoOperacion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CorrelativoOperacionRepository extends JpaRepository<CorrelativoOperacion, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from CorrelativoOperacion c
            where c.sucursal = :sucursal and c.estacion = :estacion and c.tipoOperacion = :tipo
            """)
    Optional<CorrelativoOperacion> buscarParaActualizar(
            @Param("sucursal") String sucursal,
            @Param("estacion") String estacion,
            @Param("tipo") TipoOperacion tipo);

    boolean existsBySucursalAndEstacionAndTipoOperacion(
            String sucursal, String estacion, TipoOperacion tipo);
}
