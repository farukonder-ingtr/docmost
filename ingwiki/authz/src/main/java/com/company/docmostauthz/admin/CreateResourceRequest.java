package com.company.docmostauthz.admin;

import jakarta.validation.constraints.NotBlank;

public record CreateResourceRequest(
        @NotBlank String resourceType,
        @NotBlank String docmostId,
        String parentDocmostId,
        String name
) {
}
