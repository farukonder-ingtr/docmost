package com.company.docmostauthz.auth;

import com.company.docmostauthz.ldap.LdapService;
import jakarta.validation.Valid;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Called by Docmost to verify LDAP credentials and fetch group membership.
 * Docmost then creates its own native session; this service never issues
 * a Docmost-facing session cookie (see spec section 17).
 */
@RestController
@RequestMapping("/internal")
public class LdapAuthenticationController {

    private final LdapService ldapService;

    public LdapAuthenticationController(LdapService ldapService) {
        this.ldapService = ldapService;
    }

    @PostMapping("/authenticate")
    public AuthenticateResponse authenticate(@Valid @RequestBody AuthenticateRequest request) {
        return AuthenticateResponse.from(
                ldapService.authenticate(request.username(), request.password()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public void onBadCredentials() {
        // body intentionally empty, do not leak whether the username exists
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public void onUnknownUser() {
    }
}
