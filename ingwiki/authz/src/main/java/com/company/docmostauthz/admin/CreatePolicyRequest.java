package com.company.docmostauthz.admin;

import com.company.docmostauthz.authorization.Permission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreatePolicyRequest(
        @NotBlank String ldapGroup,
        @NotNull Permission permission
) {
}
