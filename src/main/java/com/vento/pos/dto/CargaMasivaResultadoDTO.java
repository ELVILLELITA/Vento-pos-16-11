package com.vento.pos.dto;

import java.util.List;

public record CargaMasivaResultadoDTO(
        int totalProcesados,
        int insercionesExitosas,
        List<String> errores
) {
}
