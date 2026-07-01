package com.example.smt;

public final class RejectResponse {
    final boolean success;
    final String message;

    RejectResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
}
