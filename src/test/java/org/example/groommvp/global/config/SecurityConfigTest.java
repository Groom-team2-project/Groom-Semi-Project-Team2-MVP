package org.example.groommvp.global.config;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.example.groommvp.domain.auth.dto.JwtClaims;
import org.example.groommvp.domain.auth.service.JwtTokenProvider;
import org.example.groommvp.domain.member.entity.AuthProvider;
import org.example.groommvp.domain.member.entity.MemberRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class SecurityConfigTest {

    private MockMvc mockMvc;
    private JwtTokenProvider jwtTokenProvider;
    private AnnotationConfigWebApplicationContext context;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestApplication.class);
        context.refresh();

        jwtTokenProvider = context.getBean(JwtTokenProvider.class);
        when(jwtTokenProvider.getValidClaims("user-token"))
                .thenReturn(claims(MemberRole.USER));
        when(jwtTokenProvider.getValidClaims("admin-token"))
                .thenReturn(claims(MemberRole.ADMIN));

        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(FilterChainProxy.class))
                .build();
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void publicReadApiDoesNotRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(content().string("products"));
    }

    @Test
    void unknownApiRequiresAuthenticationByDefault() throws Exception {
        mockMvc.perform(get("/api/v1/internal-probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void authenticatedUserCanCallDefaultProtectedApi() throws Exception {
        mockMvc.perform(get("/api/v1/internal-probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("internal"));
    }

    @Test
    void adminApiRejectsUserToken() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void adminApiAcceptsAdminToken() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("admin-products"));
    }

    private JwtClaims claims(MemberRole role) {
        Instant now = Instant.now();
        return new JwtClaims(1L, role, AuthProvider.KAKAO, now, now.plusSeconds(3600));
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfig.class)
    static class TestApplication {

        @Bean
        JwtTokenProvider jwtTokenProvider() {
            return Mockito.mock(JwtTokenProvider.class);
        }

        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }
    }

    @RestController
    static class ProbeController {

        @GetMapping("/api/v1/products")
        String products() {
            return "products";
        }

        @PostMapping("/api/v1/products")
        String createProduct() {
            return "admin-products";
        }

        @GetMapping("/api/v1/internal-probe")
        String internal() {
            return "internal";
        }
    }
}
