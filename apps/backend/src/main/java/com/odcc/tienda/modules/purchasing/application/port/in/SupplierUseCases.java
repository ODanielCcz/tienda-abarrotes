package com.odcc.tienda.modules.purchasing.application.port.in;

import com.odcc.tienda.modules.purchasing.application.command.ChangeSupplierStatusCommand;
import com.odcc.tienda.modules.purchasing.application.command.CreateSupplierCommand;
import com.odcc.tienda.modules.purchasing.application.command.UpdateSupplierCommand;
import com.odcc.tienda.modules.purchasing.application.model.Supplier;
import com.odcc.tienda.modules.purchasing.application.query.ListSuppliersQuery;

import java.util.List;
import java.util.UUID;

public interface SupplierUseCases {
    Supplier create(CreateSupplierCommand command, UUID actorUserId);

    Supplier getById(UUID supplierId, UUID actorUserId);

    List<Supplier> list(ListSuppliersQuery query, UUID actorUserId);

    Supplier update(UpdateSupplierCommand command, UUID actorUserId);

    Supplier changeStatus(ChangeSupplierStatusCommand command, UUID actorUserId);
}
