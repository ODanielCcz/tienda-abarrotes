package com.odcc.tienda.shared.support;

import com.odcc.tienda.shared.application.audit.BusinessAuditEvent;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;

import java.util.ArrayList;
import java.util.List;

public final class InMemoryBusinessAuditPort implements BusinessAuditPort {

    private final List<BusinessAuditEvent> events = new ArrayList<>();

    @Override
    public void record(BusinessAuditEvent event) {
        events.add(event);
    }

    public List<BusinessAuditEvent> events() {
        return List.copyOf(events);
    }
}
