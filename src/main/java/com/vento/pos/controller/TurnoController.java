package com.vento.pos.controller;

import com.vento.pos.dto.AperturaTurnoRequestDTO;
import com.vento.pos.dto.CierreTurnoRequestDTO;
import com.vento.pos.dto.TurnoResponseDTO;
import com.vento.pos.service.TurnoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/turnos")
public class TurnoController {

    private final TurnoService turnoService;

    public TurnoController(TurnoService turnoService) {
        this.turnoService = turnoService;
    }

    @PostMapping("/apertura")
    @ResponseStatus(HttpStatus.CREATED)
    public TurnoResponseDTO abrir(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody AperturaTurnoRequestDTO request) {
        return turnoService.abrir(principal.getUsername(), request);
    }

    @GetMapping("/activo")
    public ResponseEntity<TurnoResponseDTO> activo(@AuthenticationPrincipal UserDetails principal) {
        TurnoResponseDTO turno = turnoService.obtenerActivo(principal.getUsername());
        return turno == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(turno);
    }

    @PostMapping("/cierre")
    public TurnoResponseDTO cerrar(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody CierreTurnoRequestDTO request) {
        return turnoService.cerrar(principal.getUsername(), request);
    }
}
