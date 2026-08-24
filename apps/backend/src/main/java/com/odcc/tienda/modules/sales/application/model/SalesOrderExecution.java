package com.odcc.tienda.modules.sales.application.model;

public record SalesOrderExecution(
    SalesOrder salesOrder,
    SalesOrderCreationOutcome outcome
) {
    public static SalesOrderExecution created(SalesOrder salesOrder) {
        return new SalesOrderExecution(salesOrder, SalesOrderCreationOutcome.CREATED);
    }

    public static SalesOrderExecution replayed(SalesOrder salesOrder) {
        return new SalesOrderExecution(salesOrder, SalesOrderCreationOutcome.REPLAYED);
    }

    public boolean wasCreated() {
        return outcome == SalesOrderCreationOutcome.CREATED;
    }
}
