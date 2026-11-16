package com.vento.pos.service;

import com.vento.pos.dto.ConfiguracionSistemaDTO;
import com.vento.pos.dto.ConfiguracionDteRequestDTO;
import com.vento.pos.dto.ConfiguracionSistemaRequestDTO;
import com.vento.pos.entity.ConfiguracionSistema;
import com.vento.pos.repository.ConfiguracionSistemaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfiguracionSistemaService {

    private static final long ID_CONFIGURACION = 1L;
    private static final String SECRETO_ENMASCARADO = "••••••••";
    private final ConfiguracionSistemaRepository repository;

    public ConfiguracionSistemaService(ConfiguracionSistemaRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ConfiguracionSistema obtenerEntidad() {
        return repository.findById(ID_CONFIGURACION)
                .orElseGet(() -> repository.saveAndFlush(new ConfiguracionSistema()));
    }

    @Transactional(readOnly = true)
    public ConfiguracionSistemaDTO obtener() {
        return convertir(obtenerEntidad());
    }

    @Transactional
    public ConfiguracionSistemaDTO actualizar(ConfiguracionSistemaRequestDTO request) {
        ConfiguracionSistema configuracion = repository.findById(ID_CONFIGURACION)
                .orElseGet(ConfiguracionSistema::new);
        configuracion.setNombreEstablecimiento(request.nombreEstablecimiento().trim());
        configuracion.setDireccion(limpiar(request.direccion()));
        configuracion.setTelefono(limpiar(request.telefono()));
        configuracion.setCodigoSucursal(request.codigoSucursal());
        configuracion.setCodigoEstacion(request.codigoEstacion());
        configuracion.setPorcentajeIva(request.porcentajeIva());
        configuracion.setMonedaSimbolo(request.monedaSimbolo().trim());
        configuracion.setPiePaginaTicket(request.piePaginaTicket().trim());
        return convertir(repository.save(configuracion));
    }

    @Transactional
    public ConfiguracionSistemaDTO actualizarDte(ConfiguracionDteRequestDTO request) {
        ConfiguracionSistema configuracion = repository.findById(ID_CONFIGURACION)
                .orElseGet(ConfiguracionSistema::new);
        if (request.ambienteDte() != null) configuracion.setAmbienteDte(limpiar(request.ambienteDte()));
        if (request.nitEmisor() != null) configuracion.setNitEmisor(limpiar(request.nitEmisor()));
        if (request.nrcEmisor() != null) configuracion.setNrcEmisor(limpiar(request.nrcEmisor()));
        if (request.codigoActividadEmisor() != null) configuracion.setCodigoActividadEmisor(limpiar(request.codigoActividadEmisor()));
        if (request.clavePrivadaCertificado() != null && !request.clavePrivadaCertificado().isBlank()) {
            configuracion.setClavePrivadaCertificado(request.clavePrivadaCertificado());
        }
        if (request.passwordCertificado() != null && !request.passwordCertificado().isBlank()) {
            configuracion.setPasswordCertificado(request.passwordCertificado());
        }
        if (request.urlFirmador() != null && !request.urlFirmador().isBlank()) {
            configuracion.setUrlFirmador(request.urlFirmador().trim());
        }
        if (request.codigoPuntoVentaMh() != null) configuracion.setCodigoPuntoVentaMh(limpiar(request.codigoPuntoVentaMh()));
        return convertir(repository.save(configuracion));
    }

    private ConfiguracionSistemaDTO convertir(ConfiguracionSistema configuracion) {
        return new ConfiguracionSistemaDTO(
                configuracion.getId(),
                configuracion.getNombreEstablecimiento(),
                configuracion.getDireccion(),
                configuracion.getTelefono(),
                configuracion.getCodigoSucursal(),
                configuracion.getCodigoEstacion(),
                configuracion.getPorcentajeIva(),
                configuracion.getMonedaSimbolo(),
                configuracion.getPiePaginaTicket(),
                configuracion.getAmbienteDte(),
                configuracion.getNitEmisor(),
                configuracion.getNrcEmisor(),
                configuracion.getCodigoActividadEmisor(),
                configuracion.getClavePrivadaCertificado() == null ? "" : SECRETO_ENMASCARADO,
                configuracion.getClavePrivadaCertificado() != null && !configuracion.getClavePrivadaCertificado().isBlank(),
                configuracion.getPasswordCertificado() == null ? "" : SECRETO_ENMASCARADO,
                configuracion.getPasswordCertificado() != null && !configuracion.getPasswordCertificado().isBlank(),
                configuracion.getUrlFirmador(),
                configuracion.getCodigoPuntoVentaMh());
    }

    private String limpiar(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
