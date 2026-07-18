package org.example.groommvp.domain.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

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
        assertThat(authentication.getPrincipal()).isEqualTo(1L);
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
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
