-- Run once against the docmost_authz database (mounted via docker-entrypoint-initdb.d).
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS resource (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    resource_type VARCHAR(20) NOT NULL,
    docmost_id    VARCHAR(100) NOT NULL,

    parent_docmost_id VARCHAR(100),

    name VARCHAR(500),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uk_resource
        UNIQUE (resource_type, docmost_id)
);

CREATE INDEX IF NOT EXISTS ix_resource_parent
    ON resource(parent_docmost_id);

CREATE TABLE IF NOT EXISTS policy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    resource_id UUID NOT NULL
        REFERENCES resource(id)
        ON DELETE CASCADE,

    ldap_group VARCHAR(255) NOT NULL,

    permission VARCHAR(20) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uk_policy
        UNIQUE(resource_id, ldap_group, permission),

    CONSTRAINT chk_permission
        CHECK(permission IN ('VIEW', 'EDIT', 'ADMIN'))
);

CREATE INDEX IF NOT EXISTS ix_policy_resource
    ON policy(resource_id);

CREATE INDEX IF NOT EXISTS ix_policy_group
    ON policy(ldap_group);
