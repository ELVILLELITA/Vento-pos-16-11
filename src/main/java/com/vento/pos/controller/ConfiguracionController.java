package com.vento.pos.controller;

import com.vento.pos.dto.ConfiguracionSistemaDTO;
import com.vento.pos.dto.ConfiguracionDteRequestDTO;
import com.vento.pos.dto.ConfiguracionSistemaRequestDTO;
import com.vento.pos.service.ConfiguracionSistemaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/configuracion")
public class ConfiguracionController {

    private final ConfiguracionSistemaService service;

    public ConfiguracionController(ConfiguracionSistemaService service) {
        this.service = service;
    }

    @GetMapping
    public ConfiguracionSistemaDTO obtener() {
        return service.obtener();
    }

    @PutMapping
    public ConfiguracionSistemaDTO actualizar(@Valid @RequestBody ConfiguracionSistemaRequestDTO request) {
        return service.actualizar(request);
    }

    @PutMapping("/dte")
    public ConfiguracionSistemaDTO actualizarDte(@Valid @RequestBody ConfiguracionDteRequestDTO request) {
        return service.actualizarDte(request);
    }
}
