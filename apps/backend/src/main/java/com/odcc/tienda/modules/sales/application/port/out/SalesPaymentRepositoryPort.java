package com.odcc.tienda.modules.sales.application.port.out;

import com.odcc.tienda.modules.sales.application.command.CreateSalesPaymentCommand;
import com.odcc.tienda.modules.sales.application.model.SalesPayment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalesPaymentRepositoryPort {
    Optional<SalesPayment> findByIdempotencyKey(UUID idempotencyKey, String fingerprint);

    boolean existsByIdempotencyKeyWithDifferentFingerprint(UUID idempotencyKey, String fingerprint);

    SalesPayment createCaptured(CreateSalesPaymentCommand command, String fingerprint);

    List<SalesPayment> findBySalesOrderId(UUID salesOrderId);

    Optional<SalesPayment> findById(UUID paymentId);

    SalesPayment cancel(UUID paymentId, UUID cancelledBy);
}
