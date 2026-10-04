package com.company.docmostauthz.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final InternalSecretFilter internalSecretFilter;

    public SecurityConfig(InternalSecretFilter internalSecretFilter) {
        this.internalSecretFilter = internalSecretFilter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(
                        org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        // Authorization/authentication decisions are protected by the
                        // shared-secret filter below instead of Spring Security's own
                        // authentication, since the caller is the Docmost backend, not a user.
                        .anyRequest().permitAll())
                .addFilterBefore(internalSecretFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
