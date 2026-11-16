package com.vento.pos.repository;

import com.vento.pos.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    @Query("""
            select c from Cliente c
            where c.activo = true
              and (:q is null or lower(c.nombre) like lower(concat('%', :q, '%'))
                   or lower(coalesce(c.documento, '')) like lower(concat('%', :q, '%')))
            order by c.nombre
            """)
    List<Cliente> buscarActivos(@Param("q") String query);

    Optional<Cliente> findByIdAndActivoTrue(Long id);
}
