package org.example.groommvp.domain.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.example.groommvp.domain.auth.dto.JwtClaims;
import org.example.groommvp.domain.auth.service.JwtTokenProvider;
import org.example.groommvp.domain.member.entity.AuthProvider;
import org.example.groommvp.domain.member.entity.MemberRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

    private static final String VALID_TOKEN = "valid-token";

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private AutoCloseable closeable;
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtTokenProvider);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        closeable.close();
    }

    @Test
    void doFilterPassesThroughWhenAuthorizationHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_OK);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtTokenProvider, never()).getValidClaims(VALID_TOKEN);
    }

    @Test
    void doFilterPassesThroughWhenAuthorizationHeaderIsNotBearerToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_OK);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtTokenProvider, never()).getValidClaims(VALID_TOKEN);
    }

    @Test
    void doFilterSetsAuthenticationWhenBearerTokenIsValid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();
        JwtClaims claims = new JwtClaims(
                1L,
                MemberRole.USER,
                AuthProvider.KAKAO,
                Instant.now(),
                Instant.now().plusSeconds(7200)
        );
        when(jwtTokenProvider.getValidClaims(VALID_TOKEN)).thenReturn(claims);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_OK);
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isInstanceOf(AuthMember.class);
        AuthMember authMember = (AuthMember) authentication.getPrincipal();
        assertThat(authMember.memberId()).isEqualTo(1L);
        assertThat(authMember.role()).isEqualTo(MemberRole.USER);
        assertThat(authMember.provider()).isEqualTo(AuthProvider.KAKAO);
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");
    }

    @Test
    void doFilterReturnsUnauthorizedWhenBearerTokenIsInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();
        when(jwtTokenProvider.getValidClaims(VALID_TOKEN))
                .thenThrow(new IllegalArgumentException("Invalid token."));

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getContentAsString()).contains("\"success\":false");
        assertThat(response.getContentAsString()).contains("\"errorCode\":\"INVALID_TOKEN\"");
        assertThat(response.getContentAsString()).contains("\"message\":\"유효하지 않은 토큰입니다.\"");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterDoesNotConvertDownstreamExceptionWhenBearerTokenIsValid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = (servletRequest, servletResponse) -> {
            throw new ServletException("Downstream exception.");
        };
        JwtClaims claims = new JwtClaims(
                1L,
                MemberRole.USER,
                AuthProvider.KAKAO,
                Instant.now(),
                Instant.now().plusSeconds(7200)
        );
        when(jwtTokenProvider.getValidClaims(VALID_TOKEN)).thenReturn(claims);

        assertThatThrownBy(() -> jwtAuthenticationFilter.doFilter(request, response, filterChain))
                .isInstanceOf(ServletException.class)
                .hasMessage("Downstream exception.");

        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_OK);
        assertThat(response.getContentAsString()).isBlank();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }
}
