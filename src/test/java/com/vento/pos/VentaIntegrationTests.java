package com.vento.pos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vento.pos.entity.MovimientoInventario;
import com.vento.pos.entity.Producto;
import com.vento.pos.entity.Turno;
import com.vento.pos.entity.Promotion;
import com.vento.pos.entity.PromotionType;
import com.vento.pos.repository.DevolucionVentaRepository;
import com.vento.pos.repository.MovimientoInventarioRepository;
import com.vento.pos.repository.ProductoRepository;
import com.vento.pos.repository.UsuarioRepository;
import com.vento.pos.repository.VentaRepository;
import com.vento.pos.repository.PromotionRepository;
import com.vento.pos.repository.TurnoRepository;
import com.vento.pos.service.FolioInternoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ventointegrationtest;DB_CLOSE_DELAY=-1",
        "vento.ventas.iva-tasa=0.13"
})
@AutoConfigureMockMvc
@WithMockUser(username = "cajero")
class VentaIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TurnoRepository turnoRepository;

    @Autowired
    private VentaRepository ventaRepository;

    @Autowired
    private MovimientoInventarioRepository movimientoInventarioRepository;

    @Autowired
    private DevolucionVentaRepository devolucionVentaRepository;

    @Autowired
    private FolioInternoService folioInternoService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PromotionRepository promotionRepository;

    private Producto producto;

    @BeforeEach
    void prepararDatos() {
        turnoRepository.deleteAll();
        movimientoInventarioRepository.deleteAll();
        devolucionVentaRepository.deleteAll();
        ventaRepository.deleteAll();
        promotionRepository.deleteAll();
        productoRepository.deleteAll();
        producto = productoRepository.save(new Producto(
                "Producto de prueba", new BigDecimal("100.00"), "Café", true, 5));
        var administrador = usuarioRepository.findByUsername("admin").orElseThrow();
        administrador.setPassword(passwordEncoder.encode("AdminPass123!"));
        usuarioRepository.save(administrador);
        turnoRepository.saveAndFlush(new Turno(
                usuarioRepository.findByUsername("cajero").orElseThrow(), new BigDecimal("0.00"), null));
        turnoRepository.saveAndFlush(new Turno(
                usuarioRepository.findByUsername("admin").orElseThrow(), new BigDecimal("0.00"), null));
    }

    @Test
    void ventaRegistraDetallesImpuestoDescuentoYDescuentaExistencias() throws Exception {
        String authorization = autorizarDescuento("10.00");
        mockMvc.perform(post("/api/v1/ventas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {"productoId": %d, "cantidad": 2},
                                    {"productoId": %d, "cantidad": 1}
                                  ],
                                  "descuento": 10.00,
                                  "descuentoAuthorizationToken": "%s",
                                  "compradorNombre": "  Cliente de prueba  "
                                }
                                """.formatted(producto.getId(), producto.getId(), authorization)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroOrden").value(matchesPattern("#3274\\d+")))
                .andExpect(jsonPath("$.subtotal").value(265.5))
                .andExpect(jsonPath("$.iva").value(33.2))
                .andExpect(jsonPath("$.descuento").value(10.0))
                .andExpect(jsonPath("$.total").value(288.7))
                .andExpect(jsonPath("$.estado").value("COMPLETADA"))
                .andExpect(jsonPath("$.compradorNombre").value("Cliente de prueba"))
                .andExpect(jsonPath("$.tipoComprobante").value("CONSUMIDOR_FINAL"))
                .andExpect(jsonPath("$.folioInterno").value(matchesPattern("S01C01CA\\d{6}")))
                .andExpect(jsonPath("$.detalles", hasSize(1)))
                .andExpect(jsonPath("$.detalles[0].cantidad").value(3));

        org.junit.jupiter.api.Assertions.assertEquals(2, productoRepository.findById(producto.getId()).orElseThrow().getStock());
        org.junit.jupiter.api.Assertions.assertEquals(1, ventaRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(1, movimientoInventarioRepository.count());
        MovimientoInventario movimiento = movimientoInventarioRepository.findAll().get(0);
        org.junit.jupiter.api.Assertions.assertEquals("VENTA", movimiento.getTipo().name());
        org.junit.jupiter.api.Assertions.assertEquals("CA", movimiento.getTipoOperacion().name());
        org.junit.jupiter.api.Assertions.assertTrue(movimiento.getFolioReferencia().startsWith("S01C01CA"));
        org.junit.jupiter.api.Assertions.assertEquals(5, movimiento.getStockAnterior());
        org.junit.jupiter.api.Assertions.assertEquals(2, movimiento.getStockNuevo());
        org.junit.jupiter.api.Assertions.assertEquals(
                "cajero",
                jdbcTemplate.queryForObject(
                        "SELECT u.username FROM ventas v JOIN usuarios u ON v.usuario_id = u.id",
                        String.class));
    }

    @Test
    void calculaIvaIncluidoYPrecioMasIvaEnLaMismaVenta() throws Exception {
        Producto incluido = productoRepository.save(new Producto(
                "Precio final", new BigDecimal("42.00"), "Café", true, 5));
        Producto masIva = new Producto(
                "Precio base", new BigDecimal("42.00"), "Café", true, 5);
        masIva.setPrecioIncluyeIva(false);
        masIva = productoRepository.save(masIva);

        mockMvc.perform(post("/api/v1/ventas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productoId":%d,"cantidad":1},{"productoId":%d,"cantidad":1}]}
                                """.formatted(incluido.getId(), masIva.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(79.17))
                .andExpect(jsonPath("$.iva").value(10.29))
                .andExpect(jsonPath("$.total").value(89.46))
                .andExpect(jsonPath("$.detalles[0].precioUnitario").value(42.0))
                .andExpect(jsonPath("$.detalles[1].precioUnitario").value(47.46));
    }

    @Test
    void existenciaInsuficienteNoGuardaLaVentaNiDescuentaStock() throws Exception {
        mockMvc.perform(post("/api/v1/ventas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productoId":%d,"cantidad":6}]}
                                """.formatted(producto.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Stock insuficiente para Producto de prueba. Disponible: 5."));

        org.junit.jupiter.api.Assertions.assertEquals(5, productoRepository.findById(producto.getId()).orElseThrow().getStock());
        org.junit.jupiter.api.Assertions.assertEquals(0, ventaRepository.count());
    }

    @Test
    void solicitudInvalidaNoRegistraLaVenta() throws Exception {
        mockMvc.perform(post("/api/v1/ventas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productoId":%d,"cantidad":0}]}
                                """.formatted(producto.getId())))
                .andExpect(status().isBadRequest());

        org.junit.jupiter.api.Assertions.assertEquals(0, ventaRepository.count());
    }

    @Test
    void descuentoMayorAlSubtotalNoRegistraLaVenta() throws Exception {
        String authorization = autorizarDescuento("100.01");
        mockMvc.perform(post("/api/v1/ventas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items":[{"productoId":%d,"cantidad":1}],
                                  "descuento":100.01,
                                  "descuentoAuthorizationToken":"%s"
                                }
                                """.formatted(producto.getId(), authorization)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El descuento no puede superar el subtotal."));

        org.junit.jupiter.api.Assertions.assertEquals(5, productoRepository.findById(producto.getId()).orElseThrow().getStock());
        org.junit.jupiter.api.Assertions.assertEquals(0, ventaRepository.count());
    }

    @Test
    void autorizacionManualRequiereCredencialesActivasDeAdministrador() throws Exception {
        mockMvc.perform(post("/api/v1/auth/discount-authorization")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"incorrecta","amount":5.00}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/discount-authorization")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"cajero","password":"AdminPass123!","amount":5.00}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/discount-authorization")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"AdminPass123!","amount":5.00}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.amount").value(5.0));
    }

    @Test
    void descuentoManualSinAutorizacionNoPuedeAplicarse() throws Exception {
        mockMvc.perform(post("/api/v1/ventas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productoId":%d,"cantidad":1}],"descuento":5.00}
                                """.formatted(producto.getId())))
                .andExpect(status().isForbidden());
        org.junit.jupiter.api.Assertions.assertEquals(0, ventaRepository.count());
    }

    @Test
    void promocionAutomaticaSeCalculaEnServidorYSeGuardaEnVentaYLinea() throws Exception {
        Promotion promotion = new Promotion("Producto 10%", PromotionType.PRODUCT_PERCENT);
        promotion.setProductId(producto.getId());
        promotion.setDiscountPercent(new BigDecimal("10.00"));
        promotion.setActive(true);
        promotionRepository.saveAndFlush(promotion);

        mockMvc.perform(post("/api/v1/promotions/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productoId":%d,"cantidad":1}]}
                                """.formatted(producto.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discount").value(8.85))
                .andExpect(jsonPath("$.promotionName").value("Producto 10%"));

        mockMvc.perform(post("/api/v1/ventas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productoId":%d,"cantidad":1}]}
                                """.formatted(producto.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descuento").value(8.85))
                .andExpect(jsonPath("$.descuentoAutomatico").value(8.85))
                .andExpect(jsonPath("$.descuentoManual").value(0))
                .andExpect(jsonPath("$.promocionAplicada").value("Producto 10%"))
                .andExpect(jsonPath("$.total").value(90.0))
                .andExpect(jsonPath("$.detalles[0].descuentoAutomatico").value(8.85));
    }

    @Test
    void autorizacionEsDeUnSoloUsoYDescuentoCombinadoQuedaAuditado() throws Exception {
        Promotion promotion = new Promotion("Producto 10%", PromotionType.PRODUCT_PERCENT);
        promotion.setProductId(producto.getId());
        promotion.setDiscountPercent(new BigDecimal("10.00"));
        promotion.setActive(true);
        promotionRepository.saveAndFlush(promotion);
        String authorization = autorizarDescuento("5.00");
        String payload = """
                {"items":[{"productoId":%d,"cantidad":1}],"descuento":5.00,
                "descuentoAuthorizationToken":"%s"}
                """.formatted(producto.getId(), authorization);

        mockMvc.perform(post("/api/v1/ventas").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descuento").value(13.85))
                .andExpect(jsonPath("$.descuentoManual").value(5.0))
                .andExpect(jsonPath("$.descuentoAutomatico").value(8.85))
                .andExpect(jsonPath("$.descuentoAprobadoPorNombre").value("Administrador General"))
                .andExpect(jsonPath("$.total").value(84.35));

        mockMvc.perform(post("/api/v1/ventas").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden());
        org.junit.jupiter.api.Assertions.assertEquals(1, ventaRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(4, productoRepository.findById(producto.getId()).orElseThrow().getStock());
    }

    @Test
    void descuentoDeLineaRequiereAutorizacionYSePersisteEnDetalle() throws Exception {
        String authorization = autorizarDescuento("10.00");
        mockMvc.perform(post("/api/v1/ventas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productoId":%d,"cantidad":1,"descuentoManual":10.00}],
                                "descuentoAuthorizationToken":"%s"}
                                """.formatted(producto.getId(), authorization)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descuentoManual").value(10.0))
                .andExpect(jsonPath("$.descuento").value(10.0))
                .andExpect(jsonPath("$.detalles[0].descuentoManual").value(10.0));
    }

    @Test
    void promocionesAdministrativasRequierenRolAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/promotions"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/promotions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Oferta","type":"SPEND_TIER","active":true,"priority":0,
                                "discountPercent":10,"minimumSpend":50}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void promocionesAdmitenCrudAdministrativoYValidanReglas() throws Exception {
        String response = mockMvc.perform(post("/api/v1/promotions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"2x1","type":"BUY_X_GET_Y","active":true,"priority":1,
                                "productId":%d,"buyQuantity":1,"freeQuantity":1}
                                """.formatted(producto.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("BUY_X_GET_Y"))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(put("/api/v1/promotions/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"2x1 pausada","type":"BUY_X_GET_Y","active":false,"priority":2,
                                "productId":%d,"buyQuantity":1,"freeQuantity":1}
                                """.formatted(producto.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.priority").value(2));

        mockMvc.perform(post("/api/v1/promotions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Sin producto","type":"PRODUCT_PERCENT","active":true,"priority":0,
                                "discountPercent":10}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/v1/promotions/{id}", id))
                .andExpect(status().isNoContent());
    }

    @Test
    void promocionAxBRegalaUnidadesMismaReferenciaAlCrearLaVenta() throws Exception {
        Promotion promotion = new Promotion("2x1", PromotionType.BUY_X_GET_Y);
        promotion.setProductId(producto.getId());
        promotion.setBuyQuantity(1);
        promotion.setFreeQuantity(1);
        promotion.setActive(true);
        promotionRepository.saveAndFlush(promotion);

        mockMvc.perform(post("/api/v1/ventas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productoId":%d,"cantidad":3}]}
                                """.formatted(producto.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descuentoAutomatico").value(88.5))
                .andExpect(jsonPath("$.promocionAplicada").value("2x1"))
                .andExpect(jsonPath("$.total").value(200.0))
                .andExpect(jsonPath("$.detalles[0].descuentoAutomatico").value(88.5));
    }

    private String autorizarDescuento(String amount) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/discount-authorization")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"AdminPass123!","amount":%s}
                                """.formatted(amount)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    @Test
    void apiListaProductosPorCategoriaYExponeElHistorial() throws Exception {
        mockMvc.perform(get("/api/v1/pos/productos").param("categoria", "Café"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombre").value("Producto de prueba"));

        mockMvc.perform(get("/api/v1/pos/productos").param("busqueda", "prueba"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get("/api/v1/ventas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        mockMvc.perform(get("/api/v1/ventas/tasa-iva"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasa").value(0.13));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void productosPuedenCrearseYActualizarse() throws Exception {
        String creado = mockMvc.perform(post("/api/v1/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Té","precio":35.00,"categoria":"  Bebidas  ","favorito":true,"stock":12,"activo":true,"precioIncluyeIva":false}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.stock").value(12))
                .andExpect(jsonPath("$.precioIncluyeIva").value(false))
                .andExpect(jsonPath("$.categoria").value("Bebidas"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long id = objectMapper.readTree(creado).get("id").asLong();

        mockMvc.perform(put("/api/v1/productos/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Té verde","precio":40.00,"categoria":"Bebidas","favorito":false,"stock":8,"activo":true,"precioIncluyeIva":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Té verde"))
                .andExpect(jsonPath("$.stock").value(8))
                .andExpect(jsonPath("$.precioIncluyeIva").value(true))
                .andExpect(jsonPath("$.activo").value(true));

        mockMvc.perform(get("/api/v1/productos/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Té verde"));
        mockMvc.perform(get("/api/v1/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void usuarioNoPuedeGestionarProductosNiVerCategoriasDeAdministracion() throws Exception {
        mockMvc.perform(post("/api/v1/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"No permitido","precio":1,"categoria":"Café","stock":1}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/productos/{id}", producto.getId()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/categorias"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void nombresDuplicadosSeRechazanYBajasSeConservanComoInactivas() throws Exception {
        mockMvc.perform(post("/api/v1/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"  producto DE prueba ","precio":1,"categoria":"Café","stock":1}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/ventas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productoId":%d,"cantidad":1}]}
                                """.formatted(producto.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/productos/{id}", producto.getId()))
                .andExpect(status().isNoContent());

        org.junit.jupiter.api.Assertions.assertTrue(productoRepository.existsById(producto.getId()));
        org.junit.jupiter.api.Assertions.assertEquals(1, ventaRepository.count());
        org.junit.jupiter.api.Assertions.assertFalse(
                productoRepository.findById(producto.getId()).orElseThrow().getActivo());
        mockMvc.perform(get("/api/v1/pos/productos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/v1/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/v1/productos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].activo").value(false));
        mockMvc.perform(get("/api/v1/ventas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].detalles[0].nombreProducto").value("Producto de prueba"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void productoRechazaPrecioYExistenciasNegativasPeroPermiteCero() throws Exception {
        mockMvc.perform(post("/api/v1/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Inválido","precio":-1,"categoria":"Café","stock":-1}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Producto gratuito","precio":0,"categoria":"Café","stock":0}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.precio").value(0))
                .andExpect(jsonPath("$.stock").value(0));
    }

    @Test
    void cajeroNoPuedePedirProductosInactivos() throws Exception {
        mockMvc.perform(get("/api/v1/productos"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/pos/productos"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void ajustesDeInventarioActualizanStockYRegistranKardex() throws Exception {
        mockMvc.perform(post("/api/v1/inventario/ajuste")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productoId":%d,"cantidad":3,"tipo":"ENTRADA","tipoOperacion":"AI","motivo":"  Compra a proveedor  "}
                                """.formatted(producto.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folioMovimiento").value(matchesPattern("S01C01AI\\d{6}")))
                .andExpect(jsonPath("$.motivo").value("Compra a proveedor"))
                .andExpect(jsonPath("$.stockAnterior").value(5))
                .andExpect(jsonPath("$.stockNuevo").value(8))
                .andExpect(jsonPath("$.usuario").value("admin"));

        mockMvc.perform(post("/api/v1/inventario/ajuste")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productoId":%d,"cantidad":2,"tipo":"SALIDA","tipoOperacion":"AI","motivo":"Merma"}
                                """.formatted(producto.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockAnterior").value(8))
                .andExpect(jsonPath("$.stockNuevo").value(6));

        org.junit.jupiter.api.Assertions.assertEquals(6, productoRepository.findById(producto.getId()).orElseThrow().getStock());
        mockMvc.perform(get("/api/v1/inventario/movimientos")
                        .param("productoId", producto.getId().toString())
                        .param("tipo", "ENTRADA")
                        .param("tipoOperacion", "AI"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].tipo").value("ENTRADA"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void salidaMayorAlStockNoCambiaExistenciasNiRegistraMovimiento() throws Exception {
        mockMvc.perform(post("/api/v1/inventario/ajuste")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productoId":%d,"cantidad":6,"tipo":"SALIDA","tipoOperacion":"AI","motivo":"Merma"}
                                """.formatted(producto.getId())))
                .andExpect(status().isConflict());
        org.junit.jupiter.api.Assertions.assertEquals(5, productoRepository.findById(producto.getId()).orElseThrow().getStock());
        org.junit.jupiter.api.Assertions.assertEquals(0, movimientoInventarioRepository.count());
    }

    @Test
    @WithMockUser(username = "cajero", roles = "USER")
    void inventarioSoloEsAccesibleParaAdministradores() throws Exception {
        mockMvc.perform(get("/api/v1/inventario/movimientos"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/inventario/ajuste")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productoId":%d,"cantidad":1,"tipo":"ENTRADA","tipoOperacion":"AI","motivo":"Compra"}
                                """.formatted(producto.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void ventasSePaginanFiltranSeDetallanYAdmitenDevolucionParcialYCompleta() throws Exception {
        String respuestaVenta = mockMvc.perform(post("/api/v1/ventas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productoId":%d,"cantidad":3}]}
                                """.formatted(producto.getId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long ventaId = objectMapper.readTree(respuestaVenta).get("id").asLong();

        mockMvc.perform(get("/api/v1/ventas")
                        .param("folioInterno", "S01C01CA")
                        .param("usuarioId", usuarioRepository.findByUsername("admin").orElseThrow().getId().toString())
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/v1/ventas/{id}", ventaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cajeroNombre").value("Administrador General"))
                .andExpect(jsonPath("$.detalles[0].cantidadDevuelta").value(0));

        mockMvc.perform(post("/api/v1/ventas/{id}/devolucion", ventaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productoId":%d,"cantidad":1}],"motivo":"Producto defectuoso"}
                                """.formatted(producto.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.folioDevolucion").value(matchesPattern("S01C01DE\\d{6}")))
                .andExpect(jsonPath("$.importe").value(100.0))
                .andExpect(jsonPath("$.motivo").value("Producto defectuoso"));

        org.junit.jupiter.api.Assertions.assertEquals(3, productoRepository.findById(producto.getId()).orElseThrow().getStock());
        mockMvc.perform(get("/api/v1/ventas/{id}", ventaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DEVOLUCION_PARCIAL"))
                .andExpect(jsonPath("$.detalles[0].cantidadDevuelta").value(1))
                .andExpect(jsonPath("$.devoluciones", hasSize(1)))
                .andExpect(jsonPath("$.devoluciones[0].motivo").value("Producto defectuoso"));

        mockMvc.perform(post("/api/v1/ventas/{id}/devolucion", ventaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Cambio de opinión\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importe").value(200.0));

        org.junit.jupiter.api.Assertions.assertEquals(5, productoRepository.findById(producto.getId()).orElseThrow().getStock());
        mockMvc.perform(get("/api/v1/ventas/{id}", ventaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DEVUELTA"))
                .andExpect(jsonPath("$.detalles[0].cantidadDevuelta").value(3))
                .andExpect(jsonPath("$.devoluciones", hasSize(2)));
        mockMvc.perform(get("/api/v1/inventario/movimientos")
                        .param("tipoOperacion", "DE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].motivo")
                        .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("Cambio de opinión"))))
                .andExpect(jsonPath("$[*].usuario").value(org.hamcrest.Matchers.hasItem("admin")));

        mockMvc.perform(post("/api/v1/ventas/{id}/devolucion", ventaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Intento de devolución repetido\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/ventas/{id}/devolucion", ventaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "cajero", roles = "USER")
    void cajeroPuedeProcesarDevolucionesYRestaurarExistencias() throws Exception {
        String respuestaVenta = mockMvc.perform(post("/api/v1/ventas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productoId":%d,"cantidad":2}]}
                                """.formatted(producto.getId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long ventaId = objectMapper.readTree(respuestaVenta).get("id").asLong();

        mockMvc.perform(post("/api/v1/ventas/{id}/devolucion", ventaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productoId":%d,"cantidad":1}],"motivo":"Producto incorrecto"}
                                """.formatted(producto.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuario").value("Cajero Turno 1"))
                .andExpect(jsonPath("$.motivo").value("Producto incorrecto"));
        org.junit.jupiter.api.Assertions.assertEquals(
                4, productoRepository.findById(producto.getId()).orElseThrow().getStock());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void dashboardAgregaVentasProductosCategoriasYMetodoPago() throws Exception {
        String respuestaVenta = mockMvc.perform(post("/api/v1/ventas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items":[{"productoId":%d,"cantidad":2}],
                                  "metodoPago":"Tarjeta"
                                }
                                """.formatted(producto.getId())))
                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.metodoPago").value("Tarjeta"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long ventaId = objectMapper.readTree(respuestaVenta).get("id").asLong();

        mockMvc.perform(post("/api/v1/ventas/{id}/devolucion", ventaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productoId":%d,"cantidad":1}],"motivo":"Cambio de opinión"}
                                """.formatted(producto.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/reportes/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVentas").value(200.0))
                .andExpect(jsonPath("$.totalTransacciones").value(1))
                .andExpect(jsonPath("$.ticketMedio").value(200.0))
                .andExpect(jsonPath("$.totalDevoluciones").value(100.0))
                .andExpect(jsonPath("$.ventasPorEmpleado[0].salesCount").value(1))
                .andExpect(jsonPath("$.topProductos[0].productName").value("Producto de prueba"))
                .andExpect(jsonPath("$.topProductos[0].unitsSold").value(1))
                .andExpect(jsonPath("$.ventasPorCategoria[0].categoryName").value("Café"))
                .andExpect(jsonPath("$.ventasPorMetodoPago[0].paymentMethod").value("Tarjeta"));
    }

    @Test
    @WithMockUser(username = "cajero", roles = "USER")
    void dashboardSoloEsAccesibleParaAdministradores() throws Exception {
        mockMvc.perform(get("/api/v1/reportes/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    void foliosSonCompactosYCorrelativosPorOperacion() {
        String primero = folioInternoService.generar(com.vento.pos.entity.TipoOperacion.AI);
        String segundo = folioInternoService.generar(com.vento.pos.entity.TipoOperacion.AI);
        org.junit.jupiter.api.Assertions.assertTrue(primero.matches("S01C01AI\\d{6}"));
        org.junit.jupiter.api.Assertions.assertEquals(
                Long.parseLong(primero.substring("S01C01AI".length())) + 1,
                Long.parseLong(segundo.substring("S01C01AI".length())));
        org.junit.jupiter.api.Assertions.assertFalse(primero.contains("-"));
    }

    @Test
    void cobrarAceptaHastaTresPagosParciales() throws Exception {
        mockMvc.perform(post("/api/v1/pos/cobrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "metodoPago": "Mixto",
                                  "items": [{"productoId": %d, "cantidad": 1}],
                                  "pagosParciales": [
                                    {"metodoPago": "Efectivo", "monto": 50.00},
                                    {"metodoPago": "Cuscatlan", "monto": 63.00, "ultimos4Digitos": "1234", "codigoAutorizacion": "AUTH-1"}
                                  ]
                                }
                                """.formatted(producto.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metodoPago").value("Mixto"));
        org.junit.jupiter.api.Assertions.assertEquals(4,
                productoRepository.findById(producto.getId()).orElseThrow().getStock());
    }
}
