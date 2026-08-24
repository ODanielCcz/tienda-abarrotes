# Lombok and MapStruct Standardization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Standardize Lombok and MapStruct throughout the Spring Boot backend, remove repetitive structural code, and enforce architectural boundaries without changing business behavior or public API contracts.

**Architecture:** Lombok will generate dependency constructors and safe boilerplate only where semantics are trivial. MapStruct will live exclusively in inbound/outbound adapters and will map transport or persistence structures without performing business decisions. Domain factories, invariant checks, authenticated context, route identifiers, normalization, and transaction boundaries remain explicit.

**Tech Stack:** Java 21, Spring Boot, Gradle Kotlin DSL, Lombok, MapStruct, ArchUnit, JUnit 5, PostgreSQL/Testcontainers, Docker Compose.

**Spec:** `docs/superpowers/specs/2026-08-23-lombok-mapstruct-standardization-design.md`

## Global Constraints

- Work only on branch `refactor/standardize-lombok-mapstruct`; do not use `codex` in branch names.
- Do not change endpoints, JSON field names, validation messages, HTTP statuses, database schema, transaction boundaries, security rules, or business calculations.
- Do not use `@Data` on domain models or persistence entities.
- Do not add public setters to domain aggregates.
- Keep explicit constructors/factories in `Brand`, `Category`, `Product`, and `ProductPresentation`.
- Keep explicit constructors that validate/copy input or derive state, including `RateLimitKeyEncoder`, `InMemoryLoginRateLimiter`, and `ApiPayloadSizeFilter`.
- Keep MapStruct imports out of `domain` and `application` packages.
- Pass route IDs, authenticated user IDs, and request context explicitly to mapper methods.
- Keep business normalization in domain/application services.
- Execute every build command from `apps/backend` using `./gradlew.bat` or `.\gradlew.bat` on Windows.

---

## Task 1: Establish Shared Lombok and MapStruct Conventions

**Files:**

- Create: `apps/backend/lombok.config`
- Create: `apps/backend/src/main/java/com/odcc/tienda/shared/infrastructure/mapping/CentralMapperConfig.java`
- Modify: `apps/backend/src/test/java/com/odcc/tienda/architecture/HexagonalArchitectureTest.java`

- [ ] **Step 1: Add failing architecture rules**

Add rules to `HexagonalArchitectureTest` that prohibit MapStruct in domain/application and field injection in production classes:

```java
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;

@ArchTest
static final ArchRule domainAndApplicationMustNotUseMapStruct = noClasses()
    .that()
    .resideInAnyPackage("..domain..", "..application..")
    .should()
    .dependOnClassesThat()
    .resideInAPackage("org.mapstruct..");

@ArchTest
static final ArchRule productionFieldsMustNotUseAutowired = fields()
    .that()
    .areDeclaredInClassesThat()
    .resideInAPackage("com.odcc.tienda..")
    .should()
    .notBeAnnotatedWith(org.springframework.beans.factory.annotation.Autowired.class);
```

Run:

```powershell
cd apps/backend
.\gradlew.bat test --tests com.odcc.tienda.architecture.HexagonalArchitectureTest --no-daemon
```

Expected: PASS against the current code and establish the regression guard before refactoring.

- [ ] **Step 2: Add deterministic Lombok configuration**

Create `apps/backend/lombok.config`:

```properties
config.stopBubbling = true
lombok.addLombokGeneratedAnnotation = true
lombok.copyableAnnotations += org.springframework.beans.factory.annotation.Qualifier
```

Do not enable global fluent accessors, chained setters, or automatic null checks because they would alter existing conventions and runtime behavior.

- [ ] **Step 3: Add central MapStruct configuration**

Create `CentralMapperConfig.java`:

```java
package com.odcc.tienda.shared.infrastructure.mapping;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.MapperConfig;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@MapperConfig(
    componentModel = MappingConstants.ComponentModel.SPRING,
    injectionStrategy = InjectionStrategy.CONSTRUCTOR,
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface CentralMapperConfig {
}
```

- [ ] **Step 4: Verify annotation processing**

Run:

```powershell
.\gradlew.bat clean compileJava --no-daemon
.\gradlew.bat test --tests com.odcc.tienda.architecture.HexagonalArchitectureTest --no-daemon
```

