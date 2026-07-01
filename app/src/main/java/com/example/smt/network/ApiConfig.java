package com.example.smt.network;

public final class ApiConfig {
    private static final String LOCAL_EMULATOR_BASE_URL = "http://10.0.2.2:8000/";
    private static final String LOCAL_DEVICE_BASE_URL = "http://10.10.203.119:8000/";
    private static final String PRODUCTION_BASE_URL = "http://10.10.203.119:8000/";

    public static final String[] BASE_URLS = {
            LOCAL_DEVICE_BASE_URL
    };

    static String primaryBaseUrl() {
        return BASE_URLS[0].endsWith("/") ? BASE_URLS[0] : BASE_URLS[0] + "/";
    }

    private ApiConfig() {
    }
}
