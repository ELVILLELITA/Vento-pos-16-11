package com.vento.pos.controller;

import com.vento.pos.dto.ConfiguracionDteRequestDTO;
import com.vento.pos.dto.ConfiguracionSistemaDTO;
import com.vento.pos.dto.DteDocumentoResponseDTO;
import com.vento.pos.service.ConfiguracionSistemaService;
import com.vento.pos.service.DteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dte")
public class DteController {
    private final ConfiguracionSistemaService configuracionService;
    private final DteService dteService;

    public DteController(ConfiguracionSistemaService configuracionService, DteService dteService) {
        this.configuracionService = configuracionService;
        this.dteService = dteService;
    }

    @GetMapping("/configuracion")
    public ConfiguracionSistemaDTO obtenerConfiguracion() {
        return configuracionService.obtener();
    }

    @PutMapping("/configuracion")
    public ConfiguracionSistemaDTO actualizarConfiguracion(@Valid @RequestBody ConfiguracionDteRequestDTO request) {
        return configuracionService.actualizarDte(request);
    }

    @PostMapping("/ventas/{ventaId}/firmar")
    public DteDocumentoResponseDTO firmarVenta(@PathVariable Long ventaId) {
        return dteService.firmarVenta(ventaId);
    }
}
