package com.company.docmostauthz.ldap;

import com.company.docmostauthz.config.LdapProperties;
import com.company.docmostauthz.config.RedisConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.BaseLdapPathContextSource;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class LdapService {

    private final LdapTemplate ldapTemplate;
    private final BaseLdapPathContextSource contextSource;
    private final LdapProperties ldapProperties;

    public LdapService(
            LdapTemplate ldapTemplate,
            BaseLdapPathContextSource contextSource,
            LdapProperties ldapProperties) {

        this.ldapTemplate = ldapTemplate;
        this.contextSource = contextSource;
        this.ldapProperties = ldapProperties;
    }

    /**
     * Binds as the user to verify the supplied credentials, then resolves
     * their profile and group membership. Throws BadCredentialsException on
     * any bind failure, including unknown usernames (do not leak which).
     */
    public LdapUser authenticate(String username, String password) {

        LdapUser user = findUser(username);

        // Base-DN search scoped to the user's own entry: binds as that entry with the given password.
        boolean bindOk = ldapTemplate.authenticate(user.dn(), "(objectClass=*)", password);

        if (!bindOk) {
            throw new BadCredentialsException("Invalid LDAP credentials");
        }

        return user;
    }

    @Cacheable(cacheNames = RedisConfig.USER_GROUPS_CACHE, key = "#username")
    public LdapUser findUser(String username) {
        return search(ldapProperties.getUserSearchFilter().replace("{0}", username));
    }

    /**
     * Docmost only knows the authenticated user's email (not their LDAP uid),
     * so /internal/authorize resolves identity and group membership by mail
     * instead of the uid-based filter used at login time.
     */
    @Cacheable(cacheNames = RedisConfig.USER_GROUPS_CACHE, key = "'email:' + #email")
    public LdapUser findUserByEmail(String email) {
        return search("(mail=" + escapeFilter(email) + ")");
    }

    private LdapUser search(String filter) {

        List<LdapUser> users = ldapTemplate.search(
                ldapProperties.getUserSearchBase(),
                filter,
                (ContextMapper<LdapUser>) ctx -> {

                    DirContextAdapter adapter = (DirContextAdapter) ctx;
                    String uid = adapter.getStringAttribute("uid");
                    String mail = adapter.getStringAttribute("mail");
                    String displayName = adapter.getStringAttribute("displayName");
                    // Full DN (not base-relative) so it works for bind + group lookups.
                    String dn = adapter.getNameInNamespace();

                    return new LdapUser(uid, mail, displayName, dn, resolveGroups(dn));
                });

        if (users.isEmpty()) {
            throw new IllegalArgumentException("LDAP user not found for filter: " + filter);
        }

        return users.get(0);
    }

    private Set<String> resolveGroups(String userDn) {

        /*
         * Direct "member" lookup only — nested/recursive AD groups require
         * LDAP_MATCHING_RULE_IN_CHAIN or app-side recursive resolution (see spec §9).
         */
        String filter = "(&(|(objectClass=group)(objectClass=groupOfNames))(member=" + escapeFilter(userDn) + "))";

        List<String> groups = ldapTemplate.search(
                ldapProperties.getGroupSearchBase(),
                filter,
                (AttributesMapper<String>) attrs -> get(attrs, "cn"));

        return new HashSet<>(groups);
    }

    private String get(Attributes attributes, String name) throws NamingException {
        var attr = attributes.get(name);
        return attr == null ? null : String.valueOf(attr.get());
    }

    private String escapeFilter(String value) {
        return value
                .replace("\\", "\\5c")
                .replace("*", "\\2a")
                .replace("(", "\\28")
                .replace(")", "\\29")
                .replace("\0", "\\00");
    }
}
