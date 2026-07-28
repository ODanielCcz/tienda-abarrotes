package com.odcc.tienda.modules.organization.application.command;

import com.odcc.tienda.modules.organization.domain.model.CashRegisterStatus;

import java.util.UUID;

public final class CashRegisterCommands {
    private CashRegisterCommands() {
    }

    public record CreateCashRegisterCommand(UUID branchId, UUID deviceId, String code, String name) {
    }

    public record UpdateCashRegisterCommand(UUID cashRegisterId, UUID branchId, UUID deviceId, String code, String name) {
    }

    public record ChangeCashRegisterStatusCommand(UUID cashRegisterId, CashRegisterStatus status) {
    }
}