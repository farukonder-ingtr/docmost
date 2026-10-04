package com.company.docmostauthz.authorization;

import com.company.docmostauthz.config.LdapProperties;
import com.company.docmostauthz.ldap.LdapService;
import com.company.docmostauthz.ldap.LdapUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationService.class);

    private final PolicyRepository policyRepository;
    private final ResourceRepository resourceRepository;
    private final LdapService ldapService;
    private final LdapProperties ldapProperties;

    public AuthorizationService(
            PolicyRepository policyRepository,
            ResourceRepository resourceRepository,
            LdapService ldapService,
            LdapProperties ldapProperties) {

        this.policyRepository = policyRepository;
        this.resourceRepository = resourceRepository;
        this.ldapService = ldapService;
        this.ldapProperties = ldapProperties;
    }

    /**
     * Authorizes a single resource, walking up parent resources so that a
     * restricted parent also restricts its children (fail closed throughout).
     * "docmostUserEmail" is Docmost's X-Docmost-User header value (the user's
     * email), not their LDAP uid.
     */
    public AuthorizationDecision authorize(String docmostUserEmail, String resourceType, String resourceId, Permission required) {

        final LdapUser user;
        try {
            user = ldapService.findUserByEmail(docmostUserEmail);
        } catch (Exception e) {
            log.warn("Authorization denied, could not resolve LDAP user for email '{}': {}", docmostUserEmail, e.getMessage());
            return AuthorizationDecision.deny("Could not verify LDAP identity");
        }

        if (user.groups().contains(ldapProperties.getAdminGroup())) {
            return AuthorizationDecision.allow("Docmost administrator");
        }

        String currentId = resourceId;
        boolean first = true;

        while (currentId != null) {

            AuthorizationDecision decision = authorizeAgainstResource(user, currentId, required, first);
            first = false;

            if (!decision.allowed()) {
                return decision;
            }

            currentId = resourceRepository
                    .findByResourceTypeAndDocmostId(resourceType, currentId)
                    .map(ResourceEntity::getParentDocmostId)
                    .orElse(null);
        }

        return AuthorizationDecision.allow("LDAP group authorized");
    }

    private AuthorizationDecision authorizeAgainstResource(
            LdapUser user, String resourceId, Permission required, boolean isTargetResource) {

        List<PolicyEntity> policies = policyRepository.findByResource_DocmostId(resourceId);

        // Unmanaged resource (no policies defined): only restrict if it is an
        // ancestor with policies elsewhere; an unmanaged target resource is allowed
        // so pages that were never explicitly restricted keep working.
        if (policies.isEmpty()) {
            return isTargetResource
                    ? AuthorizationDecision.allow("No policy configured for resource")
                    : AuthorizationDecision.allow("No policy configured for ancestor");
        }

        for (PolicyEntity policy : policies) {
            if (user.groups().contains(policy.getLdapGroup())
                    && hasPermission(policy.getPermission(), isTargetResource ? required : Permission.VIEW)) {
                return AuthorizationDecision.allow("LDAP group authorized");
            }
        }

        return AuthorizationDecision.deny("No matching LDAP group for resource " + resourceId);
    }

    private boolean hasPermission(Permission granted, Permission required) {
        return switch (granted) {
            case ADMIN -> true;
            case EDIT -> required == Permission.VIEW || required == Permission.EDIT;
            case VIEW -> required == Permission.VIEW;
        };
    }
}
