package com.odcc.tienda.modules.purchasing.application.port.in;

import com.odcc.tienda.modules.purchasing.application.command.ChangeSupplierStatusCommand;
import com.odcc.tienda.modules.purchasing.application.command.CreateSupplierCommand;
import com.odcc.tienda.modules.purchasing.application.command.UpdateSupplierCommand;
import com.odcc.tienda.modules.purchasing.application.model.Supplier;
import com.odcc.tienda.modules.purchasing.application.query.ListSuppliersQuery;

import java.util.List;
import java.util.UUID;

public interface SupplierUseCases {
    Supplier create(CreateSupplierCommand command);

    Supplier getById(UUID supplierId);

    List<Supplier> list(ListSuppliersQuery query);

    Supplier update(UpdateSupplierCommand command);

    Supplier changeStatus(ChangeSupplierStatusCommand command);
}
