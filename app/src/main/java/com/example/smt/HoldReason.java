package com.example.smt;

public final class HoldReason {
    String reasonCode;
    String description;

    String label() {
        String code = reasonCode == null ? "" : reasonCode.trim();
        String text = description == null ? "" : description.trim();
        if (code.isEmpty()) {
            return text;
        }
        return text.isEmpty() ? code : code + " - " + text;
    }
}
