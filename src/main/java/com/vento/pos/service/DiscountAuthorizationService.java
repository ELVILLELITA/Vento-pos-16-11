package com.vento.pos.service;

import com.vento.pos.dto.DiscountAuthorizationRequestDTO;
import com.vento.pos.dto.DiscountAuthorizationResponseDTO;
import com.vento.pos.entity.DiscountAuthorization;
import com.vento.pos.entity.Rol;
import com.vento.pos.entity.Usuario;
import com.vento.pos.repository.DiscountAuthorizationRepository;
import com.vento.pos.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class DiscountAuthorizationService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UsuarioRepository usuarioRepository;
    private final DiscountAuthorizationRepository authorizationRepository;
    private final PasswordEncoder passwordEncoder;

    public DiscountAuthorizationService(UsuarioRepository usuarioRepository,
                                         DiscountAuthorizationRepository authorizationRepository,
                                         PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.authorizationRepository = authorizationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public DiscountAuthorizationResponseDTO authorize(DiscountAuthorizationRequestDTO request) {
        Usuario admin = usuarioRepository.findByUsername(request.username())
                .filter(Usuario::getActivo)
                .filter(user -> user.getRol() == Rol.ROLE_ADMIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Credenciales de administrador inválidas."));
        String encodedPassword = admin.getPassword();
        if (encodedPassword == null || encodedPassword.isBlank()
                || !passwordEncoder.matches(request.password(), encodedPassword)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Credenciales de administrador inválidas.");
        }
        String token = newToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);
        authorizationRepository.save(new DiscountAuthorization(hash(token), request.amount(), admin, expiresAt));
        return new DiscountAuthorizationResponseDTO(token, request.amount(), expiresAt);
    }

    @Transactional
    public DiscountAuthorization consume(String token, java.math.BigDecimal requestedAmount) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere autorización administrativa para el descuento manual.");
        }
        DiscountAuthorization authorization = authorizationRepository.findForUpdate(hash(token))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "La autorización de descuento no es válida."));
        if (authorization.getConsumedAt() != null || !authorization.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La autorización de descuento expiró o ya fue utilizada.");
        }
        if (!authorization.getApprovedBy().getActivo()
                || authorization.getApprovedBy().getRol() != Rol.ROLE_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El administrador aprobador ya no está autorizado.");
        }
        if (authorization.getAmount().compareTo(requestedAmount) != 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El importe no coincide con la autorización administrativa.");
        }
        authorization.setConsumedAt(LocalDateTime.now());
        return authorization;
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            return Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo validar la autorización de descuento.", exception);
        }
    }
}
