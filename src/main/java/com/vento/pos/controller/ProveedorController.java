package com.vento.pos.controller;

import com.vento.pos.dto.ProveedorRequestDTO;
import com.vento.pos.dto.ProveedorResponseDTO;
import com.vento.pos.service.ProveedorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/proveedores")
public class ProveedorController {

    private final ProveedorService proveedorService;

    public ProveedorController(ProveedorService proveedorService) {
        this.proveedorService = proveedorService;
    }

    @GetMapping
    public List<ProveedorResponseDTO> listar(@RequestParam(defaultValue = "false") boolean incluirInactivos) {
        return proveedorService.listar(incluirInactivos);
    }

    @GetMapping("/{id}")
    public ProveedorResponseDTO obtener(@PathVariable Long id) {
        return proveedorService.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProveedorResponseDTO crear(@Valid @RequestBody ProveedorRequestDTO request) {
        return proveedorService.crear(request);
    }

    @PutMapping("/{id}")
    public ProveedorResponseDTO actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ProveedorRequestDTO request) {
        return proveedorService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deshabilitar(@PathVariable Long id) {
        proveedorService.deshabilitar(id);
    }
}
