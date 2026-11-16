package com.vento.pos.controller;

import com.vento.pos.dto.AjusteInventarioRequestDTO;
import com.vento.pos.dto.MovimientoInventarioResponseDTO;
import com.vento.pos.entity.TipoMovimiento;
import com.vento.pos.entity.TipoOperacion;
import com.vento.pos.entity.Usuario;
import com.vento.pos.repository.UsuarioRepository;
import com.vento.pos.service.InventarioService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/inventario")
public class InventarioController {

    private final InventarioService inventarioService;
    private final UsuarioRepository usuarioRepository;

    public InventarioController(InventarioService inventarioService, UsuarioRepository usuarioRepository) {
        this.inventarioService = inventarioService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/movimientos")
    public List<MovimientoInventarioResponseDTO> movimientos(
            @RequestParam(required = false) Long productoId,
            @RequestParam(required = false) TipoMovimiento tipo,
            @RequestParam(required = false) TipoOperacion tipoOperacion,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta) {
        return inventarioService.listarMovimientos(productoId, tipo, tipoOperacion, desde, hasta);
    }

    @PostMapping("/ajuste")
    public MovimientoInventarioResponseDTO ajustar(
            @Valid @RequestBody AjusteInventarioRequestDTO request,
            @AuthenticationPrincipal UserDetails principal) {
        Usuario admin = usuarioRepository.findByUsername(principal.getUsername())
                .filter(usuario -> Boolean.TRUE.equals(usuario.getActivo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "El usuario no está activo."));
        return inventarioService.registrarAjuste(
                request.productoId(),
                request.cantidad(),
                request.tipo(),
                request.tipoOperacion(),
                request.motivo(),
                admin);
    }
}
