package com.vento.pos.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Component
public class DteSignerClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public DteSignerClient(RestClient.Builder builder, ObjectMapper objectMapper) {
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
    }

    public String sign(String baseUrl, String nit, String privateKeyPassword, JsonNode dte) {
        String endpoint = baseUrl.replaceAll("/+$", "");
        if (!endpoint.endsWith("/firmardocumento")) endpoint += "/firmardocumento";
        ObjectNode request = objectMapper.createObjectNode();
        request.put("nit", nit);
        request.put("activo", true);
        request.put("passwordPri", privateKeyPassword);
        try {
            request.put("dteJson", objectMapper.writeValueAsString(dte));
            JsonNode response = restClient.post()
                    .uri(URI.create(endpoint + "/"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || response.isNull()) {
                throw new ResponseStatusException(BAD_GATEWAY, "El firmador DTE devolvió una respuesta vacía.");
            }
            String status = response.path("status").asText();
            if (!status.isBlank() && !"OK".equalsIgnoreCase(status)) {
                throw new ResponseStatusException(BAD_GATEWAY, "El firmador DTE rechazó el documento.");
            }
            return objectMapper.writeValueAsString(response);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(BAD_GATEWAY, "No se pudo comunicar con el firmador DTE.", exception);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("No se pudo serializar el documento DTE.", exception);
        }
    }
}