Expected: generated MapStruct implementations compile and architecture rules pass.

- [ ] **Step 5: Commit the convention foundation**

```powershell
git add lombok.config src/main/java/com/odcc/tienda/shared/infrastructure/mapping/CentralMapperConfig.java src/test/java/com/odcc/tienda/architecture/HexagonalArchitectureTest.java
git commit -m "refactor(backend): define Lombok and mapper conventions"
```

---

## Task 2: Migrate Existing Mappers to the Central Configuration

**Files:**

- Modify: `apps/backend/src/main/java/com/odcc/tienda/modules/catalog/adapter/in/rest/mapper/BrandRestMapper.java`
- Modify: `apps/backend/src/main/java/com/odcc/tienda/modules/catalog/adapter/in/rest/mapper/CategoryRestMapper.java`
- Modify: `apps/backend/src/main/java/com/odcc/tienda/modules/catalog/adapter/in/rest/mapper/ProductRestMapper.java`
- Modify: `apps/backend/src/main/java/com/odcc/tienda/modules/catalog/adapter/in/rest/mapper/ProductPresentationRestMapper.java`
- Modify: `apps/backend/src/main/java/com/odcc/tienda/modules/catalog/adapter/out/persistence/mapper/BrandPersistenceMapper.java`
- Modify: `apps/backend/src/main/java/com/odcc/tienda/modules/catalog/adapter/out/persistence/mapper/CategoryPersistenceMapper.java`
- Modify: `apps/backend/src/main/java/com/odcc/tienda/modules/catalog/adapter/out/persistence/mapper/ProductPersistenceMapper.java`
- Modify: `apps/backend/src/main/java/com/odcc/tienda/modules/identity/adapter/in/rest/mapper/AuthenticationRestMapper.java`
- Create: `apps/backend/src/test/java/com/odcc/tienda/architecture/MapperConfigurationArchitectureTest.java`

- [ ] **Step 1: Add a mapper convention test**

Create a small ArchUnit/reflection test that discovers every interface annotated with `@Mapper` and asserts that `config()` equals `CentralMapperConfig.class`. This test should fail until the eight current mappers are migrated.

- [ ] **Step 2: Replace repeated mapper options**

Change each mapper from:

```java
@Mapper(
    componentModel = SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
```

to:

```java
@Mapper(config = CentralMapperConfig.class)
```

Remove obsolete `ReportingPolicy` and static `SPRING` imports. Keep all existing `@Mapping` declarations and default/manual domain reconstruction methods unchanged.

- [ ] **Step 3: Compile generated implementations**

```powershell
.\gradlew.bat clean compileJava --no-daemon
.\gradlew.bat test --tests com.odcc.tienda.architecture.MapperConfigurationArchitectureTest --no-daemon
.\gradlew.bat test --tests "com.odcc.tienda.modules.catalog.*" --no-daemon
```

Expected: all eight `*MapperImpl` classes are generated and behavior remains unchanged.

- [ ] **Step 4: Commit existing mapper migration**

```powershell
git add src/main/java/com/odcc/tienda/modules/catalog src/main/java/com/odcc/tienda/modules/identity/adapter/in/rest/mapper src/test/java/com/odcc/tienda/architecture/MapperConfigurationArchitectureTest.java
git commit -m "refactor(mapping): centralize existing mapper configuration"
```

---

## Task 3: Simplify Only Trivial Dependency Constructors with Lombok

**Files:**

- Modify: `apps/backend/src/main/java/com/odcc/tienda/modules/identity/adapter/out/security/RedisLoginRateLimiter.java`
- Modify: `apps/backend/src/main/java/com/odcc/tienda/modules/identity/adapter/out/security/LoginRateLimitMetrics.java`
- Test: existing identity rate-limit tests under `apps/backend/src/test/java/com/odcc/tienda/modules/identity`

- [ ] **Step 1: Capture constructor behavior with focused tests**

Run the existing rate-limiter suite before editing:

```powershell
.\gradlew.bat test --tests "com.odcc.tienda.modules.identity.*RateLimit*" --no-daemon
```

Expected: PASS; this is the behavioral baseline.

