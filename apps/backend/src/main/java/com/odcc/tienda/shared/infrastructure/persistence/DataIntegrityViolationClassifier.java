package com.odcc.tienda.shared.infrastructure.persistence;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public final class DataIntegrityViolationClassifier {

    private DataIntegrityViolationClassifier() {
    }

    public static boolean matchesConstraint(
        DataIntegrityViolationException exception,
        String... constraintNames
    ) {
        Set<String> expectedConstraintNames = Arrays
            .stream(constraintNames)
            .collect(Collectors.toUnmodifiableSet());

        Throwable currentException = exception;
        while (currentException != null) {
            if (matchesHibernateConstraintName(currentException, expectedConstraintNames)
                || messageContainsConstraintName(currentException, expectedConstraintNames)) {
                return true;
            }

            currentException = currentException.getCause();
        }

        return false;
    }

    private static boolean matchesHibernateConstraintName(
        Throwable exception,
        Set<String> expectedConstraintNames
    ) {
        if (exception instanceof ConstraintViolationException constraintViolationException) {
            return expectedConstraintNames.contains(constraintViolationException.getConstraintName());
        }

        return false;
    }

    private static boolean messageContainsConstraintName(
        Throwable exception,
        Set<String> expectedConstraintNames
    ) {
        String message = exception.getMessage();
        return message != null && expectedConstraintNames.stream().anyMatch(message::contains);
    }
}
