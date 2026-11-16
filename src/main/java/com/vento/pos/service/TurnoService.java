package com.vento.pos.service;

import com.vento.pos.dto.AperturaTurnoRequestDTO;
import com.vento.pos.dto.CierreTurnoRequestDTO;
import com.vento.pos.dto.TurnoResponseDTO;
import com.vento.pos.entity.EstadoTurno;
import com.vento.pos.entity.Turno;
import com.vento.pos.entity.Usuario;
import com.vento.pos.repository.DevolucionVentaRepository;
import com.vento.pos.repository.TurnoRepository;
import com.vento.pos.repository.UsuarioRepository;
import com.vento.pos.repository.VentaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
public class TurnoService {

    private final TurnoRepository turnoRepository;
    private final UsuarioRepository usuarioRepository;
    private final VentaRepository ventaRepository;
    private final DevolucionVentaRepository devolucionRepository;

    public TurnoService(
            TurnoRepository turnoRepository,
            UsuarioRepository usuarioRepository,
            VentaRepository ventaRepository,
            DevolucionVentaRepository devolucionRepository) {
        this.turnoRepository = turnoRepository;
        this.usuarioRepository = usuarioRepository;
        this.ventaRepository = ventaRepository;
        this.devolucionRepository = devolucionRepository;
    }

    @Transactional
    public TurnoResponseDTO abrir(String username, AperturaTurnoRequestDTO request) {
        Usuario usuario = usuarioRepository.findByUsernameForUpdate(username)
                .filter(found -> Boolean.TRUE.equals(found.getActivo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "El usuario no está activo."));
        if (turnoRepository.findActiveForUpdate(usuario.getId(), EstadoTurno.OPEN).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un turno abierto para este usuario.");
        }
        Turno turno = turnoRepository.saveAndFlush(
                new Turno(usuario, request.montoInicial().setScale(2, RoundingMode.HALF_UP), normalizar(request.observaciones())));
        return convertir(turno, turno.getFechaApertura());
    }

    @Transactional(readOnly = true)
    public TurnoResponseDTO obtenerActivo(String username) {
        Usuario usuario = buscarUsuarioActivo(username);
        return turnoRepository.findFirstByUsuarioIdAndEstadoOrderByFechaAperturaDesc(usuario.getId(), EstadoTurno.OPEN)
                .map(turno -> convertir(turno, LocalDateTime.now()))
                .orElse(null);
    }

    @Transactional
    public TurnoResponseDTO cerrar(String username, CierreTurnoRequestDTO request) {
        Usuario usuario = usuarioRepository.findByUsernameForUpdate(username)
                .filter(found -> Boolean.TRUE.equals(found.getActivo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "El usuario no está activo."));
        Turno turno = turnoRepository.findActiveForUpdate(usuario.getId(), EstadoTurno.OPEN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No hay un turno abierto para este usuario."));
        LocalDateTime cierre = LocalDateTime.now();
        BigDecimal esperado = efectivoEsperado(turno, cierre);
        turno.cerrar(cierre, esperado, request.efectivoReal().setScale(2, RoundingMode.HALF_UP), normalizar(request.observaciones()));
        turnoRepository.flush();
        return convertir(turno, cierre);
    }

    @Transactional
    public Turno exigirTurnoAbierto(String username) {
        Usuario usuario = buscarUsuarioActivo(username);
        return turnoRepository.findActiveForUpdate(usuario.getId(), EstadoTurno.OPEN)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Abre un turno de caja antes de registrar una venta."));
    }

    private Usuario buscarUsuarioActivo(String username) {
        return usuarioRepository.findByUsername(username)
                .filter(found -> Boolean.TRUE.equals(found.getActivo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "El usuario no está activo."));
    }

    private TurnoResponseDTO convertir(Turno turno, LocalDateTime hasta) {
        BigDecimal ventas = ventaRepository.totalEfectivoPorCajero(
                turno.getUsuario().getId(), turno.getFechaApertura(), hasta);
        BigDecimal devoluciones = devolucionRepository.totalEfectivoDevueltoPorCajero(
                turno.getUsuario().getId(), turno.getFechaApertura(), hasta);
        BigDecimal esperado = turno.getEstado() == EstadoTurno.CLOSED
                ? turno.getEfectivoEsperado()
                : turno.getMontoInicial().add(ventas).subtract(devoluciones).setScale(2, RoundingMode.HALF_UP);
        String cajero = turno.getUsuario().getNombreCompleto() == null
                ? turno.getUsuario().getUsername()
                : turno.getUsuario().getNombreCompleto();
        return new TurnoResponseDTO(
                turno.getId(), turno.getUsuario().getId(), cajero, turno.getFechaApertura(), turno.getFechaCierre(),
                turno.getMontoInicial(), ventas, devoluciones, esperado, turno.getEfectivoReal(), turno.getVariacion(),
                turno.getEstado(), turno.getObservaciones());
    }

    private BigDecimal efectivoEsperado(Turno turno, LocalDateTime hasta) {
        BigDecimal ventas = ventaRepository.totalEfectivoPorCajero(
                turno.getUsuario().getId(), turno.getFechaApertura(), hasta);
        BigDecimal devoluciones = devolucionRepository.totalEfectivoDevueltoPorCajero(
                turno.getUsuario().getId(), turno.getFechaApertura(), hasta);
        return turno.getMontoInicial().add(ventas).subtract(devoluciones).setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizar(String observaciones) {
        return observaciones == null || observaciones.isBlank() ? null : observaciones.trim();
    }
}
