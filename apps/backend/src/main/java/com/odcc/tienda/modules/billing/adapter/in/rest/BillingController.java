package com.odcc.tienda.modules.billing.adapter.in.rest;

import com.odcc.tienda.modules.billing.adapter.in.rest.mapper.BillingRestMapper;
import com.odcc.tienda.modules.billing.adapter.in.rest.request.BillingRequests.ChangeStatusRequest;
import com.odcc.tienda.modules.billing.adapter.in.rest.request.BillingRequests.CreateFiscalDocumentRequest;
import com.odcc.tienda.modules.billing.adapter.in.rest.request.BillingRequests.FiscalProfileRequest;
import com.odcc.tienda.modules.billing.adapter.in.rest.request.BillingRequests.IssuerProfileRequest;
import com.odcc.tienda.modules.billing.application.model.BillingModels.FiscalDocument;
import com.odcc.tienda.modules.billing.application.model.BillingModels.FiscalProfile;
import com.odcc.tienda.modules.billing.application.model.BillingModels.IssuerProfile;
import com.odcc.tienda.modules.billing.application.port.in.BillingUseCases;
import com.odcc.tienda.shared.web.response.ApiResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
@Tag(name = "Facturacion", description = "Perfiles y documentos fiscales internos CFDI 4.0")
public class BillingController {

    private final BillingUseCases useCases;
    private final BillingRestMapper mapper;

    @PostMapping("/issuer-profiles")
    @Operation(summary = "Crear perfil fiscal emisor")
    @PreAuthorize("hasAuthority('BILLING_ISSUER_PROFILE_CREATE')")
    public ResponseEntity<ApiResponseDto<IssuerProfile>> createIssuer(
        @Valid @RequestBody IssuerProfileRequest request, HttpServletRequest servletRequest
    ) {
        IssuerProfile result = useCases.createIssuerProfile(
            mapper.toCreateIssuerCommand(request),
            currentUserId(servletRequest)
        );
        return created("ISSUER_PROFILE_CREATED", "Perfil emisor creado correctamente", result, servletRequest);
    }

    @GetMapping("/issuer-profiles")
    @Operation(summary = "Listar perfiles fiscales emisores")
    @PreAuthorize("hasAuthority('BILLING_ISSUER_PROFILE_READ')")
    public ResponseEntity<ApiResponseDto<List<IssuerProfile>>> listIssuers(
        @RequestParam(required = false) UUID branchId,
        @RequestParam(required = false) String status,
        HttpServletRequest servletRequest
    ) {
        return ok("ISSUER_PROFILES_FOUND", "Perfiles emisores consultados correctamente",
            useCases.listIssuerProfiles(branchId, status, currentUserId(servletRequest)), servletRequest);
    }

    @GetMapping("/issuer-profiles/{issuerProfileId}")
    @Operation(summary = "Consultar perfil fiscal emisor")
    @PreAuthorize("hasAuthority('BILLING_ISSUER_PROFILE_READ')")
    public ResponseEntity<ApiResponseDto<IssuerProfile>> getIssuer(
        @PathVariable UUID issuerProfileId, HttpServletRequest servletRequest
    ) {
        return ok("ISSUER_PROFILE_FOUND", "Perfil emisor consultado correctamente",
            useCases.getIssuerProfile(issuerProfileId, currentUserId(servletRequest)), servletRequest);
    }

    @PutMapping("/issuer-profiles/{issuerProfileId}")
    @Operation(summary = "Actualizar perfil fiscal emisor")
    @PreAuthorize("hasAuthority('BILLING_ISSUER_PROFILE_UPDATE')")
    public ResponseEntity<ApiResponseDto<IssuerProfile>> updateIssuer(
        @PathVariable UUID issuerProfileId,
        @Valid @RequestBody IssuerProfileRequest request,
        HttpServletRequest servletRequest
    ) {
        IssuerProfile result = useCases.updateIssuerProfile(
            mapper.toUpdateIssuerCommand(issuerProfileId, request),
            currentUserId(servletRequest)
        );
        return ok("ISSUER_PROFILE_UPDATED", "Perfil emisor actualizado correctamente", result, servletRequest);
    }

