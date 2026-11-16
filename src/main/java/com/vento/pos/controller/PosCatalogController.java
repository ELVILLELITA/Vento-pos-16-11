package com.vento.pos.controller;

import com.vento.pos.dto.ProductoResponseDTO;
import com.vento.pos.service.ProductoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pos/productos")
public class PosCatalogController {

    private final ProductoService productoService;

    public PosCatalogController(ProductoService productoService) {
        this.productoService = productoService;
    }

    @GetMapping
    public List<ProductoResponseDTO> listar(
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String busqueda) {
        return productoService.listar(categoria, busqueda, false);
    }
}
