package com.company.docmostauthz.auth;

import jakarta.validation.constraints.NotBlank;

public record AuthenticateRequest(
        @NotBlank String username,
        @NotBlank String password
) {
}
