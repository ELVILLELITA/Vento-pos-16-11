package com.vento.pos;

import com.vento.pos.entity.MovimientoInventario;
import com.vento.pos.entity.Producto;
import com.vento.pos.entity.Proveedor;
import com.vento.pos.repository.CompraRepository;
import com.vento.pos.repository.MovimientoInventarioRepository;
import com.vento.pos.repository.ProductoRepository;
import com.vento.pos.repository.ProveedorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:comprasintegrationtest;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@WithMockUser(username = "admin", roles = "ADMIN")
class ComprasIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private ProveedorRepository proveedorRepository;
    @Autowired private CompraRepository compraRepository;
    @Autowired private MovimientoInventarioRepository movimientoRepository;

    private Producto producto;
    private Proveedor proveedor;

    @BeforeEach
    void limpiarDatos() {
        movimientoRepository.deleteAll();
        compraRepository.deleteAll();
        proveedorRepository.deleteAll();
        productoRepository.deleteAll();
        producto = productoRepository.save(new Producto(
                "Producto de compra", new BigDecimal("12.00"), "Abarrotes", false, 4));
        proveedor = proveedorRepository.save(new Proveedor("Proveedor de prueba"));
    }

    @Test
    void registraCompraIncrementaExistenciasYGeneraMovimientoKardex() throws Exception {
        mockMvc.perform(post("/api/v1/compras")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "proveedorId": %d,
                                  "numeroFacturaProveedor": "F-123",
                                  "items": [
                                    {"productoId": %d, "cantidad": 3, "costoUnitario": 8.50}
                                  ]
                                }
                                """.formatted(proveedor.getId(), producto.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.folioInterno").value(matchesPattern("S01C01CP\\d{6}")))
                .andExpect(jsonPath("$.numeroFacturaProveedor").value("F-123"))
                .andExpect(jsonPath("$.total").value(25.50))
                .andExpect(jsonPath("$.detalles[0].subtotal").value(25.50));

        assertEquals(7, productoRepository.findById(producto.getId()).orElseThrow().getStock());
        assertEquals(1, compraRepository.count());
        MovimientoInventario movimiento = movimientoRepository.findAll().get(0);
        assertEquals("ENTRADA", movimiento.getTipo().name());
        assertEquals("CP", movimiento.getTipoOperacion().name());
        assertEquals(4, movimiento.getStockAnterior());
        assertEquals(7, movimiento.getStockNuevo());
        assertEquals("F-123", compraRepository.findAll().get(0).getNumeroFacturaProveedor());
    }

    @Test
    void permiteGestionarProveedoresSoloAlAdministrador() throws Exception {
        mockMvc.perform(post("/api/v1/proveedores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombreComercial":"Distribuidora Central","nit":"0614-123456-101-0","email":"ventas@example.com"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombreComercial").value("Distribuidora Central"))
                .andExpect(jsonPath("$.activo").value(true));

        mockMvc.perform(get("/api/v1/proveedores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombreComercial").value("Distribuidora Central"));

        mockMvc.perform(get("/api/v1/proveedores").with(user("cajero").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void actualizaYDeshabilitaProveedorSinBorrarlo() throws Exception {
        mockMvc.perform(put("/api/v1/proveedores/{id}", proveedor.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombreComercial":"Proveedor actualizado","razonSocial":"Razón social S.A.","telefono":"2222-0000"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreComercial").value("Proveedor actualizado"))
                .andExpect(jsonPath("$.razonSocial").value("Razón social S.A."));

        mockMvc.perform(delete("/api/v1/proveedores/{id}", proveedor.getId()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/proveedores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/v1/proveedores").param("incluirInactivos", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].activo").value(false));
    }

    @Test
    void rechazaCompraSinLineas() throws Exception {
        mockMvc.perform(post("/api/v1/compras")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"proveedorId": %d, "items": []}
                                """.formatted(proveedor.getId())))
                .andExpect(status().isBadRequest());
        assertEquals(0, compraRepository.count());
    }

    @Test
    void creaProductoConCodigoDeBarrasParaElCatalogo() throws Exception {
        mockMvc.perform(post("/api/v1/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre":"Producto escaneable",
                                  "categoria":"Abarrotes",
                                  "precio":4.50,
                                  "stock":0,
                                  "precioIncluyeIva":false,
                                  "codigoBarras":"7501234567890"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigoBarras").value("7501234567890"))
                .andExpect(jsonPath("$.precioIncluyeIva").value(false));

        mockMvc.perform(get("/api/v1/productos").param("busqueda", "7501234567890"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigoBarras").value("7501234567890"));
    }
}
