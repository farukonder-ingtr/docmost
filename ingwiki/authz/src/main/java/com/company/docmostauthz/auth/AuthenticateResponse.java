package com.company.docmostauthz.auth;

import com.company.docmostauthz.ldap.LdapUser;

import java.util.Set;

public record AuthenticateResponse(
        String username,
        String email,
        String displayName,
        Set<String> groups
) {
    public static AuthenticateResponse from(LdapUser user) {
        return new AuthenticateResponse(
                user.username(),
                user.email(),
                user.displayName(),
                user.groups());
    }
}
