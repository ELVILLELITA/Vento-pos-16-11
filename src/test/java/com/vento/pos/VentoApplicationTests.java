package com.vento.pos;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:ventowebtest;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class VentoApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void contextLoads() {
    }

    @Test
    @WithMockUser
    void pointOfSalePageAndAssetsLoadForAuthenticatedUsers() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));

        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nueva venta")));

        mockMvc.perform(get("/styles.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(".product-grid")));

        mockMvc.perform(get("/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("function renderCart()")));
    }

    @Test
    void demoUsersCanLoginWithAnEmptyPassword() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("http://localhost/login"));

        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"username\"")))
                .andExpect(content().string(containsString("name=\"password\" type=\"password\" autocomplete=\"current-password\"")));

        for (String username : new String[]{"cajero", "admin"}) {
            MockHttpSession session = iniciarSesion(username);
            assertNotNull(session);
            mockMvc.perform(get("/").session(session))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("/index.html"));
        }
    }

    @Test
    void logoutClearsTheSessionAndRedirectsToLogin() throws Exception {
        MockHttpSession session = iniciarSesion("cajero");
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn();
        var csrf = objectMapper.readTree(csrfResult.getResponse().getContentAsString());

        mockMvc.perform(post("/logout")
                        .session(session)
                        .header(csrf.get("headerName").asText(), csrf.get("token").asText()))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/login?logout"));

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("http://localhost/login"));
    }

    @Test
    void salesApiIsTemporarilyPublicButProfileRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/pos/productos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(9)));

        mockMvc.perform(get("/api/v1/productos"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("http://localhost/login"));

        mockMvc.perform(get("/api/v1/auth/me").session(iniciarSesion("cajero")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cajero"))
                .andExpect(jsonPath("$.nombreCompleto").value("Cajero Turno 1"))
                .andExpect(jsonPath("$.rol").value("ROLE_USER"))
                .andExpect(jsonPath("$.allowedModules", hasSize(4)))
                .andExpect(jsonPath("$.allowedModules").value(org.hamcrest.Matchers.hasItem("clientes")))
                .andExpect(jsonPath("$.allowedModules").value(org.hamcrest.Matchers.hasItem("configuracion")));

        mockMvc.perform(get("/api/v1/auth/me").session(iniciarSesion("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.allowedModules", hasSize(7)))
                .andExpect(jsonPath("$.allowedModules").value(org.hamcrest.Matchers.hasItem("clientes")))
                .andExpect(jsonPath("$.allowedModules").value(org.hamcrest.Matchers.hasItem("inventario")))
                .andExpect(jsonPath("$.allowedModules").value(org.hamcrest.Matchers.hasItem("compras")))
                .andExpect(jsonPath("$.allowedModules").value(org.hamcrest.Matchers.hasItem("configuracion")))
                .andExpect(jsonPath("$.allowedModules").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem("productos"))));
    }

    @Test
    @WithMockUser
    void catalogIsSeededFromTheDatabase() throws Exception {
        mockMvc.perform(get("/api/v1/pos/productos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(9)))
                .andExpect(jsonPath("$[0].nombre").value("Espresso"));
    }

    private MockHttpSession iniciarSesion(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .param("username", username)
                        .param("password", "")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
