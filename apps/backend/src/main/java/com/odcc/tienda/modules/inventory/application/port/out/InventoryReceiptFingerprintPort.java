package com.odcc.tienda.modules.inventory.application.port.out;

public interface InventoryReceiptFingerprintPort {

    String sha256(String canonicalValue);
}
