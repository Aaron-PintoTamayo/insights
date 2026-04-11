package com.h2ai.insights.exception;

import java.util.List;

public class IncompletePatientInfoException extends RuntimeException {

    private final List<String> missingFields;

    public IncompletePatientInfoException(List<String> missingFields) {
        super("Missing required patient fields: " + String.join(", ", missingFields));
        this.missingFields = List.copyOf(missingFields);
    }

    public List<String> getMissingFields() {
        return missingFields;
    }
}
