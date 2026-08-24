package com.odcc.tienda.modules.identity.adapter.in.rest;

import com.odcc.tienda.modules.identity.adapter.in.rest.mapper.AuthenticationRestMapper;
import com.odcc.tienda.modules.identity.adapter.in.rest.request.LoginRequest;
import com.odcc.tienda.modules.identity.adapter.in.rest.response.LoginResponse;
import com.odcc.tienda.modules.identity.application.model.LoginResult;
import com.odcc.tienda.modules.identity.application.port.in.LoginUseCase;
import com.odcc.tienda.shared.web.response.ApiResponseDto;
import com.odcc.tienda.shared.web.correlation.CorrelationIdFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Inicio de sesión y emisión de tokens")
public class AuthenticationController {

    private final LoginUseCase loginUseCase;
    private final AuthenticationRestMapper mapper;

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión y obtener un token de acceso")
    @SecurityRequirements
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Autenticación correcta"),
        @ApiResponse(responseCode = "400", description = "Solicitud inválida"),
        @ApiResponse(responseCode = "401", description = "Credenciales inválidas"),
        @ApiResponse(responseCode = "429", description = "Demasiados intentos"),
        @ApiResponse(
            responseCode = "503",
            description = "Protección de inicio de sesión no disponible"
        )
    })
    public ResponseEntity<ApiResponseDto<LoginResponse>> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest servletRequest
    ) {
        LoginResult result = loginUseCase.execute(
            mapper.toCommand(request, servletRequest.getRemoteAddr())
        );

        return ResponseEntity.ok(
            ApiResponseDto.success(
                HttpStatus.OK,
                "LOGIN_SUCCEEDED",
                "Inicio de sesión correcto",
                mapper.toResponse(result),
                servletRequest.getRequestURI(),
                CorrelationIdFilter.from(servletRequest)
            )
        );
    }
}
