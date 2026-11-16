package com.vento.pos.repository;

import com.vento.pos.entity.DteDocumento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DteDocumentoRepository extends JpaRepository<DteDocumento, Long> {
    Optional<DteDocumento> findByVentaId(Long ventaId);
}
