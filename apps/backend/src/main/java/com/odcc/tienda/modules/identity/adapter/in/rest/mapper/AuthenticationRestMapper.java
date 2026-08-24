package com.odcc.tienda.modules.identity.adapter.in.rest.mapper;

import com.odcc.tienda.modules.identity.adapter.in.rest.request.LoginRequest;
import com.odcc.tienda.modules.identity.adapter.in.rest.response.LoginResponse;
import com.odcc.tienda.modules.identity.application.command.LoginCommand;
import com.odcc.tienda.modules.identity.application.model.LoginResult;
import com.odcc.tienda.shared.infrastructure.mapping.CentralMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CentralMapperConfig.class)
public interface AuthenticationRestMapper {

    @Mapping(target = "username", source = "request.username")
    @Mapping(target = "password", source = "request.password")
    @Mapping(target = "clientAddress", source = "clientAddress")
    LoginCommand toCommand(LoginRequest request, String clientAddress);

    LoginResponse toResponse(LoginResult result);
}
