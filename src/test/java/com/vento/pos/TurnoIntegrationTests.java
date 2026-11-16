package com.vento.pos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vento.pos.entity.Producto;
import com.vento.pos.repository.DevolucionVentaRepository;
import com.vento.pos.repository.MovimientoInventarioRepository;
import com.vento.pos.repository.ProductoRepository;
import com.vento.pos.repository.TurnoRepository;
import com.vento.pos.repository.VentaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:turnointegrationtest;DB_CLOSE_DELAY=-1",
        "vento.ventas.iva-tasa=0.13"
})
@AutoConfigureMockMvc
class TurnoIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private TurnoRepository turnoRepository;
    @Autowired private VentaRepository ventaRepository;
    @Autowired private DevolucionVentaRepository devolucionRepository;
    @Autowired private MovimientoInventarioRepository movimientoRepository;

    private Producto producto;

    @BeforeEach
    void preparar() {
        turnoRepository.deleteAll();
        movimientoRepository.deleteAll();
        devolucionRepository.deleteAll();
        ventaRepository.deleteAll();
        productoRepository.deleteAll();
        producto = productoRepository.save(new Producto("Producto caja", new BigDecimal("10.00"), "Café", true, 20));
    }

    @Test
    @WithMockUser(username = "cajero")
    void checkoutSinTurnoAbiertoFallaConConflict() throws Exception {
        mockMvc.perform(post("/api/v1/ventas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productoId":%d,"cantidad":1}]}
                                """.formatted(producto.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Abre un turno de caja antes de registrar una venta."));
        assertEquals(0, ventaRepository.count());
    }

    @Test
    @WithMockUser(username = "cajero")
    void aperturaVentaDevolucionCierreYVariacionSeCalculanParaElCajero() throws Exception {
        mockMvc.perform(post("/api/v1/turnos/apertura")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"montoInicial":40.00,"observaciones":"Fondo inicial"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("OPEN"))
                .andExpect(jsonPath("$.montoInicial").value(40.0))
                .andExpect(jsonPath("$.cajero").value("Cajero Turno 1"));

        mockMvc.perform(post("/api/v1/turnos/apertura")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"montoInicial":20.00}
                                """))
                .andExpect(status().isConflict());

        String cashSaleJson = mockMvc.perform(post("/api/v1/ventas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productoId":%d,"cantidad":1}],"metodoPago":"Efectivo"}
                                """.formatted(producto.getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode cashSale = objectMapper.readTree(cashSaleJson);
        mockMvc.perform(get("/api/v1/turnos/activo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.efectivoEsperado").value(50.0));

        mockMvc.perform(post("/api/v1/ventas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productoId":%d,"cantidad":1}],"metodoPago":"Tarjeta"}
                                """.formatted(producto.getId())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/turnos/activo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.efectivoEsperado").value(50.0));

        mockMvc.perform(post("/api/v1/ventas/{id}/devolucion", cashSale.get("id").asLong())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"motivo":"Reembolso de prueba","items":[{"productoId":%d,"cantidad":1}]}
                                """.formatted(producto.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importe").value(10.0));

        mockMvc.perform(get("/api/v1/turnos/activo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ventasEfectivo").value(10.0))
                .andExpect(jsonPath("$.devolucionesEfectivo").value(10.0))
                .andExpect(jsonPath("$.efectivoEsperado").value(40.0));

        mockMvc.perform(post("/api/v1/turnos/cierre")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"efectivoReal":38.00,"observaciones":"Diferencia de caja"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CLOSED"))
                .andExpect(jsonPath("$.efectivoEsperado").value(40.0))
                .andExpect(jsonPath("$.efectivoReal").value(38.0))
                .andExpect(jsonPath("$.variacion").value(-2.0))
                .andExpect(jsonPath("$.observaciones").isNotEmpty());

        mockMvc.perform(get("/api/v1/turnos/activo"))
                .andExpect(status().isNoContent());
    }
}
