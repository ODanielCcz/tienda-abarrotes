package com.odcc.tienda.modules.cash.application.port.out;

import com.odcc.tienda.modules.cash.application.command.CloseCashSessionCommand;
import com.odcc.tienda.modules.cash.application.command.OpenCashSessionCommand;
import com.odcc.tienda.modules.cash.application.model.CashMovement;
import com.odcc.tienda.modules.cash.application.model.CashSession;
import com.odcc.tienda.modules.cash.application.query.ListCashSessionsQuery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CashSessionRepositoryPort {
    CashSession open(OpenCashSessionCommand command);

    Optional<CashSession> findById(UUID cashSessionId);

    List<CashSession> findAll(ListCashSessionsQuery query);

    CashSession close(CloseCashSessionCommand command);

    List<CashMovement> findMovements(UUID cashSessionId);
}
