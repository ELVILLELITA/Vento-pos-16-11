package com.vento.pos.repository;

import com.vento.pos.entity.EstadoTurno;
import com.vento.pos.entity.Turno;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TurnoRepository extends JpaRepository<Turno, Long> {

    Optional<Turno> findFirstByUsuarioIdAndEstadoOrderByFechaAperturaDesc(Long usuarioId, EstadoTurno estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Turno t where t.usuario.id = :usuarioId and t.estado = :estado order by t.fechaApertura desc")
    Optional<Turno> findActiveForUpdate(@Param("usuarioId") Long usuarioId, @Param("estado") EstadoTurno estado);
}
