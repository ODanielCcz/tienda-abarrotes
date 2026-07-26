package com.odcc.tienda.shared.infrastructure.transaction;

import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class SpringTransactionRunner implements TransactionRunner {

    private final TransactionTemplate transactionTemplate;

    @Override
    public <T> T required(Supplier<T> operation) {
        Objects.requireNonNull(operation, "La operación transaccional es obligatoria");

        return transactionTemplate.execute(status -> operation.get());
    }
}