- [ ] **Step 2: Replace trivial constructors**

Add `@RequiredArgsConstructor` to both final classes and delete only constructors that assign parameters directly to final fields:

```java
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class RedisLoginRateLimiter implements LoginRateLimitPort {
    // existing final fields and behavior
}
```

Apply the same pattern to `LoginRateLimitMetrics`.

Do not alter explicit constructors in classes that validate, copy, normalize, or derive values.

- [ ] **Step 3: Verify identity behavior**

```powershell
.\gradlew.bat compileJava --no-daemon
.\gradlew.bat test --tests "com.odcc.tienda.modules.identity.*" --no-daemon
```

- [ ] **Step 4: Commit Lombok cleanup**

```powershell
git add src/main/java/com/odcc/tienda/modules/identity/adapter/out/security/RedisLoginRateLimiter.java src/main/java/com/odcc/tienda/modules/identity/adapter/out/security/LoginRateLimitMetrics.java
git commit -m "refactor(identity): simplify dependency constructors"
```

---

## Task 4: Generate Structural Inventory REST Mappings

**Files:**

- Create: `apps/backend/src/main/java/com/odcc/tienda/modules/inventory/adapter/in/rest/mapper/AdvancedInventoryRestMapper.java`
- Create: `apps/backend/src/main/java/com/odcc/tienda/modules/inventory/adapter/in/rest/mapper/InventoryReceiptRestMapper.java`
- Modify: `apps/backend/src/main/java/com/odcc/tienda/modules/inventory/adapter/in/rest/AdvancedInventoryController.java`
- Modify: `apps/backend/src/main/java/com/odcc/tienda/modules/inventory/adapter/in/rest/InventoryReceiptController.java`
- Create: `apps/backend/src/test/java/com/odcc/tienda/modules/inventory/adapter/in/rest/mapper/AdvancedInventoryRestMapperTest.java`
- Create: `apps/backend/src/test/java/com/odcc/tienda/modules/inventory/adapter/in/rest/mapper/InventoryReceiptRestMapperTest.java`
- Test: `apps/backend/src/test/java/com/odcc/tienda/modules/inventory/adapter/in/rest/AdvancedInventoryApiIntegrationTest.java`

- [ ] **Step 1: Write mapper tests for nested items**

Instantiate generated mappers with `Mappers.getMapper(...)` and assert all request fields survive mapping. Include empty lists, one nested item, route IDs, and actor IDs. Tests must fail before mapper interfaces exist.

- [ ] **Step 2: Add inventory mapper interfaces**

Use the central config and explicit parameters for context:

```java
@Mapper(config = CentralMapperConfig.class)
public interface AdvancedInventoryRestMapper {

    InventoryAdjustmentItemCommand toCommand(InventoryAdjustmentItemRequest request);

    InventoryTransferItemCommand toCommand(InventoryTransferItemRequest request);

    InventoryCountItemCommand toCommand(InventoryCountItemRequest request);

    ReservationItemCommand toCommand(ReservationItemRequest request);

    @Mapping(target = "actorUserId", source = "actorUserId")
    CreateInventoryAdjustmentCommand toCommand(
        CreateInventoryAdjustmentRequest request,
        UUID actorUserId
    );

    // Equivalent explicit-context methods for transfer, count,
    // reservation, confirmation, and release commands.
}
```

For receipts:

```java
@Mapper(config = CentralMapperConfig.class)
public interface InventoryReceiptRestMapper {

    InventoryReceiptItemCommand toCommand(InventoryReceiptItemRequest request);

    InventoryReceiptPalletCommand toCommand(InventoryReceiptPalletRequest request);

    @Mapping(target = "actorUserId", source = "actorUserId")
    CreateInventoryReceiptCommand toCommand(
        CreateInventoryReceiptRequest request,
        UUID actorUserId
    );
}
```

Use `default` methods only to preserve the current `null -> List.of()` semantics; do not invent normalization.

- [ ] **Step 3: Inject mappers into controllers**

Keep `@RequiredArgsConstructor`, add final mapper fields, replace `new ...Command(...)` and stream helper methods with mapper calls, and delete only superseded private mapping helpers. JWT-to-UUID extraction stays explicit.

