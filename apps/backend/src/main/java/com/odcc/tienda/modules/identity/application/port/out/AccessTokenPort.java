package com.odcc.tienda.modules.identity.application.port.out;

import com.odcc.tienda.modules.identity.application.model.IssuedAccessToken;
import com.odcc.tienda.modules.identity.domain.model.UserAccount;

public interface AccessTokenPort {

    IssuedAccessToken issue(UserAccount userAccount);
}
