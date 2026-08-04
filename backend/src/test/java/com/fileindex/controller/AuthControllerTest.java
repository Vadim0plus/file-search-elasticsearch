package com.fileindex.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fileindex.config.AuthProperties;
import com.fileindex.config.SecurityConfig;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Loads the real SecurityConfig (not just @WithMockUser) so the custom login/logout/me
 * behavior - JSON-friendly status codes instead of redirects, session-based auth - is
 * actually exercised, matching what the frontend depends on.
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(AuthProperties.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithWrongCredentialsFails() throws Exception {
        mockMvc.perform(post("/api/auth/login").param("username", "admin").param("password", "wrong"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void loginThenMeReturnsAuthenticatedUsername() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .param("username", "admin")
                .param("password", "admin"))
            .andExpect(status().isOk())
            .andReturn();

        HttpSession session = loginResult.getRequest().getSession(false);

        mockMvc.perform(get("/api/auth/me").session((MockHttpSession) session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("admin"));
    }

    @Test
    void logoutSucceeds() throws Exception {
        mockMvc.perform(post("/api/auth/logout")).andExpect(status().isOk());
    }
}
