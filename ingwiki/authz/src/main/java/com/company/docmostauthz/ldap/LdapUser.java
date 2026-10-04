package com.company.docmostauthz.ldap;

import java.io.Serializable;
import java.util.Set;

// Serializable so it can round-trip through the Redis cache (JdkSerializationRedisSerializer).
public record LdapUser(
        String username,
        String email,
        String displayName,
        String dn,
        Set<String> groups
) implements Serializable {
}
