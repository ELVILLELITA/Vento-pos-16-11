package com.vento.pos.service;

import com.vento.pos.dto.CambioPasswordRequestDTO;
import com.vento.pos.dto.CambiarRolRequestDTO;
import com.vento.pos.dto.CrearUsuarioRequestDTO;
import com.vento.pos.dto.ActualizarUsuarioRequestDTO;
import com.vento.pos.dto.EstadoUsuarioRequestDTO;
import com.vento.pos.dto.ResetPasswordRequestDTO;
import com.vento.pos.dto.UsuarioResponseDTO;
import com.vento.pos.entity.Usuario;
import com.vento.pos.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class UsuarioService {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponseDTO> listar() {
        return repository.findAllByOrderByNombreCompletoAsc().stream().map(this::convertir).toList();
    }

    @Transactional
    public UsuarioResponseDTO crear(CrearUsuarioRequestDTO request) {
        if (repository.existsByUsernameIgnoreCase(request.username().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El nombre de usuario ya está registrado.");
        }
        Usuario usuario = new Usuario(
                request.username().trim(),
                passwordEncoder.encode(request.password()),
                request.nombreCompleto().trim(),
                request.rol(),
                true);
        return convertir(repository.save(usuario));
    }

    @Transactional
    public UsuarioResponseDTO cambiarRol(Long id, CambiarRolRequestDTO request, String actor) {
        Usuario usuario = buscar(id);
        if (usuario.getUsername().equalsIgnoreCase(actor) && request.rol() != usuario.getRol()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No puedes cambiar tu propio rol.");
        }
        usuario.setRol(request.rol());
        return convertir(usuario);
    }

    @Transactional
    public UsuarioResponseDTO actualizar(Long id, ActualizarUsuarioRequestDTO request, String actor) {
        Usuario usuario = buscar(id);
        String username = request.username().trim();
        boolean esActor = usuario.getUsername().equalsIgnoreCase(actor);
        if (esActor && (!usuario.getUsername().equalsIgnoreCase(username) || request.rol() != usuario.getRol())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No puedes cambiar tu propio usuario o rol.");
        }
        if (!usuario.getUsername().equalsIgnoreCase(username)
                && repository.existsByUsernameIgnoreCaseAndIdNot(username, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El nombre de usuario ya está registrado.");
        }
        usuario.setUsername(username);
        usuario.setNombreCompleto(request.nombreCompleto().trim());
        usuario.setRol(request.rol());
        return convertir(usuario);
    }

    @Transactional
    public UsuarioResponseDTO cambiarEstado(Long id, EstadoUsuarioRequestDTO request, String actor) {
        Usuario usuario = buscar(id);
        if (!request.activo() && usuario.getUsername().equalsIgnoreCase(actor)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No puedes deshabilitar tu propia cuenta.");
        }
        usuario.setActivo(request.activo());
        return convertir(usuario);
    }

    @Transactional
    public void cambiarPassword(String username, CambioPasswordRequestDTO request) {
        Usuario usuario = repository.findByUsername(username)
                .filter(found -> Boolean.TRUE.equals(found.getActivo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el usuario."));
        String actual = usuario.getPassword();
        boolean coincide = actual == null || actual.isBlank()
                ? request.passwordActual().isEmpty()
                : passwordEncoder.matches(request.passwordActual(), actual);
        if (!coincide) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña actual no es correcta.");
        }
        usuario.setPassword(passwordEncoder.encode(request.nuevaPassword()));
    }

    @Transactional
    public void restablecerPassword(Long id, ResetPasswordRequestDTO request) {
        Usuario usuario = buscar(id);
        usuario.setPassword(passwordEncoder.encode(request.nuevaPassword()));
    }

    private Usuario buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el usuario."));
    }

    private UsuarioResponseDTO convertir(Usuario usuario) {
        return new UsuarioResponseDTO(
                usuario.getId(), usuario.getUsername(), usuario.getNombreCompleto(), usuario.getRol(), usuario.getActivo());
    }
}
