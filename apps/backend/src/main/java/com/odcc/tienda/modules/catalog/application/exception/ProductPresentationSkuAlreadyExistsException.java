package com.odcc.tienda.modules.catalog.application.exception;

public class ProductPresentationSkuAlreadyExistsException extends RuntimeException {
    public ProductPresentationSkuAlreadyExistsException(String sku) {
        super("Ya existe una presentacion con SKU " + sku);
    }
}
