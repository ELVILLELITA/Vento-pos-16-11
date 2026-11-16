package com.vento.pos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vento.pos.service.DteSignerClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DteSignerClientTests {

    @Test
    void enviaDteAlFirmadorLocalYDevuelveLaRespuestaFirmada() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:8080/firmardocumento/"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "nit":"0614",
                          "activo":true,
                          "passwordPri":"secreto",
                          "dteJson":"{\\"identificacion\\":{\\"tipoDte\\":\\"01\\"}}"
                        }
                        """))
                .andRespond(withSuccess("""
                        {"status":"OK","body":{"firmaElectronica":"firma"}}
                        """, MediaType.APPLICATION_JSON));

        DteSignerClient client = new DteSignerClient(builder, new ObjectMapper());
        String signed = client.sign("http://localhost:8080/", "0614", "secreto",
                new ObjectMapper().readTree("{\"identificacion\":{\"tipoDte\":\"01\"}}"));

        assertTrue(signed.contains("\"firmaElectronica\":\"firma\""));
        server.verify();
    }
}
