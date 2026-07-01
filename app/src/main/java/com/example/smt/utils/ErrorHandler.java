package com.example.smt.utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.ResponseBody;

public final class ErrorHandler {
    private static final String DEFAULT_ERROR_MESSAGE = "System error occurred. Please try again.";
    private static final Pattern ERROR_CODE_PATTERN = Pattern.compile("\\b(\\d{5})\\b");

    private ErrorHandler() {
    }

    public static String parseError(ResponseBody errorBody) {
        if (errorBody == null) {
            return DEFAULT_ERROR_MESSAGE;
        }

        try {
            String rawBody = errorBody.string();
            String detail = extractDetail(rawBody);
            String errorCode = extractErrorCode(detail);
            return mapErrorCode(errorCode);
        } catch (IOException exception) {
            return DEFAULT_ERROR_MESSAGE;
        }
    }

    private static String extractDetail(String rawBody) {
        if (rawBody == null || rawBody.trim().isEmpty()) {
            return "";
        }

        try {
            JSONObject json = new JSONObject(rawBody);
            return json.optString("detail", "");
        } catch (JSONException exception) {
            return rawBody;
        }
    }

    private static String extractErrorCode(String detail) {
        if (detail == null || detail.isEmpty()) {
            return "";
        }

        Matcher matcher = ERROR_CODE_PATTERN.matcher(detail);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String mapErrorCode(String errorCode) {
        switch (errorCode) {
            case "51509":
                return "Operation failed: Data mismatch (51509)";
            case "51510":
                return "Cannot merge: Source runcard is not available or already closed (51510)";
            default:
                return DEFAULT_ERROR_MESSAGE;
        }
    }
}
