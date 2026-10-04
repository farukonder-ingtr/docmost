package com.company.docmostauthz.authorization;

import com.company.docmostauthz.config.LdapProperties;
import com.company.docmostauthz.ldap.LdapService;
import com.company.docmostauthz.ldap.LdapUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationServiceTest {

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private LdapService ldapService;

    private LdapProperties ldapProperties;
    private AuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        ldapProperties = new LdapProperties();
        ldapProperties.setAdminGroup("DOCMOST-ADMIN");
        authorizationService = new AuthorizationService(policyRepository, resourceRepository, ldapService, ldapProperties);
    }

    private LdapUser userWithGroups(String... groups) {
        return new LdapUser("faruk", "faruk@placeholder.test", "Faruk Yilmaz", "uid=faruk,ou=Users,dc=placeholder,dc=test", Set.of(groups));
    }

    private PolicyEntity policy(String ldapGroup, Permission permission) {
        PolicyEntity entity = new PolicyEntity();
        entity.setLdapGroup(ldapGroup);
        entity.setPermission(permission);
        return entity;
    }

    @Test
    void adminGroupBypassesAllPolicyChecks() {
        when(ldapService.findUserByEmail("faruk@placeholder.test")).thenReturn(userWithGroups("DOCMOST-ADMIN"));

        AuthorizationDecision decision = authorizationService.authorize(
                "faruk@placeholder.test", "PAGE", "page-1", Permission.ADMIN);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).contains("administrator");
    }

    @Test
    void unmanagedResourceWithNoPoliciesIsAllowed() {
        when(ldapService.findUserByEmail("ali@placeholder.test")).thenReturn(userWithGroups("DOCMOST-HR"));
        when(policyRepository.findByResource_DocmostId("page-1")).thenReturn(List.of());
        when(resourceRepository.findByResourceTypeAndDocmostId("PAGE", "page-1")).thenReturn(Optional.empty());

        AuthorizationDecision decision = authorizationService.authorize(
                "ali@placeholder.test", "PAGE", "page-1", Permission.VIEW);

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void matchingGroupWithSufficientPermissionIsAllowed() {
        when(ldapService.findUserByEmail("ayse@placeholder.test")).thenReturn(userWithGroups("DOCMOST-FINANCE"));
        when(policyRepository.findByResource_DocmostId("page-1"))
                .thenReturn(List.of(policy("DOCMOST-FINANCE", Permission.VIEW)));
        when(resourceRepository.findByResourceTypeAndDocmostId("PAGE", "page-1")).thenReturn(Optional.empty());

        AuthorizationDecision decision = authorizationService.authorize(
                "ayse@placeholder.test", "PAGE", "page-1", Permission.VIEW);

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void nonMatchingGroupIsDenied() {
        when(ldapService.findUserByEmail("ali@placeholder.test")).thenReturn(userWithGroups("DOCMOST-HR"));
        when(policyRepository.findByResource_DocmostId("page-1"))
                .thenReturn(List.of(policy("DOCMOST-FINANCE", Permission.VIEW)));
        // Denied on the target resource itself, so the parent-resource lookup is never reached.

        AuthorizationDecision decision = authorizationService.authorize(
                "ali@placeholder.test", "PAGE", "page-1", Permission.VIEW);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("No matching LDAP group");
    }

    @Test
    void viewOnlyPolicyDoesNotSatisfyEditRequirement() {
        when(ldapService.findUserByEmail("ayse@placeholder.test")).thenReturn(userWithGroups("DOCMOST-FINANCE"));
        when(policyRepository.findByResource_DocmostId("page-1"))
                .thenReturn(List.of(policy("DOCMOST-FINANCE", Permission.VIEW)));

        AuthorizationDecision decision = authorizationService.authorize(
                "ayse@placeholder.test", "PAGE", "page-1", Permission.EDIT);

        assertThat(decision.allowed()).isFalse();
    }

    @Test
    void editPolicyAlsoSatisfiesViewRequirement() {
        when(ldapService.findUserByEmail("faruk@placeholder.test")).thenReturn(userWithGroups("DOCMOST-ARCHITECT"));
        when(policyRepository.findByResource_DocmostId("page-1"))
                .thenReturn(List.of(policy("DOCMOST-ARCHITECT", Permission.EDIT)));
        when(resourceRepository.findByResourceTypeAndDocmostId("PAGE", "page-1")).thenReturn(Optional.empty());

        AuthorizationDecision decision = authorizationService.authorize(
                "faruk@placeholder.test", "PAGE", "page-1", Permission.VIEW);

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void restrictedParentDeniesChildEvenWithoutItsOwnPolicy() {
        when(ldapService.findUserByEmail("ali@placeholder.test")).thenReturn(userWithGroups("DOCMOST-HR"));
        // child has no policies of its own
        when(policyRepository.findByResource_DocmostId("child-page")).thenReturn(List.of());
        // but its parent is restricted to a group ali isn't in
        when(policyRepository.findByResource_DocmostId("parent-page"))
                .thenReturn(List.of(policy("DOCMOST-FINANCE", Permission.VIEW)));

        ResourceEntity child = new ResourceEntity();
        child.setResourceType("PAGE");
        child.setDocmostId("child-page");
        child.setParentDocmostId("parent-page");

        when(resourceRepository.findByResourceTypeAndDocmostId("PAGE", "child-page")).thenReturn(Optional.of(child));
        // Denied on the parent, so a grandparent lookup is never reached.

        AuthorizationDecision decision = authorizationService.authorize(
                "ali@placeholder.test", "PAGE", "child-page", Permission.VIEW);

        assertThat(decision.allowed()).isFalse();
    }

    @Test
    void unresolvableLdapIdentityFailsClosed() {
        when(ldapService.findUserByEmail(any())).thenThrow(new IllegalArgumentException("LDAP user not found"));

        AuthorizationDecision decision = authorizationService.authorize(
                "ghost@placeholder.test", "PAGE", "page-1", Permission.VIEW);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("Could not verify LDAP identity");
    }
}
