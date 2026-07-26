package com.odcc.tienda.modules.purchasing.support;

import com.odcc.tienda.modules.purchasing.application.model.Supplier;
import com.odcc.tienda.modules.purchasing.application.port.out.SupplierRepositoryPort;
import com.odcc.tienda.modules.purchasing.application.query.ListSuppliersQuery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemorySupplierRepository implements SupplierRepositoryPort {

    private final Map<UUID, Supplier> suppliers = new LinkedHashMap<>();

    @Override
    public boolean existsByCode(String supplierCode) {
        return suppliers.values().stream().anyMatch(supplier -> supplier.supplierCode().equals(supplierCode));
    }

    @Override
    public boolean existsByCodeAndIdNot(String supplierCode, UUID supplierId) {
        return suppliers.values().stream().anyMatch(supplier -> supplier.supplierCode().equals(supplierCode) && !supplier.supplierId().equals(supplierId));
    }

    @Override
    public Supplier save(Supplier supplier) {
        suppliers.put(supplier.supplierId(), supplier);
        return supplier;
    }

    @Override
    public Optional<Supplier> findById(UUID supplierId) {
        return Optional.ofNullable(suppliers.get(supplierId));
    }

    @Override
    public List<Supplier> findAll(ListSuppliersQuery query) {
        return new ArrayList<>(suppliers.values());
    }
}
