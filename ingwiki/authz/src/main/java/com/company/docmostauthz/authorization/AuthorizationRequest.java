package com.company.docmostauthz.authorization;

public record AuthorizationRequest(
        String resourceType,
        String resourceId,
        Permission permission
) {
}
