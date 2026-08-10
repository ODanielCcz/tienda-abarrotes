package com.odcc.tienda.shared.application.authorization;

import java.util.Set;
import java.util.UUID;

public record BranchScope(boolean globalAccess, Set<UUID> branchIds) {

    public BranchScope {
        branchIds = branchIds == null ? Set.of() : Set.copyOf(branchIds);
    }

    public static BranchScope global() {
        return new BranchScope(true, Set.of());
    }

    public static BranchScope restricted(Set<UUID> branchIds) {
        return new BranchScope(false, branchIds);
    }

    public boolean allows(UUID branchId) {
        return globalAccess || branchIds.contains(branchId);
    }
}
