package com.company.docmostauthz.authorization;

import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal")
public class AuthorizationController {

    private final AuthorizationService service;

    public AuthorizationController(AuthorizationService service) {
        this.service = service;
    }

    @PostMapping("/authorize")
    public AuthorizationDecision authorize(
            @RequestHeader("X-Docmost-User") @NotBlank String username,
            @RequestBody AuthorizationRequest request) {

        return service.authorize(
                username,
                request.resourceType(),
                request.resourceId(),
                request.permission());
    }
}
