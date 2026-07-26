package com.odcc.tienda.shared.support;

import com.odcc.tienda.shared.application.transaction.TransactionRunner;

import java.util.function.Supplier;

public final class ImmediateTransactionRunner implements TransactionRunner {

    private int executionCount;

    @Override
    public <T> T required(Supplier<T> operation) {
        executionCount++;
        return operation.get();
    }

    public int executionCount() {
        return executionCount;
    }
}
