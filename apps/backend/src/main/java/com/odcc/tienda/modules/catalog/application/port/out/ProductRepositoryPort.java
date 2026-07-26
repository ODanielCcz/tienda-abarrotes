package com.odcc.tienda.modules.catalog.application.port.out;

import com.odcc.tienda.modules.catalog.application.query.ListProductsQuery;
import com.odcc.tienda.modules.catalog.application.query.ProductPage;
import com.odcc.tienda.modules.catalog.domain.model.Product;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepositoryPort {

    Optional<Product> findById(UUID productId);

    ProductPage findAll(ListProductsQuery query);

    Product save(Product product);
}