- [ ] **Step 4: Verify inventory contracts**

```powershell
.\gradlew.bat test --tests "com.odcc.tienda.modules.inventory.adapter.in.rest.mapper.*" --no-daemon
.\gradlew.bat test --tests com.odcc.tienda.modules.inventory.adapter.in.rest.AdvancedInventoryApiIntegrationTest --no-daemon
.\gradlew.bat compileJava --no-daemon
```

- [ ] **Step 5: Commit inventory mapping**

```powershell
git add src/main/java/com/odcc/tienda/modules/inventory/adapter/in/rest src/test/java/com/odcc/tienda/modules/inventory/adapter/in/rest
git commit -m "refactor(inventory): generate REST command mappings"
```

---

## Task 5: Generate Purchasing REST Mappings

**Files:**

- Create: `apps/backend/src/main/java/com/odcc/tienda/modules/purchasing/adapter/in/rest/mapper/PurchaseRestMapper.java`
- Create: `apps/backend/src/main/java/com/odcc/tienda/modules/purchasing/adapter/in/rest/mapper/SupplierRestMapper.java`
- Modify: `apps/backend/src/main/java/com/odcc/tienda/modules/purchasing/adapter/in/rest/PurchaseController.java`
- Modify: `apps/backend/src/main/java/com/odcc/tienda/modules/purchasing/adapter/in/rest/SupplierController.java`
- Create: `apps/backend/src/test/java/com/odcc/tienda/modules/purchasing/adapter/in/rest/mapper/PurchaseRestMapperTest.java`
- Create: `apps/backend/src/test/java/com/odcc/tienda/modules/purchasing/adapter/in/rest/mapper/SupplierRestMapperTest.java`
- Test: `apps/backend/src/test/java/com/odcc/tienda/modules/purchasing/application/usecase/PurchaseServiceTest.java`

- [ ] **Step 1: Write failing mapping tests**

Cover create-purchase items, receipt items, receipt pallets, supplier create/update/status, explicit `purchaseId`, and `null -> empty list` behavior.

- [ ] **Step 2: Implement mapper interfaces**

`PurchaseRestMapper` maps:

```text
CreatePurchaseItemRequest -> CreatePurchaseItemCommand
CreatePurchaseRequest + actorUserId -> CreatePurchaseCommand
ReceivePurchaseItemRequest -> ReceivePurchaseItemCommand
ReceivePurchasePalletRequest -> ReceivePurchasePalletCommand
ReceivePurchaseRequest + purchaseId -> ReceivePurchaseCommand
```

`SupplierRestMapper` maps create/update/status requests, receiving `supplierId` explicitly for update/status commands.

- [ ] **Step 3: Replace controller constructors and helper streams**

Inject the mappers through Lombok-generated constructors. Preserve current-user extraction and use-case invocation order.

- [ ] **Step 4: Verify purchasing behavior**

```powershell
.\gradlew.bat test --tests "com.odcc.tienda.modules.purchasing.adapter.in.rest.mapper.*" --no-daemon
.\gradlew.bat test --tests com.odcc.tienda.modules.purchasing.application.usecase.PurchaseServiceTest --no-daemon
.\gradlew.bat compileJava --no-daemon
```

- [ ] **Step 5: Commit purchasing mapping**

```powershell
git add src/main/java/com/odcc/tienda/modules/purchasing/adapter/in/rest src/test/java/com/odcc/tienda/modules/purchasing/adapter/in/rest
git commit -m "refactor(purchasing): generate REST command mappings"
```

---

## Task 6: Generate Organization and Billing REST Mappings

**Files:**

