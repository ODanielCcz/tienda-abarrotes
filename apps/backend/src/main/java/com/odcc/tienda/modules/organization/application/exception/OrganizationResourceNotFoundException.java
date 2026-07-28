package com.odcc.tienda.modules.organization.application.exception;

import java.util.UUID;

public class OrganizationResourceNotFoundException extends RuntimeException {
    public OrganizationResourceNotFoundException(String resource, UUID id) {
        super("No existe " + resource + " con id " + id);
    }
}