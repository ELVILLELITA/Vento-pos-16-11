package com.vento.pos.controller;

import com.vento.pos.dto.VentaRequestDTO;
import com.vento.pos.dto.VentaResponseDTO;
import com.vento.pos.dto.PaginaResponseDTO;
import com.vento.pos.dto.DevolucionRequestDTO;
import com.vento.pos.dto.DevolucionResponseDTO;
import com.vento.pos.dto.AnulacionRequestDTO;
import com.vento.pos.service.VentaService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ventas")
public class VentaController {

    private final VentaService ventaService;

    public VentaController(VentaService ventaService) {
        this.ventaService = ventaService;
    }

    @PostMapping
    public VentaResponseDTO crear(@Valid @RequestBody VentaRequestDTO request) {
        return ventaService.crearVenta(request);
    }

    @GetMapping
    public PaginaResponseDTO<VentaResponseDTO> historial(
            @RequestParam(required = false) String folioInterno,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaFin,
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "La página debe ser positiva y el tamaño debe estar entre 1 y 100.");
        }
        return PaginaResponseDTO.desde(ventaService.buscarHistorial(
                folioInterno, fechaInicio, fechaFin, usuarioId, PageRequest.of(page, size)));
    }

    @GetMapping("/{id}")
    public VentaResponseDTO detalle(@PathVariable Long id) {
        return ventaService.obtenerVenta(id);
    }

    @PostMapping("/{id}/devolucion")
    public ResponseEntity<DevolucionResponseDTO> devolver(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) DevolucionRequestDTO request,
            @AuthenticationPrincipal UserDetails principal) {
        DevolucionResponseDTO devolucion = ventaService.procesarDevolucion(id, request, principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(devolucion);
    }

    @PostMapping("/{id}/anulacion")
    public ResponseEntity<DevolucionResponseDTO> anular(
            @PathVariable Long id,
            @Valid @RequestBody AnulacionRequestDTO request,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ventaService.anularVenta(id, request.motivo(), principal.getUsername()));
    }

    @GetMapping("/tasa-iva")
    public Map<String, BigDecimal> tasaIva() {
        return Map.of("tasa", ventaService.obtenerTasaIva());
    }
}
