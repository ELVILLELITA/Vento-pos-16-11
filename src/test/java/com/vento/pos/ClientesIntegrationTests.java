package com.vento.pos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vento.pos.entity.Producto;
import com.vento.pos.entity.Turno;
import com.vento.pos.repository.ClienteRepository;
import com.vento.pos.repository.MovimientoInventarioRepository;
import com.vento.pos.repository.ProductoRepository;
import com.vento.pos.repository.VentaRepository;
import com.vento.pos.repository.UsuarioRepository;
import com.vento.pos.repository.TurnoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:clientesintegrationtest;DB_CLOSE_DELAY=-1",
        "vento.ventas.iva-tasa=0.13"
})
@AutoConfigureMockMvc
@WithMockUser(username = "cajero")
class ClientesIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private VentaRepository ventaRepository;

    @Autowired
    private MovimientoInventarioRepository movimientoInventarioRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TurnoRepository turnoRepository;

    @BeforeEach
    void limpiarDatos() {
        turnoRepository.deleteAll();
        movimientoInventarioRepository.deleteAll();
        ventaRepository.deleteAll();
        clienteRepository.deleteAll();
        productoRepository.deleteAll();
        turnoRepository.saveAndFlush(new Turno(
                usuarioRepository.findByUsername("cajero").orElseThrow(), new BigDecimal("0.00"), null));
    }

    @Test
    void registraBuscaActualizaYDeshabilitaClientes() throws Exception {
        String creado = mockMvc.perform(post("/api/v1/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"María López","tipoDocumento":"DUI","documento":"01234567-8","email":"maria@example.com","giro":"Actividades de programación informática","codigoActividad":"62010"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoDocumento").value("DUI"))
                .andExpect(jsonPath("$.giro").value("Actividades de programación informática"))
                .andExpect(jsonPath("$.codigoActividad").value("62010"))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.fechaRegistro").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(creado).get("id").asLong();

        mockMvc.perform(get("/api/v1/clientes").param("q", "01234567"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombre").value("María López"));

        mockMvc.perform(put("/api/v1/clientes/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"María López García","tipoDocumento":"NIT","documento":"0614-123456-101-0","giro":"Restaurantes y servicios móviles de comidas","codigoActividad":"56101"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("María López García"))
                .andExpect(jsonPath("$.codigoActividad").value("56101"))
                .andExpect(jsonPath("$.tipoDocumento").value("NIT"));

        mockMvc.perform(delete("/api/v1/clientes/{id}", id))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/clientes").param("q", "María"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void catalogoDeActividadesBuscaPorCodigoDescripcionYSinonimos() throws Exception {
        mockMvc.perform(get("/api/v1/catalogos/actividades-economicas").param("q", "56101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].codigo").value("56101"));

        mockMvc.perform(get("/api/v1/catalogos/actividades-economicas").param("q", "Café"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigo", org.hamcrest.Matchers.hasItem("56101")))
                .andExpect(jsonPath("$[*].codigo", org.hamcrest.Matchers.hasItem("01271")));

        mockMvc.perform(get("/api/v1/catalogos/actividades-economicas").param("q", "software"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigo", org.hamcrest.Matchers.hasItem("62010")));

        mockMvc.perform(get("/api/v1/catalogos/actividades-economicas").param("q", "62010"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].descripcion").value("Programación Informática"));

        mockMvc.perform(get("/api/v1/catalogos/actividades-economicas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(774)));
    }

    @Test
    void ventaGuardaElClienteSeleccionado() throws Exception {
        String creado = mockMvc.perform(post("/api/v1/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Cliente de venta","tipoDocumento":"NIT","documento":"0614-123456-101-0","nrc":"123456-7","giro":"Comercio"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long clienteId = objectMapper.readTree(creado).get("id").asLong();
        Producto producto = productoRepository.save(new Producto(
                "Producto de prueba", new BigDecimal("10.00"), "Café", true, 5));

        mockMvc.perform(post("/api/v1/ventas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productoId":%d,"cantidad":1}],"clienteId":%d,"tipoComprobante":"CREDITO_FISCAL"}
                                """.formatted(producto.getId(), clienteId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(clienteId))
                .andExpect(jsonPath("$.clienteNombre").value("Cliente de venta"))
                .andExpect(jsonPath("$.clienteNrc").value("123456-7"))
                .andExpect(jsonPath("$.clienteGiro").value("Comercio"))
                .andExpect(jsonPath("$.tipoComprobante").value("CREDITO_FISCAL"));
    }

    @Test
    void creditoFiscalRequiereClienteConDatosFiscales() throws Exception {
        Producto producto = productoRepository.save(new Producto(
                "Producto de prueba", new BigDecimal("10.00"), "Café", true, 5));

        mockMvc.perform(post("/api/v1/ventas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productoId":%d,"cantidad":1}],"tipoComprobante":"CREDITO_FISCAL"}
                                """.formatted(producto.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Selecciona un cliente para emitir un crédito fiscal."));

        String creado = mockMvc.perform(post("/api/v1/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Cliente sin datos","tipoDocumento":"DUI","documento":"01234567-8"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long clienteId = objectMapper.readTree(creado).get("id").asLong();

        mockMvc.perform(post("/api/v1/ventas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productoId":%d,"cantidad":1}],"clienteId":%d,"tipoComprobante":"CREDITO_FISCAL"}
                                """.formatted(producto.getId(), clienteId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "El cliente debe tener NRC, giro y documento DUI o NIT para emitir crédito fiscal."));
    }
}
