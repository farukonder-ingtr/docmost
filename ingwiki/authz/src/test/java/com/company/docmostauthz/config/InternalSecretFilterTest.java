package com.company.docmostauthz.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalSecretFilterTest {

    private static final String HEADER = "X-Docmost-Internal-Secret";
    private static final String SECRET = "super-secret-value";

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private InternalSecretFilter filter;

    @BeforeEach
    void setUp() {
        InternalSecretProperties properties = new InternalSecretProperties();
        properties.setSharedSecret(SECRET);
        filter = new InternalSecretFilter(properties);
    }

    @Test
    void allowsInternalRequestWithCorrectSecret() throws Exception {
        when(request.getRequestURI()).thenReturn("/internal/authorize");
        when(request.getHeader(HEADER)).thenReturn(SECRET);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void rejectsInternalRequestWithMissingSecret() throws Exception {
        when(request.getRequestURI()).thenReturn("/internal/authorize");
        when(request.getHeader(HEADER)).thenReturn(null);

        filter.doFilter(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void rejectsInternalRequestWithWrongSecret() throws Exception {
        when(request.getRequestURI()).thenReturn("/admin/resources");
        when(request.getHeader(HEADER)).thenReturn("not-the-secret");

        filter.doFilter(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void skipsSecretCheckForNonInternalNonAdminPaths() throws Exception {
        when(request.getRequestURI()).thenReturn("/actuator/health");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }
}