- Create: `apps/backend/src/main/java/com/odcc/tienda/modules/organization/adapter/in/rest/mapper/OrganizationRestMapper.java`
- Modify: `apps/backend/src/main/java/com/odcc/tienda/modules/organization/adapter/in/rest/OrganizationController.java`
- Create: `apps/backend/src/test/java/com/odcc/tienda/modules/organization/adapter/in/rest/mapper/OrganizationRestMapperTest.java`
- Create: `apps/backend/src/main/java/com/odcc/tienda/modules/billing/adapter/in/rest/mapper/BillingRestMapper.java`
- Create: `apps/backend/src/main/java/com/odcc/tienda/modules/billing/adapter/in/rest/mapper/CatalogFiscalClassificationRestMapper.java`
- Modify: `apps/backend/src/main/java/com/odcc/tienda/modules/billing/adapter/in/rest/BillingController.java`
- Modify: `apps/backend/src/main/java/com/odcc/tienda/modules/billing/adapter/in/rest/CatalogFiscalClassificationController.java`
- Create: `apps/backend/src/test/java/com/odcc/tienda/modules/billing/adapter/in/rest/mapper/BillingRestMapperTest.java`
- Test: `apps/backend/src/test/java/com/odcc/tienda/modules/organization/adapter/in/rest/OrganizationApiIntegrationTest.java`
- Test: `apps/backend/src/test/java/com/odcc/tienda/modules/billing/adapter/in/rest/BillingApiIntegrationTest.java`

- [ ] **Step 1: Add failing mapper tests**

Cover all create/update/status requests for branches, warehouses, cash registers, devices, issuer profiles, fiscal profiles, documents, and catalog fiscal classification. Assert path IDs and actor IDs override no request fields.

- [ ] **Step 2: Implement organization mappings**

Use overloaded methods with explicit route ID and actor ID. Do not map list filters that currently contain defaulting/business rules; leave those query constructions explicit in the controller or application service.

- [ ] **Step 3: Implement billing mappings**

Map structural profile/document/classification requests only. Keep RFC/SAT validation, ownership validation, sales snapshots, and status transitions in application/domain code.

- [ ] **Step 4: Refactor controllers and verify**

```powershell
.\gradlew.bat test --tests "com.odcc.tienda.modules.organization.adapter.in.rest.mapper.*" --no-daemon
.\gradlew.bat test --tests com.odcc.tienda.modules.organization.adapter.in.rest.OrganizationApiIntegrationTest --no-daemon
.\gradlew.bat test --tests "com.odcc.tienda.modules.billing.adapter.in.rest.mapper.*" --no-daemon
.\gradlew.bat test --tests com.odcc.tienda.modules.billing.adapter.in.rest.BillingApiIntegrationTest --no-daemon
```

- [ ] **Step 5: Commit administrative mapping**

```powershell
git add src/main/java/com/odcc/tienda/modules/organization/adapter/in/rest src/test/java/com/odcc/tienda/modules/organization/adapter/in/rest src/main/java/com/odcc/tienda/modules/billing/adapter/in/rest src/test/java/com/odcc/tienda/modules/billing/adapter/in/rest
git commit -m "refactor(admin): generate organization and billing mappings"
```

---

## Task 7: Generate Sales and Cash REST Mappings

**Files:**

- Create: `apps/backend/src/main/java/com/odcc/tienda/modules/sales/adapter/in/rest/mapper/CustomerRestMapper.java`
- Create: `apps/backend/src/main/java/com/odcc/tienda/modules/sales/adapter/in/rest/mapper/SalesOrderRestMapper.java`
- Create: `apps/backend/src/main/java/com/odcc/tienda/modules/sales/adapter/in/rest/mapper/SalesPaymentRestMapper.java`
- Create: `apps/backend/src/main/java/com/odcc/tienda/modules/sales/adapter/in/rest/mapper/SalesReturnRestMapper.java`
- Modify: `apps/backend/src/main/java/com/odcc/tienda/modules/sales/adapter/in/rest/CustomerController.java`
- Modify: `apps/backend/src/main/java/com/odcc/tienda/modules/sales/adapter/in/rest/SalesOrderController.java`
- Modify: `apps/backend/src/main/java/com/odcc/tienda/modules/sales/adapter/in/rest/SalesPaymentController.java`
- Modify: `apps/backend/src/main/java/com/odcc/tienda/modules/sales/adapter/in/rest/SalesReturnController.java`
- Create: `apps/backend/src/main/java/com/odcc/tienda/modules/cash/adapter/in/rest/mapper/CashSessionRestMapper.java`
- Modify: `apps/backend/src/main/java/com/odcc/tienda/modules/cash/adapter/in/rest/CashSessionController.java`
- Create tests under the corresponding `adapter/in/rest/mapper` test packages.
- Test: `apps/backend/src/test/java/com/odcc/tienda/modules/sales/adapter/in/rest/CustomerApiIntegrationTest.java`
- Test: `apps/backend/src/test/java/com/odcc/tienda/modules/sales/adapter/in/rest/SalesReturnApiIntegrationTest.java`
- Test: `apps/backend/src/test/java/com/odcc/tienda/modules/cash/adapter/in/rest/CashAndPaymentsApiIntegrationTest.java`

