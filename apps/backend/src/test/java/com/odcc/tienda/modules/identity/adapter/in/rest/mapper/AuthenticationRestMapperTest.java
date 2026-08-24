package com.odcc.tienda.modules.identity.adapter.in.rest.mapper;

import com.odcc.tienda.modules.identity.adapter.in.rest.request.LoginRequest;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationRestMapperTest {

    @Test
    void mapsClientAddressProvidedByTheTransportBoundary() {
        var mapper = Mappers.getMapper(AuthenticationRestMapper.class);

        var command = mapper.toCommand(
            new LoginRequest("admin", "Temporal123!"),
            "127.0.0.1"
        );

        assertThat(command.username()).isEqualTo("admin");
        assertThat(command.password()).isEqualTo("Temporal123!");
        assertThat(command.clientAddress()).isEqualTo("127.0.0.1");
    }
}