    @PatchMapping("/issuer-profiles/{issuerProfileId}/status")
    @Operation(summary = "Cambiar estado de perfil fiscal emisor")
    @PreAuthorize("hasAuthority('BILLING_ISSUER_PROFILE_STATUS')")
    public ResponseEntity<ApiResponseDto<IssuerProfile>> changeIssuerStatus(
        @PathVariable UUID issuerProfileId,
        @Valid @RequestBody ChangeStatusRequest request,
        HttpServletRequest servletRequest
    ) {
        return ok("ISSUER_PROFILE_STATUS_UPDATED", "Estado del perfil emisor actualizado correctamente",
            useCases.changeIssuerProfileStatus(mapper.toStatusCommand(issuerProfileId, request), currentUserId(servletRequest)), servletRequest);
    }

    @PostMapping("/fiscal-profiles")
    @Operation(summary = "Crear perfil fiscal de cliente")
    @PreAuthorize("hasAuthority('BILLING_FISCAL_PROFILE_CREATE')")
    public ResponseEntity<ApiResponseDto<FiscalProfile>> createFiscalProfile(
        @Valid @RequestBody FiscalProfileRequest request, HttpServletRequest servletRequest
    ) {
        FiscalProfile result = useCases.createFiscalProfile(
            mapper.toCreateFiscalProfileCommand(request),
            currentUserId(servletRequest)
        );
        return created("FISCAL_PROFILE_CREATED", "Perfil fiscal creado correctamente", result, servletRequest);
    }

    @GetMapping("/fiscal-profiles")
    @Operation(summary = "Listar perfiles fiscales de clientes")
    @PreAuthorize("hasAuthority('BILLING_FISCAL_PROFILE_READ')")
    public ResponseEntity<ApiResponseDto<List<FiscalProfile>>> listFiscalProfiles(
        @RequestParam(required = false) UUID customerId,
        @RequestParam(required = false) String status,
        HttpServletRequest servletRequest
    ) {
        return ok("FISCAL_PROFILES_FOUND", "Perfiles fiscales consultados correctamente",
            useCases.listFiscalProfiles(customerId, status, currentUserId(servletRequest)), servletRequest);
    }

    @GetMapping("/fiscal-profiles/{fiscalProfileId}")
    @Operation(summary = "Consultar perfil fiscal de cliente")
    @PreAuthorize("hasAuthority('BILLING_FISCAL_PROFILE_READ')")
    public ResponseEntity<ApiResponseDto<FiscalProfile>> getFiscalProfile(
        @PathVariable UUID fiscalProfileId, HttpServletRequest servletRequest
    ) {
        return ok("FISCAL_PROFILE_FOUND", "Perfil fiscal consultado correctamente",
            useCases.getFiscalProfile(fiscalProfileId, currentUserId(servletRequest)), servletRequest);
    }

    @PutMapping("/fiscal-profiles/{fiscalProfileId}")
    @Operation(summary = "Actualizar perfil fiscal de cliente")
    @PreAuthorize("hasAuthority('BILLING_FISCAL_PROFILE_UPDATE')")
    public ResponseEntity<ApiResponseDto<FiscalProfile>> updateFiscalProfile(
        @PathVariable UUID fiscalProfileId,
        @Valid @RequestBody FiscalProfileRequest request,
        HttpServletRequest servletRequest
    ) {
        FiscalProfile result = useCases.updateFiscalProfile(
            mapper.toUpdateFiscalProfileCommand(fiscalProfileId, request),
            currentUserId(servletRequest)
        );
        return ok("FISCAL_PROFILE_UPDATED", "Perfil fiscal actualizado correctamente", result, servletRequest);
    }

