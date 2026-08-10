package com.odcc.tienda.shared.application.authorization;

import java.util.UUID;

public interface BranchAccessPort {

    BranchScope resolveScope(UUID userId);

    void requireAccess(UUID userId, UUID branchId);
}
