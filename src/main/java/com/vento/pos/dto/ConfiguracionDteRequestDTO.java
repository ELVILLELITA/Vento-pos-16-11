package com.vento.pos.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ConfiguracionDteRequestDTO(
        @Pattern(regexp = "00|01") String ambienteDte,
        @Size(max = 30) String nitEmisor,
        @Size(max = 30) String nrcEmisor,
        @Size(max = 10) String codigoActividadEmisor,
        @Size(max = 10000) String clavePrivadaCertificado,
        @Size(max = 1000) String passwordCertificado,
        @Size(max = 500) @Pattern(regexp = "^$|https?://.+") String urlFirmador,
        @Size(max = 15) String codigoPuntoVentaMh
) {
}
