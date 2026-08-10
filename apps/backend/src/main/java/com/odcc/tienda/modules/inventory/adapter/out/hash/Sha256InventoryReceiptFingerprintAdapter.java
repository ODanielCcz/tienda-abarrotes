package com.odcc.tienda.modules.inventory.adapter.out.hash;

import com.odcc.tienda.modules.inventory.application.port.out.InventoryReceiptFingerprintPort;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class Sha256InventoryReceiptFingerprintAdapter implements InventoryReceiptFingerprintPort {

    @Override
    public String sha256(String canonicalValue) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(canonicalValue.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no esta disponible", exception);
        }
    }
}
