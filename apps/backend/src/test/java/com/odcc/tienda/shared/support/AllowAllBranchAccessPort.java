package com.odcc.tienda.shared.support;

import com.odcc.tienda.shared.application.authorization.BranchAccessPort;
import com.odcc.tienda.shared.application.authorization.BranchScope;

import java.util.Set;
import java.util.UUID;

public class AllowAllBranchAccessPort implements BranchAccessPort {
    @Override
    public BranchScope resolveScope(UUID userId) {
        return new BranchScope(true, Set.of());
    }

    @Override
    public void requireAccess(UUID userId, UUID branchId) {
        // Test double with global access.
    }
}