- [ ] **Step 1: Write failing sales/cash mapper tests**

Cover nested sales items and return items, payment route IDs, return route IDs, customer update IDs, cash session IDs, and actor IDs. Verify price and discount values are copied as request expectations only; pricing authority remains in the sales use case.

- [ ] **Step 2: Add mappers with explicit context**

Representative signature:

```java
@Mapper(config = CentralMapperConfig.class)
public interface SalesOrderRestMapper {

    CreateSalesOrderItemCommand toCommand(CreateSalesOrderItemRequest request);

    @Mapping(target = "actorUserId", source = "actorUserId")
    CreateSalesOrderCommand toCommand(
        CreateSalesOrderRequest request,
        UUID actorUserId
    );
}
```

Use equivalent explicit signatures for `salesOrderId`, `returnId`, `paymentId`, `cashSessionId`, and actor IDs. Do not map JWT objects.

- [ ] **Step 3: Refactor controllers**

Inject mappers with final fields and `@RequiredArgsConstructor`. Keep response envelopes, HTTP statuses, correlation IDs, security annotations, and current-user extraction unchanged.

- [ ] **Step 4: Verify operational flows**

```powershell
.\gradlew.bat test --tests "com.odcc.tienda.modules.sales.adapter.in.rest.mapper.*" --no-daemon
.\gradlew.bat test --tests com.odcc.tienda.modules.sales.adapter.in.rest.CustomerApiIntegrationTest --no-daemon
.\gradlew.bat test --tests com.odcc.tienda.modules.sales.adapter.in.rest.SalesReturnApiIntegrationTest --no-daemon
.\gradlew.bat test --tests "com.odcc.tienda.modules.cash.adapter.in.rest.mapper.*" --no-daemon
.\gradlew.bat test --tests com.odcc.tienda.modules.cash.adapter.in.rest.CashAndPaymentsApiIntegrationTest --no-daemon
```

- [ ] **Step 5: Commit sales/cash mapping**

```powershell
git add src/main/java/com/odcc/tienda/modules/sales/adapter/in/rest src/test/java/com/odcc/tienda/modules/sales/adapter/in/rest src/main/java/com/odcc/tienda/modules/cash/adapter/in/rest src/test/java/com/odcc/tienda/modules/cash/adapter/in/rest
git commit -m "refactor(sales): generate sales and cash REST mappings"
```

---

## Task 8: Limit Sync Mapping to Structural Fields

**Files:**

- Create: `apps/backend/src/main/java/com/odcc/tienda/modules/sync/adapter/in/rest/mapper/SyncRestMapper.java`
- Modify: `apps/backend/src/main/java/com/odcc/tienda/modules/sync/adapter/in/rest/SyncController.java`
- Create: `apps/backend/src/test/java/com/odcc/tienda/modules/sync/adapter/in/rest/mapper/SyncRestMapperTest.java`
- Test: `apps/backend/src/test/java/com/odcc/tienda/modules/sync/adapter/in/rest/SyncApiIntegrationTest.java`
- Test: `apps/backend/src/test/java/com/odcc/tienda/modules/sync/application/usecase/SyncServicePayloadValidationTest.java`

- [ ] **Step 1: Write failing structural mapping tests**

Assert that envelope fields map exactly and authenticated user ID is supplied explicitly. Include payload preservation as a `JsonNode` without transforming its contents.

- [ ] **Step 2: Add `SyncRestMapper`**

Map only:

```text
IngestOperationRequest + actorUserId -> IngestOperationCommand
AcknowledgeCheckpointRequest + deviceId + actorUserId -> AcknowledgeCheckpointCommand
ResolveConflictRequest + conflictId + actorUserId -> ResolveConflictCommand
```

Do not move payload depth, size, operation-type, sequence, idempotency, or conflict rules into the mapper.

