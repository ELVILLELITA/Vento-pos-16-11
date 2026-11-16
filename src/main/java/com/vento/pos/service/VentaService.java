package com.vento.pos.service;

import com.vento.pos.dto.VentaRequestDTO;
import com.vento.pos.dto.VentaResponseDTO;
import com.vento.pos.dto.DevolucionRequestDTO;
import com.vento.pos.dto.DevolucionResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDateTime;

public interface VentaService {

    VentaResponseDTO crearVenta(VentaRequestDTO request);

    List<VentaResponseDTO> obtenerHistorial();

    Page<VentaResponseDTO> buscarHistorial(
            String folioInterno, LocalDateTime fechaInicio, LocalDateTime fechaFin, Long usuarioId, Pageable pageable);

    VentaResponseDTO obtenerVenta(Long id);

    DevolucionResponseDTO procesarDevolucion(Long ventaId, DevolucionRequestDTO request, String username);

    DevolucionResponseDTO anularVenta(Long ventaId, String motivo, String username);

    BigDecimal obtenerTasaIva();
}
