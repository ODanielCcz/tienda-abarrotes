package com.odcc.tienda.modules.identity.application.exception;

public class SelfDisableNotAllowedException extends IdentityException {

    public SelfDisableNotAllowedException() {
        super("No puedes desactivar tu propio usuario");
    }
}
