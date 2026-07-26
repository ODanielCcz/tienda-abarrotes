package com.odcc.tienda.modules.inventory.application.exception;

import java.util.UUID;

public class InventoryResourceNotFoundException extends RuntimeException {
    public InventoryResourceNotFoundException(String resource, UUID id) {
        super("No existe " + resource + " con id " + id);
    }
}
