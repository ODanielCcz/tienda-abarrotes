package com.odcc.tienda.modules.billing.application.usecase;

import com.odcc.tienda.modules.billing.application.command.BillingCommands.ChangeStatusCommand;
import com.odcc.tienda.modules.billing.application.command.BillingCommands.CreateFiscalDocumentCommand;
import com.odcc.tienda.modules.billing.application.command.BillingCommands.CreateFiscalProfileCommand;
import com.odcc.tienda.modules.billing.application.command.BillingCommands.CreateIssuerProfileCommand;
import com.odcc.tienda.modules.billing.application.command.BillingCommands.UpdateFiscalProfileCommand;
import com.odcc.tienda.modules.billing.application.command.BillingCommands.UpdateIssuerProfileCommand;
import com.odcc.tienda.modules.billing.application.command.BillingCommands.UpdateProductFiscalClassificationCommand;
import com.odcc.tienda.modules.billing.application.command.BillingCommands.UpdateUnitFiscalClassificationCommand;
import com.odcc.tienda.modules.billing.application.exception.BillingConflictException;
import com.odcc.tienda.modules.billing.application.exception.BillingException;
import com.odcc.tienda.modules.billing.application.exception.BillingNotFoundException;
import com.odcc.tienda.modules.billing.application.model.BillingModels.FiscalDocument;
import com.odcc.tienda.modules.billing.application.model.BillingModels.FiscalDocumentSource;
import com.odcc.tienda.modules.billing.application.model.BillingModels.FiscalProfile;
import com.odcc.tienda.modules.billing.application.model.BillingModels.IssuerProfile;
import com.odcc.tienda.modules.billing.application.port.in.BillingUseCases;
import com.odcc.tienda.modules.billing.application.port.out.BillingRepositoryPort;
import com.odcc.tienda.shared.application.audit.BusinessAuditEvent;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public final class BillingService implements BillingUseCases {

    private static final Pattern RFC_PATTERN = Pattern.compile("^[A-ZÑ&]{3,4}[0-9]{6}[A-Z0-9]{3}$");
    private static final Pattern POSTAL_CODE_PATTERN = Pattern.compile("^[0-9]{5}$");
    private static final Pattern SAT_PRODUCT_PATTERN = Pattern.compile("^[0-9]{8}$");
    private static final Pattern SAT_UNIT_PATTERN = Pattern.compile("^[A-Z0-9]{1,5}$");
    private static final Pattern SHORT_CODE_PATTERN = Pattern.compile("^[A-Z0-9]{1,5}$");

    private final BillingRepositoryPort repository;
    private final TransactionRunner transactionRunner;
    private final BusinessAuditPort auditPort;

    @Override
    public IssuerProfile createIssuerProfile(CreateIssuerProfileCommand command) {
        CreateIssuerProfileCommand normalized = normalize(command);
        validateIssuer(normalized);
        requireActiveBranch(normalized.branchId());
        if (repository.activeIssuerExists(normalized.branchId(), null)) {
            throw new BillingConflictException("La sucursal ya tiene un perfil emisor activo");
        }
        return transactionRunner.required(() -> {
            IssuerProfile created = repository.createIssuerProfile(normalized);
            audit("ISSUER_PROFILE_CREATED", "ISSUER_PROFILE", created.issuerProfileId(), Map.of(), issuerState(created));
            return created;
        });
    }

    @Override
    public List<IssuerProfile> listIssuerProfiles(UUID branchId, String status) {
        return repository.listIssuerProfiles(branchId, normalizeStatusFilter(status));
    }

    @Override
    public IssuerProfile getIssuerProfile(UUID issuerProfileId) {
        if (issuerProfileId == null) throw new BillingException("El perfil emisor es obligatorio");
        return repository.findIssuerProfile(issuerProfileId)
            .orElseThrow(() -> new BillingNotFoundException("el perfil emisor " + issuerProfileId));
    }

    @Override
    public IssuerProfile updateIssuerProfile(UpdateIssuerProfileCommand command) {
        UpdateIssuerProfileCommand normalized = normalize(command);
        validateIssuer(normalized);
        IssuerProfile current = getIssuerProfile(normalized.issuerProfileId());
        requireActiveBranch(normalized.branchId());
        return transactionRunner.required(() -> {
            IssuerProfile updated = repository.updateIssuerProfile(normalized);
            audit("ISSUER_PROFILE_UPDATED", "ISSUER_PROFILE", updated.issuerProfileId(), issuerState(current), issuerState(updated));
            return updated;
        });
    }

    @Override
    public IssuerProfile changeIssuerProfileStatus(ChangeStatusCommand command) {
        validateStatusCommand(command);
        IssuerProfile current = getIssuerProfile(command.resourceId());
        String status = normalizeStatus(command.status());
        if ("ACTIVE".equals(status)) {
            requireActiveBranch(current.branchId());
            if (repository.activeIssuerExists(current.branchId(), current.issuerProfileId())) {
                throw new BillingConflictException("La sucursal ya tiene otro perfil emisor activo");
            }
        }
        return transactionRunner.required(() -> {
            IssuerProfile updated = repository.changeIssuerProfileStatus(current.issuerProfileId(), status);
            audit("ISSUER_PROFILE_STATUS_CHANGED", "ISSUER_PROFILE", updated.issuerProfileId(),
                Map.of("status", current.status()), Map.of("status", updated.status()));
            return updated;
        });
    }

    @Override
    public FiscalProfile createFiscalProfile(CreateFiscalProfileCommand command) {
        CreateFiscalProfileCommand normalized = normalize(command);
        validateFiscalProfile(normalized);
        requireActiveCustomer(normalized.customerId());
        return transactionRunner.required(() -> {
            FiscalProfile created = repository.createFiscalProfile(normalized);
            audit("FISCAL_PROFILE_CREATED", "FISCAL_PROFILE", created.fiscalProfileId(), Map.of(), fiscalState(created));
            return created;
        });
    }

    @Override
    public List<FiscalProfile> listFiscalProfiles(UUID customerId, String status) {
        return repository.listFiscalProfiles(customerId, normalizeStatusFilter(status));
    }

    @Override
    public FiscalProfile getFiscalProfile(UUID fiscalProfileId) {
        if (fiscalProfileId == null) throw new BillingException("El perfil fiscal es obligatorio");
        return repository.findFiscalProfile(fiscalProfileId)
            .orElseThrow(() -> new BillingNotFoundException("el perfil fiscal " + fiscalProfileId));
    }

    @Override
    public FiscalProfile updateFiscalProfile(UpdateFiscalProfileCommand command) {
        UpdateFiscalProfileCommand normalized = normalize(command);
        validateFiscalProfile(normalized);
        FiscalProfile current = getFiscalProfile(normalized.fiscalProfileId());
        requireActiveCustomer(normalized.customerId());
        return transactionRunner.required(() -> {
            FiscalProfile updated = repository.updateFiscalProfile(normalized);
            audit("FISCAL_PROFILE_UPDATED", "FISCAL_PROFILE", updated.fiscalProfileId(), fiscalState(current), fiscalState(updated));
            return updated;
        });
    }

    @Override
    public FiscalProfile changeFiscalProfileStatus(ChangeStatusCommand command) {
        validateStatusCommand(command);
        FiscalProfile current = getFiscalProfile(command.resourceId());
        String status = normalizeStatus(command.status());
        if ("ACTIVE".equals(status)) requireActiveCustomer(current.customerId());
        return transactionRunner.required(() -> {
            FiscalProfile updated = repository.changeFiscalProfileStatus(current.fiscalProfileId(), status);
            audit("FISCAL_PROFILE_STATUS_CHANGED", "FISCAL_PROFILE", updated.fiscalProfileId(),
                Map.of("status", current.status()), Map.of("status", updated.status()));
            return updated;
        });
    }

    @Override
    public void updateProductFiscalClassification(UpdateProductFiscalClassificationCommand command) {
        if (command == null || command.productId() == null) throw new BillingException("El producto es obligatorio");
        String code = normalizeRequired(command.satProductServiceCode(), "El codigo SAT del producto es obligatorio");
        if (!SAT_PRODUCT_PATTERN.matcher(code).matches()) throw new BillingException("El codigo SAT del producto debe contener 8 digitos");
        if (!repository.productExists(command.productId())) throw new BillingNotFoundException("el producto " + command.productId());
        transactionRunner.required(() -> {
            repository.updateProductFiscalClassification(command.productId(), code);
            audit("CATALOG_FISCAL_CLASSIFICATION_UPDATED", "PRODUCT", command.productId(), Map.of(), Map.of("satProductServiceCode", code));
            return null;
        });
    }

    @Override
    public void updateUnitFiscalClassification(UpdateUnitFiscalClassificationCommand command) {
        if (command == null || command.unitId() == null) throw new BillingException("La unidad de medida es obligatoria");
        String code = normalizeRequired(command.satUnitCode(), "El codigo SAT de unidad es obligatorio");
        if (!SAT_UNIT_PATTERN.matcher(code).matches()) throw new BillingException("El codigo SAT de unidad es invalido");
        if (!repository.unitExists(command.unitId())) throw new BillingNotFoundException("la unidad de medida " + command.unitId());
        transactionRunner.required(() -> {
            repository.updateUnitFiscalClassification(command.unitId(), code);
            audit("CATALOG_FISCAL_CLASSIFICATION_UPDATED", "UNIT_OF_MEASURE", command.unitId(), Map.of(), Map.of("satUnitCode", code));
            return null;
        });
    }

    @Override
    public FiscalDocument createFiscalDocument(CreateFiscalDocumentCommand command) {
        validateCreateDocument(command);
        FiscalDocumentSource source = repository.findFiscalDocumentSource(command.salesOrderId())
            .orElseThrow(() -> new BillingNotFoundException("la venta " + command.salesOrderId()));
        if (!"CONFIRMED".equals(source.orderStatus()) || !"PAID".equals(source.paymentStatus())) {
            throw new BillingConflictException("La venta debe estar confirmada y completamente pagada");
        }
        if (source.customerId() == null) throw new BillingConflictException("La venta debe tener un cliente para facturarse");
        IssuerProfile issuer = getIssuerProfile(command.issuerProfileId());
        FiscalProfile receiver = getFiscalProfile(command.fiscalProfileId());
        if (!"ACTIVE".equals(issuer.status())) throw new BillingConflictException("El perfil emisor no esta activo");
        if (!"ACTIVE".equals(receiver.status())) throw new BillingConflictException("El perfil fiscal del cliente no esta activo");
        if (!issuer.branchId().equals(source.branchId())) throw new BillingConflictException("El perfil emisor no pertenece a la sucursal de la venta");
        if (!receiver.customerId().equals(source.customerId())) throw new BillingConflictException("El perfil fiscal no pertenece al cliente de la venta");
        if (source.items().isEmpty()) throw new BillingConflictException("La venta no contiene partidas fiscales");
        if (source.items().stream().anyMatch(item -> item.satProductServiceCode() == null || item.satUnitCode() == null)) {
            throw new BillingConflictException("Todos los productos y unidades deben tener clasificacion SAT");
        }
        if (repository.activeIncomeDocumentExists(source.salesOrderId())) {
            throw new BillingConflictException("La venta ya tiene un documento fiscal vigente");
        }
        return transactionRunner.required(() -> {
            FiscalDocument document = repository.createFiscalDocument(command, issuer, receiver, source);
            audit("FISCAL_DOCUMENT_CREATED", "FISCAL_DOCUMENT", document.fiscalDocumentId(), Map.of(),
                Map.of("salesOrderId", document.salesOrderId(), "status", document.status(), "total", document.total()));
            return document;
        });
    }

    @Override
    public List<FiscalDocument> listFiscalDocuments(UUID salesOrderId, String status) {
        return repository.listFiscalDocuments(salesOrderId, normalizeDocumentStatus(status));
    }

    @Override
    public FiscalDocument getFiscalDocument(UUID fiscalDocumentId) {
        if (fiscalDocumentId == null) throw new BillingException("El documento fiscal es obligatorio");
        return repository.findFiscalDocument(fiscalDocumentId)
            .orElseThrow(() -> new BillingNotFoundException("el documento fiscal " + fiscalDocumentId));
    }

    @Override
    public FiscalDocument markFiscalDocumentReady(UUID fiscalDocumentId) {
        FiscalDocument current = getFiscalDocument(fiscalDocumentId);
        if (!"DRAFT".equals(current.status())) throw new BillingConflictException("Solo un documento DRAFT puede marcarse como READY");
        return transactionRunner.required(() -> {
            FiscalDocument ready = repository.markFiscalDocumentReady(fiscalDocumentId);
            audit("FISCAL_DOCUMENT_READY", "FISCAL_DOCUMENT", fiscalDocumentId,
                Map.of("status", current.status()), Map.of("status", ready.status()));
            return ready;
        });
    }

    private void requireActiveBranch(UUID branchId) {
        if (branchId == null || !repository.branchIsActive(branchId)) {
            throw new BillingNotFoundException("una sucursal activa con id " + branchId);
        }
    }

    private void requireActiveCustomer(UUID customerId) {
        if (customerId == null || !repository.customerIsActive(customerId)) {
            throw new BillingNotFoundException("un cliente activo con id " + customerId);
        }
    }

    private static void validateIssuer(CreateIssuerProfileCommand command) {
        if (command == null || command.branchId() == null) throw new BillingException("La sucursal es obligatoria");
        validateFiscalIdentity(command.rfc(), command.legalName(), command.postalCode(), command.fiscalRegimeCode());
    }

    private static void validateIssuer(UpdateIssuerProfileCommand command) {
        if (command == null || command.issuerProfileId() == null) throw new BillingException("El perfil emisor es obligatorio");
        if (command.branchId() == null) throw new BillingException("La sucursal es obligatoria");
        validateFiscalIdentity(command.rfc(), command.legalName(), command.postalCode(), command.fiscalRegimeCode());
    }

    private static void validateFiscalProfile(CreateFiscalProfileCommand command) {
        if (command == null || command.customerId() == null) throw new BillingException("El cliente es obligatorio");
        validateFiscalIdentity(command.rfc(), command.legalName(), command.postalCode(), command.fiscalRegimeCode());
        validateOptionalCode(command.cfdiUseCode(), "El uso CFDI es invalido");
    }

    private static void validateFiscalProfile(UpdateFiscalProfileCommand command) {
        if (command == null || command.fiscalProfileId() == null) throw new BillingException("El perfil fiscal es obligatorio");
        if (command.customerId() == null) throw new BillingException("El cliente es obligatorio");
        validateFiscalIdentity(command.rfc(), command.legalName(), command.postalCode(), command.fiscalRegimeCode());
        validateOptionalCode(command.cfdiUseCode(), "El uso CFDI es invalido");
    }

    private static void validateFiscalIdentity(String rfc, String legalName, String postalCode, String fiscalRegimeCode) {
        if (rfc == null || !RFC_PATTERN.matcher(rfc).matches()) throw new BillingException("El RFC no tiene un formato valido");
        if (legalName == null || legalName.isBlank() || legalName.length() > 300) throw new BillingException("La razon social es obligatoria y no puede superar 300 caracteres");
        if (postalCode == null || !POSTAL_CODE_PATTERN.matcher(postalCode).matches()) throw new BillingException("El codigo postal debe contener 5 digitos");
        if (fiscalRegimeCode == null || !SHORT_CODE_PATTERN.matcher(fiscalRegimeCode).matches()) throw new BillingException("El regimen fiscal es invalido");
    }

    private static void validateCreateDocument(CreateFiscalDocumentCommand command) {
        if (command == null) throw new BillingException("El documento fiscal es obligatorio");
        if (command.salesOrderId() == null) throw new BillingException("La venta es obligatoria");
        if (command.issuerProfileId() == null) throw new BillingException("El perfil emisor es obligatorio");
        if (command.fiscalProfileId() == null) throw new BillingException("El perfil fiscal receptor es obligatorio");
        validateOptionalCode(command.paymentFormCode(), "La forma de pago es invalida");
        validateOptionalCode(command.paymentMethodCode(), "El metodo de pago es invalido");
        if (trimToNull(command.series()) != null && trimToNull(command.series()).length() > 25) throw new BillingException("La serie no puede superar 25 caracteres");
        if (trimToNull(command.folio()) != null && trimToNull(command.folio()).length() > 50) throw new BillingException("El folio no puede superar 50 caracteres");
    }

    private static void validateOptionalCode(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized != null && !SHORT_CODE_PATTERN.matcher(normalized.toUpperCase(Locale.ROOT)).matches()) throw new BillingException(message);
    }

    private static void validateStatusCommand(ChangeStatusCommand command) {
        if (command == null || command.resourceId() == null) throw new BillingException("El recurso fiscal es obligatorio");
        normalizeStatus(command.status());
    }

    private static String normalizeStatus(String status) {
        String normalized = normalizeRequired(status, "El estado es obligatorio");
        if (!List.of("ACTIVE", "INACTIVE").contains(normalized)) throw new BillingException("Estado fiscal invalido");
        return normalized;
    }

    private static String normalizeStatusFilter(String status) {
        return status == null || status.isBlank() ? null : normalizeStatus(status);
    }

    private static String normalizeDocumentStatus(String status) {
        if (status == null || status.isBlank()) return null;
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!List.of("DRAFT", "READY", "STAMP_PENDING", "STAMPED", "CANCEL_PENDING", "CANCELLED", "ERROR").contains(normalized)) {
            throw new BillingException("Estado de documento fiscal invalido");
        }
        return normalized;
    }

    private static CreateIssuerProfileCommand normalize(CreateIssuerProfileCommand command) {
        if (command == null) return null;
        return new CreateIssuerProfileCommand(command.branchId(), upper(command.rfc()), trim(command.legalName()),
            trim(command.postalCode()), upper(command.fiscalRegimeCode()), upperToNull(command.defaultSeries()));
    }

    private static UpdateIssuerProfileCommand normalize(UpdateIssuerProfileCommand command) {
        if (command == null) return null;
        return new UpdateIssuerProfileCommand(command.issuerProfileId(), command.branchId(), upper(command.rfc()),
            trim(command.legalName()), trim(command.postalCode()), upper(command.fiscalRegimeCode()), upperToNull(command.defaultSeries()));
    }

    private static CreateFiscalProfileCommand normalize(CreateFiscalProfileCommand command) {
        if (command == null) return null;
        return new CreateFiscalProfileCommand(command.customerId(), upper(command.rfc()), trim(command.legalName()),
            trim(command.postalCode()), upper(command.fiscalRegimeCode()), upperToNull(command.cfdiUseCode()), lowerToNull(command.email()));
    }

    private static UpdateFiscalProfileCommand normalize(UpdateFiscalProfileCommand command) {
        if (command == null) return null;
        return new UpdateFiscalProfileCommand(command.fiscalProfileId(), command.customerId(), upper(command.rfc()),
            trim(command.legalName()), trim(command.postalCode()), upper(command.fiscalRegimeCode()), upperToNull(command.cfdiUseCode()), lowerToNull(command.email()));
    }

    private static Map<String, Object> issuerState(IssuerProfile value) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("branchId", value.branchId());
        state.put("rfc", value.rfc());
        state.put("legalName", value.legalName());
        state.put("status", value.status());
        return state;
    }

    private static Map<String, Object> fiscalState(FiscalProfile value) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("customerId", value.customerId());
        state.put("rfc", value.rfc());
        state.put("legalName", value.legalName());
        state.put("status", value.status());
        return state;
    }

    private void audit(String eventType, String aggregateType, UUID aggregateId, Map<String, Object> before, Map<String, Object> after) {
        auditPort.record(new BusinessAuditEvent(eventType, aggregateType, aggregateId, before, after, Map.of()));
    }

    private static String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) throw new BillingException(message);
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String upperToNull(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String lowerToNull(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
