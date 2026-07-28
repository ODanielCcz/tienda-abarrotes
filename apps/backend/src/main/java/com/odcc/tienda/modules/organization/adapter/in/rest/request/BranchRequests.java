package com.odcc.tienda.modules.organization.adapter.in.rest.request;

import com.odcc.tienda.modules.organization.domain.model.BranchStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class BranchRequests {
    private BranchRequests() {
    }

    public record CreateBranchRequest(@NotBlank @Size(max = 30) String code, @NotBlank @Size(max = 150) String name, @Size(max = 200) String legalName, String timezone, String currencyCode) {
    }

    public record UpdateBranchRequest(@NotBlank @Size(max = 30) String code, @NotBlank @Size(max = 150) String name, @Size(max = 200) String legalName, String timezone, String currencyCode) {
    }

    public record ChangeBranchStatusRequest(@NotNull BranchStatus status) {
    }
}