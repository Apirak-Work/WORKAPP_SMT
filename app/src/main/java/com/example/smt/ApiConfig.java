package com.example.smt;

final class ApiConfig {
    static final String[] BASE_URLS = {
            "http://10.10.203.116:5000",
            "http://10.0.2.2:5000",
            "http://127.0.0.1:5000"
    };

    static String primaryBaseUrl() {
        return BASE_URLS[0].endsWith("/") ? BASE_URLS[0] : BASE_URLS[0] + "/";
    }

    private ApiConfig() {
    }
}
