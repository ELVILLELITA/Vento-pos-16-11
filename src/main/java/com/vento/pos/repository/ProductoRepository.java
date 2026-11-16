package com.vento.pos.repository;

import com.vento.pos.entity.Producto;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    @Query("""
            select p from Producto p
            where (:incluirInactivos = true or p.activo = true)
              and (:categoria is null or lower(p.categoria) = lower(:categoria))
              and (:busqueda is null
                   or lower(p.nombre) like lower(concat('%', :busqueda, '%'))
                   or lower(p.categoria) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(p.codigoBarras, '')) like lower(concat('%', :busqueda, '%')))
            order by p.id
            """)
    List<Producto> buscar(
            @Param("categoria") String categoria,
            @Param("busqueda") String busqueda,
            @Param("incluirInactivos") boolean incluirInactivos);

    List<Producto> findAllByOrderByNombreAsc();

    @Query("select distinct p.categoria from Producto p where p.activo = true order by p.categoria")
    List<String> findActiveCategories();

    boolean existsByNombreIgnoreCaseAndActivoTrue(String nombre);

    boolean existsByNombreIgnoreCaseAndActivoTrueAndIdNot(String nombre, Long id);

    @Modifying
    @Transactional
    @Query("update Producto p set p.activo = true where p.activo is null")
    int activarProductosExistentes();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Producto p where p.id = :id")
    Optional<Producto> findByIdForUpdate(@Param("id") Long id);
}