    @PatchMapping("/fiscal-profiles/{fiscalProfileId}/status")
    @Operation(summary = "Cambiar estado de perfil fiscal de cliente")
    @PreAuthorize("hasAuthority('BILLING_FISCAL_PROFILE_STATUS')")
    public ResponseEntity<ApiResponseDto<FiscalProfile>> changeFiscalProfileStatus(
        @PathVariable UUID fiscalProfileId,
        @Valid @RequestBody ChangeStatusRequest request,
        HttpServletRequest servletRequest
    ) {
        return ok("FISCAL_PROFILE_STATUS_UPDATED", "Estado del perfil fiscal actualizado correctamente",
            useCases.changeFiscalProfileStatus(mapper.toStatusCommand(fiscalProfileId, request), currentUserId(servletRequest)), servletRequest);
    }

    @PostMapping("/fiscal-documents")
    @Operation(summary = "Crear documento fiscal interno en borrador")
    @PreAuthorize("hasAuthority('BILLING_FISCAL_DOCUMENT_CREATE')")
    public ResponseEntity<ApiResponseDto<FiscalDocument>> createDocument(
        @Valid @RequestBody CreateFiscalDocumentRequest request, HttpServletRequest servletRequest
    ) {
        FiscalDocument result = useCases.createFiscalDocument(
            mapper.toCreateDocumentCommand(request),
            currentUserId(servletRequest)
        );
        return created("FISCAL_DOCUMENT_CREATED", "Documento fiscal creado correctamente", result, servletRequest);
    }

    @GetMapping("/fiscal-documents")
    @Operation(summary = "Listar documentos fiscales")
    @PreAuthorize("hasAuthority('BILLING_FISCAL_DOCUMENT_READ')")
    public ResponseEntity<ApiResponseDto<List<FiscalDocument>>> listDocuments(
        @RequestParam(required = false) UUID salesOrderId,
        @RequestParam(required = false) String status,
        HttpServletRequest servletRequest
    ) {
        return ok("FISCAL_DOCUMENTS_FOUND", "Documentos fiscales consultados correctamente",
            useCases.listFiscalDocuments(salesOrderId, status, currentUserId(servletRequest)), servletRequest);
    }

    @GetMapping("/fiscal-documents/{fiscalDocumentId}")
    @Operation(summary = "Consultar documento fiscal")
    @PreAuthorize("hasAuthority('BILLING_FISCAL_DOCUMENT_READ')")
    public ResponseEntity<ApiResponseDto<FiscalDocument>> getDocument(
        @PathVariable UUID fiscalDocumentId, HttpServletRequest servletRequest
    ) {
        return ok("FISCAL_DOCUMENT_FOUND", "Documento fiscal consultado correctamente",
            useCases.getFiscalDocument(fiscalDocumentId, currentUserId(servletRequest)), servletRequest);
    }

    @PostMapping("/fiscal-documents/{fiscalDocumentId}/ready")
    @Operation(summary = "Marcar documento fiscal como listo")
    @PreAuthorize("hasAuthority('BILLING_FISCAL_DOCUMENT_READY')")
    public ResponseEntity<ApiResponseDto<FiscalDocument>> readyDocument(
        @PathVariable UUID fiscalDocumentId, HttpServletRequest servletRequest
    ) {
        return ok("FISCAL_DOCUMENT_READY", "Documento fiscal marcado como listo",
            useCases.markFiscalDocumentReady(fiscalDocumentId, currentUserId(servletRequest)), servletRequest);
    }

    private <T> ResponseEntity<ApiResponseDto<T>> created(String code, String message, T data, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponseDto.success(HttpStatus.CREATED, code, message, data, request.getRequestURI()));
    }

    private <T> ResponseEntity<ApiResponseDto<T>> ok(String code, String message, T data, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, code, message, data, request.getRequestURI()));
    }

    private static UUID currentUserId(HttpServletRequest request) {
        if (request.getUserPrincipal() == null || request.getUserPrincipal().getName() == null) {
            throw new IllegalStateException("El JWT no contiene usuario");
        }
        return UUID.fromString(request.getUserPrincipal().getName());
    }
}
