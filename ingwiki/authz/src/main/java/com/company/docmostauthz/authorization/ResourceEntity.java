package com.company.docmostauthz.authorization;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "resource")
public class ResourceEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "docmost_id", nullable = false)
    private String docmostId;

    @Column(name = "parent_docmost_id")
    private String parentDocmostId;

    private String name;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getDocmostId() {
        return docmostId;
    }

    public void setDocmostId(String docmostId) {
        this.docmostId = docmostId;
    }

    public String getParentDocmostId() {
        return parentDocmostId;
    }

    public void setParentDocmostId(String parentDocmostId) {
        this.parentDocmostId = parentDocmostId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
