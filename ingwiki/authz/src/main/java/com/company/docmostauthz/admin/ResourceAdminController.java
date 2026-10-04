package com.company.docmostauthz.admin;

import com.company.docmostauthz.authorization.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Manages which LDAP groups may VIEW/EDIT/ADMIN a Docmost resource (page/space).
 * Kept on the internal network alongside /internal/** — never exposed publicly.
 */
@RestController
@RequestMapping("/admin/resources")
public class ResourceAdminController {

    private final ResourceRepository resourceRepository;
    private final PolicyRepository policyRepository;

    public ResourceAdminController(
            ResourceRepository resourceRepository,
            PolicyRepository policyRepository) {

        this.resourceRepository = resourceRepository;
        this.policyRepository = policyRepository;
    }

    @PostMapping
    public ResourceEntity createResource(@Valid @RequestBody CreateResourceRequest request) {

        ResourceEntity resource = resourceRepository
                .findByResourceTypeAndDocmostId(request.resourceType(), request.docmostId())
                .orElseGet(ResourceEntity::new);

        resource.setResourceType(request.resourceType());
        resource.setDocmostId(request.docmostId());
        resource.setParentDocmostId(request.parentDocmostId());
        resource.setName(request.name());

        return resourceRepository.save(resource);
    }

    @PostMapping("/{docmostId}/policies")
    public PolicyEntity addPolicy(
            @PathVariable String docmostId,
            @Valid @RequestBody CreatePolicyRequest request) {

        ResourceEntity resource = findResourceOrThrow(docmostId);

        PolicyEntity policy = new PolicyEntity();
        policy.setResource(resource);
        policy.setLdapGroup(request.ldapGroup());
        policy.setPermission(request.permission());

        return policyRepository.save(policy);
    }

    @GetMapping("/{docmostId}/policies")
    public List<PolicyEntity> listPolicies(@PathVariable String docmostId) {
        return policyRepository.findByResource_DocmostId(docmostId);
    }

    @DeleteMapping("/{docmostId}/policies/{ldapGroup}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePolicy(@PathVariable String docmostId, @PathVariable String ldapGroup) {
        policyRepository.deleteByResource_DocmostIdAndLdapGroup(docmostId, ldapGroup);
    }

    private ResourceEntity findResourceOrThrow(String docmostId) {
        return resourceRepository.findByResourceTypeAndDocmostId("PAGE", docmostId)
                .or(() -> resourceRepository.findByResourceTypeAndDocmostId("SPACE", docmostId))
                .orElseThrow(() -> new EntityNotFoundException("Resource not found: " + docmostId));
    }
}
