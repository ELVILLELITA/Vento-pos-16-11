package com.vento.pos.controller;

import com.vento.pos.dto.VentaRequestDTO;
import com.vento.pos.dto.VentaResponseDTO;
import com.vento.pos.service.VentaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pos")
public class PosCheckoutController {

    private final VentaService ventaService;

    public PosCheckoutController(VentaService ventaService) {
        this.ventaService = ventaService;
    }

    @PostMapping("/cobrar")
    public VentaResponseDTO cobrar(@Valid @RequestBody VentaRequestDTO request) {
        return ventaService.crearVenta(request);
    }
}
