package com.vento.pos.service;

import com.vento.pos.dto.ProveedorRequestDTO;
import com.vento.pos.dto.ProveedorResponseDTO;
import com.vento.pos.entity.Proveedor;
import com.vento.pos.repository.ProveedorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ProveedorService {

    private final ProveedorRepository repository;

    public ProveedorService(ProveedorRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ProveedorResponseDTO> listar(boolean incluirInactivos) {
        List<Proveedor> proveedores = incluirInactivos
                ? repository.findAllByOrderByNombreComercialAsc()
                : repository.findAllByActivoTrueOrderByNombreComercialAsc();
        return proveedores.stream().map(ProveedorService::convertir).toList();
    }

    @Transactional(readOnly = true)
    public ProveedorResponseDTO obtener(Long id) {
        Proveedor proveedor = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el proveedor " + id + "."));
        return convertir(proveedor);
    }

    @Transactional
    public ProveedorResponseDTO crear(ProveedorRequestDTO request) {
        Proveedor proveedor = new Proveedor(request.nombreComercial().trim());
        aplicar(proveedor, request);
        return convertir(repository.save(proveedor));
    }

    @Transactional
    public ProveedorResponseDTO actualizar(Long id, ProveedorRequestDTO request) {
        Proveedor proveedor = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el proveedor " + id + "."));
        aplicar(proveedor, request);
        return convertir(proveedor);
    }

    @Transactional
    public void deshabilitar(Long id) {
        Proveedor proveedor = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el proveedor " + id + "."));
        proveedor.setActivo(false);
    }

    private static void aplicar(Proveedor proveedor, ProveedorRequestDTO request) {
        proveedor.setNombreComercial(request.nombreComercial().trim());
        proveedor.setRazonSocial(limpiar(request.razonSocial()));
        proveedor.setNrc(limpiar(request.nrc()));
        proveedor.setNit(limpiar(request.nit()));
        proveedor.setTelefono(limpiar(request.telefono()));
        proveedor.setEmail(limpiar(request.email()));
        proveedor.setDireccion(limpiar(request.direccion()));
    }

    private static String limpiar(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ProveedorResponseDTO convertir(Proveedor proveedor) {
        return new ProveedorResponseDTO(
                proveedor.getId(), proveedor.getNombreComercial(), proveedor.getRazonSocial(), proveedor.getNrc(),
                proveedor.getNit(), proveedor.getTelefono(), proveedor.getEmail(), proveedor.getDireccion(),
                proveedor.isActivo());
    }
}