- [ ] **Step 3: Refactor controller and verify Sync**

```powershell
.\gradlew.bat test --tests com.odcc.tienda.modules.sync.adapter.in.rest.mapper.SyncRestMapperTest --no-daemon
.\gradlew.bat test --tests com.odcc.tienda.modules.sync.application.usecase.SyncServicePayloadValidationTest --no-daemon
.\gradlew.bat test --tests com.odcc.tienda.modules.sync.adapter.in.rest.SyncApiIntegrationTest --no-daemon
```

- [ ] **Step 4: Commit Sync mapping**

```powershell
git add src/main/java/com/odcc/tienda/modules/sync/adapter/in/rest src/test/java/com/odcc/tienda/modules/sync/adapter/in/rest
git commit -m "refactor(sync): generate structural REST mappings"
```

---

## Task 9: Final Audit, Full Verification, and Documentation

**Files:**

- Modify if required: `docs/superpowers/specs/2026-08-23-lombok-mapstruct-standardization-design.md`
- Modify if present and relevant: `docs/architecture/*`
- No production changes unless a failing verification exposes an actual regression.

- [ ] **Step 1: Audit annotation usage**

Run:

```powershell
rg -n "@(Data|Setter|Autowired)" src/main/java
rg -n "org\.mapstruct" src/main/java/com/odcc/tienda/modules/*/domain src/main/java/com/odcc/tienda/modules/*/application
rg -n "new [A-Z][A-Za-z0-9]*(Command|Response)\(" src/main/java/com/odcc/tienda/modules/*/adapter/in/rest
```

Expected:

- No `@Data`.
- No field-level `@Autowired`.
- No MapStruct dependencies in domain/application.
- Remaining manual command creation is documented as contextual/business mapping, not accidental boilerplate.

- [ ] **Step 2: Run architecture and clean build checks**

```powershell
.\gradlew.bat clean compileJava --no-daemon
.\gradlew.bat test --tests com.odcc.tienda.architecture.HexagonalArchitectureTest --tests com.odcc.tienda.architecture.MapperConfigurationArchitectureTest --no-daemon
.\gradlew.bat test --no-daemon
.\gradlew.bat bootJar --no-daemon
```

Expected: every command exits `0`.

- [ ] **Step 3: Verify generated implementations**

```powershell
Get-ChildItem -Recurse build\generated\sources\annotationProcessor\java\main -Filter "*MapperImpl.java" | Select-Object -ExpandProperty FullName
```

Expected: implementations exist for the original eight mappers and every new adapter mapper.

- [ ] **Step 4: Build the Docker image**

From repository root:

```powershell
docker compose build backend
docker compose up -d database redis backend
docker compose ps
docker compose logs backend --tail=100
```

Expected: backend reaches a healthy/running state with no bean ambiguity or mapper injection errors.

- [ ] **Step 5: Smoke-test API compatibility**

Use the existing local login and representative catalog, inventory, purchasing, sales, cash, organization, billing, and Sync endpoints. Compare status, `code`, `reason`, field names, and `correlationId` with the pre-refactor contract. No JSON contract changes are acceptable.

- [ ] **Step 6: Check the patch**

```powershell
git diff --check origin/main...HEAD
git status --short
git log --oneline --decorate origin/main..HEAD
```

Expected: no whitespace errors, no untracked implementation files, and commits remain logically separated.

- [ ] **Step 7: Commit documentation corrections only if needed**

```powershell
git add docs
git commit -m "docs(backend): record Lombok and MapStruct conventions"
```

Skip this commit if the approved design spec already contains the final rules and no documentation changed.

## Completion Criteria

- Existing 98 valid `@RequiredArgsConstructor` usages remain correct.
- Trivial dependency constructors are generated; invariant-bearing constructors remain explicit.
- Every MapStruct mapper uses `CentralMapperConfig`.
- New MapStruct mappings exist only where they produce a net readability gain.
- No mapper contains business calculations, authorization, transaction logic, validation, or normalization.
- Domain and application remain independent of Lombok-generated framework behavior and MapStruct APIs.
- Full tests, `bootJar`, and Docker backend build pass.
- Public REST behavior is unchanged.
