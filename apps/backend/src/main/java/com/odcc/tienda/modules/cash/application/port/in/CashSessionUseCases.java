package com.odcc.tienda.modules.cash.application.port.in;

import com.odcc.tienda.modules.cash.application.command.CloseCashSessionCommand;
import com.odcc.tienda.modules.cash.application.command.CreateCashMovementCommand;
import com.odcc.tienda.modules.cash.application.command.OpenCashSessionCommand;
import com.odcc.tienda.modules.cash.application.model.CashMovement;
import com.odcc.tienda.modules.cash.application.model.CashSession;
import com.odcc.tienda.modules.cash.application.query.ListCashSessionsQuery;

import java.util.List;
import java.util.UUID;

public interface CashSessionUseCases {
    CashSession open(OpenCashSessionCommand command);

    CashSession getById(UUID cashSessionId, UUID actorUserId);

    List<CashSession> list(ListCashSessionsQuery query, UUID actorUserId);

    CashSession close(CloseCashSessionCommand command);

    List<CashMovement> listMovements(UUID cashSessionId, UUID actorUserId);

    CashMovement createMovement(CreateCashMovementCommand command);
}
