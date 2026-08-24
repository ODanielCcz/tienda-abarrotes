package com.odcc.tienda.modules.identity.adapter.in.rest;

import com.odcc.tienda.modules.identity.adapter.in.rest.error.AuthenticationExceptionHandler;
import com.odcc.tienda.modules.identity.adapter.in.rest.mapper.AuthenticationRestMapperImpl;
import com.odcc.tienda.modules.identity.application.command.LoginCommand;
import com.odcc.tienda.modules.identity.application.exception.InvalidCredentialsException;
import com.odcc.tienda.modules.identity.application.exception.LoginRateLimitUnavailableException;
import com.odcc.tienda.modules.identity.application.exception.LoginRateLimitedException;
import com.odcc.tienda.modules.identity.application.model.LoginRateLimitDimension;
import com.odcc.tienda.modules.identity.application.model.AuthenticatedUser;
import com.odcc.tienda.modules.identity.application.model.LoginResult;
import com.odcc.tienda.modules.identity.application.port.in.LoginUseCase;
import com.odcc.tienda.shared.web.correlation.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthenticationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
    AuthenticationRestMapperImpl.class,
    AuthenticationExceptionHandler.class
})
class AuthenticationControllerTest {

    private static final String ENDPOINT = "/api/v1/auth/login";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoginUseCase loginUseCase;

    @Test
    void shouldReturnAccessTokenForValidCredentials() throws Exception {
        UUID userId = UUID.fromString(
            "d42a895d-00ac-4a4c-baa5-4e3742f22398"
        );
        given(loginUseCase.execute(any(LoginCommand.class)))
            .willReturn(
                new LoginResult(
                    "signed.jwt.token",
                    "Bearer",
                    Instant.parse("2026-07-24T09:00:00Z"),
                    new AuthenticatedUser(
                        userId,
                        "admin",
                        "Administrador",
                        Set.of("SYSTEM_ADMIN"),
                        Set.of("CATALOG_BRAND_READ")
                    )
                )
            );

        mockMvc.perform(
                post(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "username": "admin",
                          "password": "correct-password"
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("LOGIN_SUCCEEDED"))
            .andExpect(jsonPath("$.data.accessToken").value("signed.jwt.token"))
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.data.expiresAt").value("2026-07-24T09:00:00Z"))
            .andExpect(jsonPath("$.data.user").doesNotExist());
    }

    @Test
    void shouldReturnUnauthorizedForInvalidCredentials() throws Exception {
        given(loginUseCase.execute(any(LoginCommand.class)))
            .willThrow(new InvalidCredentialsException());

        mockMvc.perform(
                post(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "username": "admin",
                          "password": "wrong-password"
                        }
                        """)
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void shouldRejectEmptyCredentialsAtHttpBoundary() throws Exception {
        mockMvc.perform(
                post(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"username": "", "password": ""}
                        """)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.username").exists())
            .andExpect(jsonPath("$.errors.password").exists());

        verifyNoInteractions(loginUseCase);
    }

    @Test
    void shouldReturn429WithRetryAfterAndCorrelationId() throws Exception {
        given(loginUseCase.execute(any(LoginCommand.class)))
            .willThrow(new LoginRateLimitedException(
                37,
                LoginRateLimitDimension.PAIR
            ));

        mockMvc.perform(loginRequest("correlation-rate-limited-123"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "37"))
            .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"))
            .andExpect(jsonPath("$.reason").value("Too Many Requests"))
            .andExpect(jsonPath("$.correlationId")
                .value("correlation-rate-limited-123"));
    }

    @Test
    void shouldReturn503WhenRateLimitBackendIsUnavailable() throws Exception {
        given(loginUseCase.execute(any(LoginCommand.class)))
            .willThrow(new LoginRateLimitUnavailableException(5));

        mockMvc.perform(loginRequest("correlation-redis-unavailable-123"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().string("Retry-After", "5"))
            .andExpect(jsonPath("$.code")
                .value("LOGIN_RATE_LIMIT_UNAVAILABLE"))
            .andExpect(jsonPath("$.reason").value("Service Unavailable"))
            .andExpect(jsonPath("$.correlationId")
                .value("correlation-redis-unavailable-123"));
    }

    private static MockHttpServletRequestBuilder loginRequest(
        String correlationId
    ) {
        return post(ENDPOINT)
            .requestAttr(CorrelationIdFilter.ATTRIBUTE_NAME, correlationId)
            .header(CorrelationIdFilter.HEADER_NAME, correlationId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "username": "admin",
                  "password": "correct-password"
                }
                """);
    }
}


