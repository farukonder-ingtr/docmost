package com.company.docmostauthz.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects any request to /internal/** that does not present the shared secret.
 * Docker Compose keeps this service off any publicly reachable network, this
 * filter is a second layer of defense in case that network boundary is misconfigured.
 */
@Component
public class InternalSecretFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Docmost-Internal-Secret";

    private final InternalSecretProperties properties;

    public InternalSecretFilter(InternalSecretProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !(uri.startsWith("/internal/") || uri.startsWith("/admin/"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String provided = request.getHeader(HEADER);

        if (properties.getSharedSecret() == null
                || properties.getSharedSecret().isBlank()
                || provided == null
                || !properties.getSharedSecret().equals(provided)) {

            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
