package com.odcc.tienda.modules.organization.application.command;

import com.odcc.tienda.modules.organization.domain.model.BranchStatus;

import java.util.UUID;

public final class BranchCommands {
    private BranchCommands() {
    }

    public record CreateBranchCommand(String code, String name, String legalName, String timezone, String currencyCode) {
    }

    public record UpdateBranchCommand(UUID branchId, String code, String name, String legalName, String timezone, String currencyCode) {
    }

    public record ChangeBranchStatusCommand(UUID branchId, BranchStatus status) {
    }
}