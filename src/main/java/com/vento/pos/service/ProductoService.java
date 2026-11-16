package com.vento.pos.service;

import com.vento.pos.dto.ProductoRequestDTO;
import com.vento.pos.dto.ProductoResponseDTO;

import java.util.List;

public interface ProductoService {

    List<ProductoResponseDTO> listar(String categoria, String busqueda, boolean incluirInactivos);

    ProductoResponseDTO obtener(Long id);

    ProductoResponseDTO crear(ProductoRequestDTO request);

    ProductoResponseDTO actualizar(Long id, ProductoRequestDTO request);

    void eliminar(Long id);

    List<String> listarCategorias();
}
