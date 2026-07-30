package com.odcc.tienda.modules.sync.application.port.out;

public interface RequestFingerprintPort {
    String sha256(String canonicalValue);
}
