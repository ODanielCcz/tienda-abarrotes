package com.odcc.tienda.modules.organization.adapter.in.rest.request;

import com.odcc.tienda.modules.organization.domain.model.CashRegisterStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class CashRegisterRequests {
    private CashRegisterRequests() {
    }

    public record CreateCashRegisterRequest(@NotNull UUID branchId, UUID deviceId, @NotBlank @Size(max = 50) String code, @NotBlank @Size(max = 150) String name) {
    }

    public record UpdateCashRegisterRequest(@NotNull UUID branchId, UUID deviceId, @NotBlank @Size(max = 50) String code, @NotBlank @Size(max = 150) String name) {
    }

    public record ChangeCashRegisterStatusRequest(@NotNull CashRegisterStatus status) {
    }
}