package com.odcc.tienda.shared.application.audit;

public interface BusinessAuditPort {

    void record(BusinessAuditEvent event);
}
