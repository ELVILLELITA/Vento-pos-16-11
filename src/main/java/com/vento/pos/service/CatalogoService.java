package com.vento.pos.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vento.pos.dto.ActividadEconomicaDTO;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class CatalogoService {

    private static final Map<String, Set<String>> SINONIMOS = Map.of(
            "cafe", Set.of("56101", "56106", "56107", "56108", "56109"),
            "cafeteria", Set.of("56101", "56106", "56107", "56108", "56109"),
            "software", Set.of("62010", "62020", "62090")
    );

    private final List<ActividadEconomicaDTO> actividades;

    public CatalogoService(ObjectMapper objectMapper) throws IOException {
        ClassPathResource catalogo = new ClassPathResource("catalogos/actividades-economicas-cat-019.json");
        actividades = List.copyOf(objectMapper.readValue(
                catalogo.getInputStream(), new TypeReference<>() {}));
    }

    public List<ActividadEconomicaDTO> buscar(String query) {
        if (query == null || query.isBlank()) return actividades;
        String normalized = normalizar(query.trim());
        Set<String> codigosSinonimos = SINONIMOS.entrySet().stream()
                .filter(entry -> entry.getKey().contains(normalized) || normalized.contains(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream())
                .collect(java.util.stream.Collectors.toSet());
        return actividades.stream()
                .filter(actividad -> actividad.codigo().contains(query.trim())
                        || normalizar(actividad.descripcion()).contains(normalized)
                        || codigosSinonimos.contains(actividad.codigo()))
                .toList();
    }

    private String normalizar(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
