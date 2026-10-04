package com.company.docmostauthz.authorization;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PolicyRepository extends JpaRepository<PolicyEntity, UUID> {

    List<PolicyEntity> findByResource_DocmostId(String docmostId);

    void deleteByResource_DocmostIdAndLdapGroup(String docmostId, String ldapGroup);
}
