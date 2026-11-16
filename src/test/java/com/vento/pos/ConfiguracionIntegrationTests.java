package com.vento.pos;

import com.vento.pos.repository.UsuarioRepository;
import com.vento.pos.repository.ConfiguracionSistemaRepository;
import com.vento.pos.service.FolioInternoService;
import com.vento.pos.entity.TipoOperacion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:ventoconfigurationtest;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class ConfiguracionIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ConfiguracionSistemaRepository configuracionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private FolioInternoService folioInternoService;

    @Test
    @WithMockUser(username = "cajero", roles = "USER")
    void usuarioAutenticadoPuedeConsultarConfiguracionPeroNoModificarla() throws Exception {
        mockMvc.perform(get("/api/v1/configuracion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreEstablecimiento").value("Café Vento"))
                .andExpect(jsonPath("$.porcentajeIva").value(13.0));

        mockMvc.perform(put("/api/v1/configuracion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombreEstablecimiento":"Otra tienda",
                                  "codigoSucursal":"S01",
                                  "codigoEstacion":"C01",
                                  "porcentajeIva":13,
                                  "monedaSimbolo":"$",
                                  "piePaginaTicket":"Gracias"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void administradorActualizaConfiguracionYFoliosUsanSusCodigos() throws Exception {
        mockMvc.perform(put("/api/v1/configuracion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombreEstablecimiento":"  Vento Centro  ",
                                  "direccion":"  Calle 1  ",
                                  "telefono":"5551234",
                                  "codigoSucursal":"S02",
                                  "codigoEstacion":"C03",
                                  "porcentajeIva":16.00,
                                  "monedaSimbolo":"MX$",
                                  "piePaginaTicket":"  Vuelva pronto  "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreEstablecimiento").value("Vento Centro"))
                .andExpect(jsonPath("$.direccion").value("Calle 1"))
                .andExpect(jsonPath("$.porcentajeIva").value(16.0))
                .andExpect(jsonPath("$.piePaginaTicket").value("Vuelva pronto"));

        assertTrue(folioInternoService.generar(TipoOperacion.AI).matches("S02C03AI\\d{6}"));

        mockMvc.perform(put("/api/v1/configuracion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombreEstablecimiento":"Café Vento",
                                  "codigoSucursal":"S01",
                                  "codigoEstacion":"C01",
                                  "porcentajeIva":13.00,
                                  "monedaSimbolo":"$",
                                  "piePaginaTicket":"¡Gracias por su compra!"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void administradorGuardaDteEnmascaraSecretosYConservaCredencialesEnBlanco() throws Exception {
        mockMvc.perform(put("/api/v1/configuracion/dte")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ambienteDte":"01",
                                  "nitEmisor":"0614-123456-102-3",
                                  "nrcEmisor":"123456-7",
                                  "codigoActividadEmisor":"56101",
                                  "clavePrivadaCertificado":"clave-super-secreta",
                                  "passwordCertificado":"password-super-secreto",
                                  "urlFirmador":"http://localhost:8080",
                                  "codigoPuntoVentaMh":"PV001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clavePrivadaCertificado").value("••••••••"))
                .andExpect(jsonPath("$.passwordCertificado").value("••••••••"))
                .andExpect(jsonPath("$.clavePrivadaCertificadoConfigurada").value(true))
                .andExpect(jsonPath("$.passwordCertificadoConfigurado").value(true));

        String respuesta = mockMvc.perform(get("/api/v1/configuracion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ambienteDte").value("01"))
                .andExpect(jsonPath("$.nitEmisor").value("0614-123456-102-3"))
                .andExpect(jsonPath("$.codigoPuntoVentaMh").value("PV001"))
                .andExpect(jsonPath("$.clavePrivadaCertificado").value("••••••••"))
                .andExpect(jsonPath("$.passwordCertificado").value("••••••••"))
                .andReturn().getResponse().getContentAsString();
        assertTrue(!respuesta.contains("clave-super-secreta"));
        assertTrue(!respuesta.contains("password-super-secreto"));

        mockMvc.perform(put("/api/v1/configuracion/dte")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ambienteDte":"00",
                                  "clavePrivadaCertificado":"",
                                  "passwordCertificado":""
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clavePrivadaCertificadoConfigurada").value(true))
                .andExpect(jsonPath("$.passwordCertificadoConfigurado").value(true));

        var stored = configuracionRepository.findById(1L).orElseThrow();
        assertTrue(stored.getClavePrivadaCertificado().equals("clave-super-secreta"));
        assertTrue(stored.getPasswordCertificado().equals("password-super-secreto"));
    }

    @Test
    @WithMockUser(username = "cajero", roles = "USER")
    void cajeroNoPuedeModificarConfiguracionDte() throws Exception {
        mockMvc.perform(put("/api/v1/configuracion/dte")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ambienteDte\":\"01\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/dte/configuracion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ambienteDte\":\"01\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void rutaDteEnmascaraLasCredencialesConfiguradas() throws Exception {
        mockMvc.perform(put("/api/v1/dte/configuracion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clavePrivadaCertificado":"clave-prueba","passwordCertificado":"password-prueba"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clavePrivadaCertificadoConfigurada").value(true))
                .andExpect(jsonPath("$.passwordCertificadoConfigurado").value(true));

        mockMvc.perform(get("/api/v1/dte/configuracion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clavePrivadaCertificado").value("••••••••"))
                .andExpect(jsonPath("$.passwordCertificado").value("••••••••"));
    }

    @Test
    @WithMockUser(username = "cajero", roles = "USER")
    void cajeroPuedeCambiarSuPasswordYAdministradorPuedeCrearUsuarios() throws Exception {
        mockMvc.perform(put("/api/v1/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"passwordActual":"","nuevaPassword":"NuevaClave123"}
                                """))
                .andExpect(status().isNoContent());
        assertTrue(passwordEncoder.matches(
                "NuevaClave123", usuarioRepository.findByUsername("cajero").orElseThrow().getPassword()));

        mockMvc.perform(post("/api/v1/usuarios")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"cajero.nuevo",
                                  "password":"ClaveNueva123",
                                  "nombreCompleto":"Cajero Nuevo",
                                  "rol":"ROLE_USER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("ROLE_USER"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void administradorPuedeEditarYDeshabilitarUsuariosPeroNoSuPropiaCuenta() throws Exception {
        mockMvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"cuenta.edicion",
                                  "password":"ClaveNueva123",
                                  "nombreCompleto":"Cuenta Inicial",
                                  "rol":"ROLE_USER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("cuenta.edicion"));
        long id = usuarioRepository.findByUsername("cuenta.edicion").orElseThrow().getId();
        long adminId = usuarioRepository.findByUsername("admin").orElseThrow().getId();

        mockMvc.perform(put("/api/v1/usuarios/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"cuenta.actualizada",
                                  "nombreCompleto":"Cuenta Actualizada",
                                  "rol":"ROLE_ADMIN"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cuenta.actualizada"))
                .andExpect(jsonPath("$.nombreCompleto").value("Cuenta Actualizada"))
                .andExpect(jsonPath("$.rol").value("ROLE_ADMIN"));

        mockMvc.perform(put("/api/v1/usuarios/{id}/estado", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        mockMvc.perform(put("/api/v1/usuarios/{id}/estado", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true));

        mockMvc.perform(put("/api/v1/usuarios/{id}/estado", adminId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/v1/usuarios/{id}", adminId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"admin.modificado",
                                  "nombreCompleto":"Administrador",
                                  "rol":"ROLE_USER"
                                }
                                """))
                .andExpect(status().isConflict());
    }
}
