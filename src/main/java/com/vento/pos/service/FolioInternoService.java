package com.vento.pos.service;

import com.vento.pos.entity.CorrelativoOperacion;
import com.vento.pos.entity.TipoOperacion;
import com.vento.pos.repository.CorrelativoOperacionRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class FolioInternoService implements ApplicationRunner {

    private static final Pattern IDENTIFICADOR = Pattern.compile("[A-Z0-9]{1,10}");
    private final CorrelativoOperacionRepository correlativoRepository;
    private final ConfiguracionSistemaService configuracionService;

    public FolioInternoService(
            CorrelativoOperacionRepository correlativoRepository,
            ConfiguracionSistemaService configuracionService) {
        this.correlativoRepository = correlativoRepository;
        this.configuracionService = configuracionService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var configuracion = configuracionService.obtenerEntidad();
        String sucursal = validarIdentificador(configuracion.getCodigoSucursal(), "sucursal");
        String estacion = validarIdentificador(configuracion.getCodigoEstacion(), "estación");
        for (TipoOperacion tipo : TipoOperacion.values()) {
            if (!correlativoRepository.existsBySucursalAndEstacionAndTipoOperacion(sucursal, estacion, tipo)) {
                correlativoRepository.save(new CorrelativoOperacion(sucursal, estacion, tipo));
            }
        }
        correlativoRepository.flush();
    }

    @Transactional
    public String generar(TipoOperacion tipoOperacion) {
        var configuracion = configuracionService.obtenerEntidad();
        return generar(configuracion.getCodigoSucursal(), configuracion.getCodigoEstacion(), tipoOperacion);
    }

    @Transactional
    public String generar(String sucursal, String estacion, TipoOperacion tipoOperacion) {
        String sucursalValidada = validarIdentificador(sucursal, "sucursal");
        String estacionValidada = validarIdentificador(estacion, "estación");
        if (tipoOperacion == null) {
            throw new IllegalArgumentException("El tipo de operación es obligatorio.");
        }
        CorrelativoOperacion correlativo = correlativoRepository
                .buscarParaActualizar(sucursalValidada, estacionValidada, tipoOperacion)
                .orElseGet(() -> correlativoRepository.saveAndFlush(
                        new CorrelativoOperacion(sucursalValidada, estacionValidada, tipoOperacion)));
        long siguiente = correlativo.siguienteNumero();
        return sucursalValidada + estacionValidada + tipoOperacion.getCodigo()
                + String.format(Locale.ROOT, "%06d", siguiente);
    }

    private static String validarIdentificador(String value, String campo) {
        if (value == null || !IDENTIFICADOR.matcher(value).matches()) {
            throw new IllegalArgumentException("La " + campo + " debe tener de 1 a 10 caracteres alfanuméricos en mayúsculas.");
        }
        return value;
    }
}
