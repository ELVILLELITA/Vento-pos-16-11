package com.vento.pos.controller;

import com.vento.pos.dto.CambiarRolRequestDTO;
import com.vento.pos.dto.CrearUsuarioRequestDTO;
import com.vento.pos.dto.ActualizarUsuarioRequestDTO;
import com.vento.pos.dto.EstadoUsuarioRequestDTO;
import com.vento.pos.dto.ResetPasswordRequestDTO;
import com.vento.pos.dto.UsuarioResponseDTO;
import com.vento.pos.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuarioController {

    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    @GetMapping
    public List<UsuarioResponseDTO> listar() {
        return service.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponseDTO crear(@Valid @RequestBody CrearUsuarioRequestDTO request) {
        return service.crear(request);
    }

    @PutMapping("/{id}/rol")
    public UsuarioResponseDTO cambiarRol(
            @PathVariable Long id,
            @Valid @RequestBody CambiarRolRequestDTO request,
            @AuthenticationPrincipal UserDetails principal) {
        return service.cambiarRol(id, request, principal.getUsername());
    }

    @PutMapping("/{id}")
    public UsuarioResponseDTO actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarUsuarioRequestDTO request,
            @AuthenticationPrincipal UserDetails principal) {
        return service.actualizar(id, request, principal.getUsername());
    }

    @PutMapping("/{id}/estado")
    public UsuarioResponseDTO cambiarEstado(
            @PathVariable Long id,
            @Valid @RequestBody EstadoUsuarioRequestDTO request,
            @AuthenticationPrincipal UserDetails principal) {
        return service.cambiarEstado(id, request, principal.getUsername());
    }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restablecerPassword(
            @PathVariable Long id, @Valid @RequestBody ResetPasswordRequestDTO request) {
        service.restablecerPassword(id, request);
    }
}
