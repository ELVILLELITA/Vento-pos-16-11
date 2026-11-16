package com.vento.pos.repository;

import com.vento.pos.entity.Proveedor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {
    List<Proveedor> findAllByOrderByNombreComercialAsc();
    List<Proveedor> findAllByActivoTrueOrderByNombreComercialAsc();
    Optional<Proveedor> findByIdAndActivoTrue(Long id);
}
