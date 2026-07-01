package com.example.smt;

public final class RejectReason {
    String reasonCode;
    String description;
    String reasonGroup;

    String label() {
        String code = reasonCode == null ? "" : reasonCode.trim();
        String desc = description == null ? "" : description.trim();
        return code.isEmpty() ? desc : code + " - " + desc;
    }
}
