# RC1 Operational Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Validate and document a secure Backend RC1, provision Sync ownership safely, automate quality gates, and publish the validated candidate without touching web or mobile applications.

**Architecture:** The backend stays unchanged except for deterministic container provenance. Operational scripts and documentation are separated from schema migrations: `V034` remains schema-only and a local ignored CSV provides approved user-device assignments. GitHub Actions reproduces the quality gates against an isolated PostgreSQL stack and scans the built image before publication.

**Tech Stack:** Java 21, Gradle Wrapper, Spring Boot, PostgreSQL 18, Flyway 12, Docker Compose, GitHub Actions, Trivy, PowerShell 7.

**Spec:** Chat-approved 2026-08-18 RC1 closure plan (points 1–4).

## Global Constraints

- Do not touch `apps/web` or `apps/mobile`.
- Do not execute `Flyway clean` against the working local database.
- Keep real secrets and device-user mappings in ignored local files only.
- Do not create branches whose name contains `codex`.
- Keep the database schema at migrations `V001` through `V034`.

---

### Task 1: Establish repeatable RC1 validation

**Files:**
- Create: `docs/operations/rc1-validation.md`
- Modify: `PROJECT.md`, `docs/releases/backend-v1.0.0-rc1.md`, `docs/operations/backend-mvp.md`

- [ ] Create an isolated Compose project and apply Flyway V001–V034.
- [ ] Verify `flyway_schema_history` ends at version `034`.
- [ ] Run Gradle tests, bootJar, Docker build and actuator health smoke check.
- [ ] Record only commands and evidence, never local credentials or database values.

### Task 2: Make Sync device provisioning operational

**Files:**
- Create: `scripts/provision-sync-device-bindings.ps1`
- Create: `docs/operations/sync-device-provisioning.md`
- Modify: `docs/api/postman/Tienda-Abarrotes-Local.postman_environment.json`, `docs/api/postman/Tienda-Abarrotes-MVP.postman_collection.json`

- [ ] Require an explicit ignored CSV mapping and support PowerShell `-WhatIf`.
- [ ] Validate active device, active user and active branch assignment before inserting.
- [ ] Use an idempotent upsert only after validation succeeds.
- [ ] Document owner-only Sync operation checks and the Postman preconditions.

### Task 3: Add reproducible CI quality gates

**Files:**
- Create: `.github/workflows/backend-ci.yml`, `.github/dependabot.yml`, `docs/operations/ci-cd.md`
- Modify: `deploy/backend/Dockerfile`, `.env.example`

- [ ] Pin the Java runtime base image by its verified digest.
- [ ] Run Gradle tests, bootJar, isolated migration validation, image build and Trivy image scan.
- [ ] Publish reports and apply least-privilege GitHub token permissions.
- [ ] Schedule dependency scanning and keep workflow dependencies current.

### Task 4: Reconcile public API and release records

**Files:**
- Modify: `README.md`, `apps/backend/README.md`, `docs/api/backend-api-v1.md`, `PROJECT.md`, `docs/releases/backend-v1.0.0-rc1.md`

- [ ] Document `reason` and `correlationId` in the standard response.
- [ ] Declare OpenAPI `/v3/api-docs` as the executable route reference.
- [ ] List all implemented module groups and point Postman users to the Sync binding requirement.
- [ ] Mark the release candidate only after the validation and CI criteria pass.

### Task 5: Publish the candidate safely

**Files:**
- Modify: Git history only after all validations pass.

- [ ] Fetch and compare `origin/main` before pushing.
- [ ] Push the verified commits without force push.
- [ ] Create annotated tag `backend-v1.0.0-rc1` only after remote CI is green.
