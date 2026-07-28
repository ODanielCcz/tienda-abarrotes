package com.odcc.tienda.modules.organization.application.exception;

public class OrganizationCodeAlreadyExistsException extends RuntimeException {
    public OrganizationCodeAlreadyExistsException(String resource, String code) {
        super("Ya existe " + resource + " con codigo " + code);
    }
}