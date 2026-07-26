package com.odcc.tienda.modules.identity.application.port.in;

import com.odcc.tienda.modules.identity.application.command.LoginCommand;
import com.odcc.tienda.modules.identity.application.model.LoginResult;

public interface LoginUseCase {

    LoginResult execute(LoginCommand command);
}
