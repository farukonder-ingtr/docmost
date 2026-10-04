package com.company.docmostauthz.admin;

import com.company.docmostauthz.authorization.Permission;
import com.company.docmostauthz.authorization.PolicyEntity;
import com.company.docmostauthz.authorization.PolicyRepository;
import com.company.docmostauthz.authorization.ResourceEntity;
import com.company.docmostauthz.authorization.ResourceRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceAdminControllerTest {

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private PolicyRepository policyRepository;

    private ResourceAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new ResourceAdminController(resourceRepository, policyRepository);
    }

    @Test
    void createResourceInsertsNewEntityWhenAbsent() {
        when(resourceRepository.findByResourceTypeAndDocmostId("PAGE", "page-1")).thenReturn(Optional.empty());
        when(resourceRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        CreateResourceRequest request = new CreateResourceRequest("PAGE", "page-1", null, "Finance Only Page");
        ResourceEntity result = controller.createResource(request);

        assertThat(result.getResourceType()).isEqualTo("PAGE");
        assertThat(result.getDocmostId()).isEqualTo("page-1");
        assertThat(result.getName()).isEqualTo("Finance Only Page");
    }

    @Test
    void createResourceUpdatesExistingEntityInstead() {
        ResourceEntity existing = new ResourceEntity();
        existing.setResourceType("PAGE");
        existing.setDocmostId("page-1");
        existing.setName("Old Name");

        when(resourceRepository.findByResourceTypeAndDocmostId("PAGE", "page-1")).thenReturn(Optional.of(existing));
        when(resourceRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        controller.createResource(new CreateResourceRequest("PAGE", "page-1", "parent-1", "New Name"));

        ArgumentCaptor<ResourceEntity> captor = ArgumentCaptor.forClass(ResourceEntity.class);
        verify(resourceRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("New Name");
        assertThat(captor.getValue().getParentDocmostId()).isEqualTo("parent-1");
    }

    @Test
    void addPolicyAttachesPolicyToExistingPageResource() {
        ResourceEntity page = new ResourceEntity();
        page.setResourceType("PAGE");
        page.setDocmostId("page-1");

        when(resourceRepository.findByResourceTypeAndDocmostId("PAGE", "page-1")).thenReturn(Optional.of(page));
        when(policyRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        PolicyEntity result = controller.addPolicy("page-1", new CreatePolicyRequest("DOCMOST-FINANCE", Permission.VIEW));

        assertThat(result.getLdapGroup()).isEqualTo("DOCMOST-FINANCE");
        assertThat(result.getPermission()).isEqualTo(Permission.VIEW);
        assertThat(result.getResource()).isSameAs(page);
    }

    @Test
    void addPolicyFallsBackToSpaceResourceWhenNoPageMatches() {
        ResourceEntity space = new ResourceEntity();
        space.setResourceType("SPACE");
        space.setDocmostId("space-1");

        when(resourceRepository.findByResourceTypeAndDocmostId("PAGE", "space-1")).thenReturn(Optional.empty());
        when(resourceRepository.findByResourceTypeAndDocmostId("SPACE", "space-1")).thenReturn(Optional.of(space));
        when(policyRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        PolicyEntity result = controller.addPolicy("space-1", new CreatePolicyRequest("DOCMOST-IT", Permission.EDIT));

        assertThat(result.getResource()).isSameAs(space);
    }

    @Test
    void addPolicyThrowsWhenResourceDoesNotExistAtAll() {
        when(resourceRepository.findByResourceTypeAndDocmostId("PAGE", "missing")).thenReturn(Optional.empty());
        when(resourceRepository.findByResourceTypeAndDocmostId("SPACE", "missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.addPolicy("missing", new CreatePolicyRequest("DOCMOST-IT", Permission.VIEW)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void listPoliciesDelegatesToRepository() {
        List<PolicyEntity> policies = List.of(new PolicyEntity());
        when(policyRepository.findByResource_DocmostId("page-1")).thenReturn(policies);

        assertThat(controller.listPolicies("page-1")).isEqualTo(policies);
    }

    @Test
    void removePolicyDelegatesToRepository() {
        controller.removePolicy("page-1", "DOCMOST-HR");

        verify(policyRepository).deleteByResource_DocmostIdAndLdapGroup("page-1", "DOCMOST-HR");
    }
}
