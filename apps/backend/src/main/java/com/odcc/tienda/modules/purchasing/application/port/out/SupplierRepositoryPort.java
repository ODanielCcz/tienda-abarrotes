package com.odcc.tienda.modules.purchasing.application.port.out;

import com.odcc.tienda.modules.purchasing.application.model.Supplier;
import com.odcc.tienda.modules.purchasing.application.query.ListSuppliersQuery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierRepositoryPort {
    boolean existsByCode(String supplierCode);

    boolean existsByCodeAndIdNot(String supplierCode, UUID supplierId);

    Supplier save(Supplier supplier);

    Optional<Supplier> findById(UUID supplierId);

    List<Supplier> findAll(ListSuppliersQuery query);
}
