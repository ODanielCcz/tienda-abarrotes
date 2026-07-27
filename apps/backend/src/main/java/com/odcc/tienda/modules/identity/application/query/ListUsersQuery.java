package com.odcc.tienda.modules.identity.application.query;

import com.odcc.tienda.modules.identity.domain.model.UserAccountStatus;

public record ListUsersQuery(
    String search,
    UserAccountStatus status,
    String roleCode
) {
}
