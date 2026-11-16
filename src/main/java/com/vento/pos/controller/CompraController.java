package com.vento.pos.controller;

import com.vento.pos.dto.CompraRequestDTO;
import com.vento.pos.dto.CompraResponseDTO;
import com.vento.pos.entity.Usuario;
import com.vento.pos.repository.UsuarioRepository;
import com.vento.pos.service.CompraService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/compras")
public class CompraController {

    private final CompraService compraService;
    private final UsuarioRepository usuarioRepository;

    public CompraController(CompraService compraService, UsuarioRepository usuarioRepository) {
        this.compraService = compraService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping
    public List<CompraResponseDTO> listar() {
        return compraService.listar();
    }

    @GetMapping("/{id}")
    public CompraResponseDTO obtener(@PathVariable Long id) {
        return compraService.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompraResponseDTO registrar(
            @Valid @RequestBody CompraRequestDTO request,
            @AuthenticationPrincipal UserDetails principal) {
        Usuario usuario = usuarioRepository.findByUsername(principal.getUsername())
                .filter(found -> Boolean.TRUE.equals(found.getActivo()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "El usuario no está activo."));
        return compraService.registrar(request, usuario);
    }
}
