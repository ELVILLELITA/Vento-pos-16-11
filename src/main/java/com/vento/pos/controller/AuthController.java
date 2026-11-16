package com.vento.pos.controller;

import com.vento.pos.dto.UserProfileDTO;
import com.vento.pos.dto.CambioPasswordRequestDTO;
import com.vento.pos.dto.DiscountAuthorizationRequestDTO;
import com.vento.pos.dto.DiscountAuthorizationResponseDTO;
import com.vento.pos.entity.Rol;
import com.vento.pos.entity.Usuario;
import com.vento.pos.repository.UsuarioRepository;
import com.vento.pos.service.UsuarioService;
import com.vento.pos.service.DiscountAuthorizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final List<String> USER_MODULES = List.of("pos", "ventas", "clientes", "configuracion");
    private static final List<String> ADMIN_MODULES = List.of(
            "pos", "ventas", "clientes", "inventario", "compras", "reportes", "configuracion");

    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;
    private final DiscountAuthorizationService discountAuthorizationService;

    public AuthController(UsuarioRepository usuarioRepository, UsuarioService usuarioService,
                          DiscountAuthorizationService discountAuthorizationService) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
        this.discountAuthorizationService = discountAuthorizationService;
    }

    @GetMapping("/csrf")
    public Map<String, String> tokenCsrf(CsrfToken csrfToken) {
        return Map.of(
                "headerName", csrfToken.getHeaderName(),
                "token", csrfToken.getToken());
    }

    @GetMapping("/me")
    public UserProfileDTO perfilActual(@AuthenticationPrincipal UserDetails principal) {
        Usuario usuario = usuarioRepository.findByUsername(principal.getUsername())
                .filter(found -> Boolean.TRUE.equals(found.getActivo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "El usuario no está activo."));
        List<String> allowedModules = usuario.getRol() == Rol.ROLE_ADMIN ? ADMIN_MODULES : USER_MODULES;
        return new UserProfileDTO(
                usuario.getUsername(),
                usuario.getNombreCompleto(),
                usuario.getRol(),
                allowedModules);
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cambiarPassword(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody CambioPasswordRequestDTO request) {
        usuarioService.cambiarPassword(principal.getUsername(), request);
    }

    @org.springframework.web.bind.annotation.PostMapping({"/discount-authorization", "/autorizar-descuento"})
    public DiscountAuthorizationResponseDTO autorizarDescuento(
            @Valid @RequestBody DiscountAuthorizationRequestDTO request) {
        return discountAuthorizationService.authorize(request);
    }
}
