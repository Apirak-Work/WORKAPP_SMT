package com.example.smt;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class ProductionApiClient {
    private final String[] baseUrls;

    ProductionApiClient(String[] baseUrls) {
        this.baseUrls = new String[baseUrls.length];
        for (int i = 0; i < baseUrls.length; i++) {
            this.baseUrls[i] = trimTrailingSlash(baseUrls[i]);
        }
    }

    ProductionDetail getProductionDetail(String runcardNo) throws IOException, JSONException {
        JSONObject json = new JSONObject(get("/api/production/detail/" + urlSegment(runcardNo)));
        return new ProductionDetail(
                runcardNo,
                opt(json, "description"),
                opt(json, "material"),
                opt(json, "rc_quantity"),
                opt(json, "qty_rc"),
                opt(json, "qty_wo"),
                opt(json, "date_code"),
                opt(json, "work_order"),
                opt(json, "mpq"),
                opt(json, "assy_lot"),
                opt(json, "waferLot"),
                opt(json, "orderType"),
                opt(json, "uom"),
                opt(json, "lot_type"),
                opt(json, "reel_number")
        );
    }

    List<OperTrackingRow> getOperTracking(String runcardNo) throws IOException, JSONException {
        JSONArray array = new JSONArray(get("/api/production/runcards/" + urlSegment(runcardNo) + "/opers"));
        List<OperTrackingRow> rows = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject json = array.getJSONObject(i);
            rows.add(new OperTrackingRow(
                    opt(json, "oper"),
                    opt(json, "wc"),
                    opt(json, "description"),
                    opt(json, "workCenter"),
                    opt(json, "receive"),
                    opt(json, "yield"),
                    opt(json, "scrap"),
                    opt(json, "move"),
                    opt(json, "percentYield"),
                    opt(json, "receiveDate"),
                    opt(json, "confirmDate"),
                    opt(json, "en")
            ));
        }
        return rows;
    }

    ValidateScanResult validateScan(String userId, String machineId, String runcardNo) throws IOException, JSONException {
        JSONObject request = new JSONObject()
                .put("userId", userId)
                .put("machineId", machineId)
                .put("runcardNo", runcardNo);
        JSONObject json = new JSONObject(post("/api/workflow/validate-scan", request, true));
        return new ValidateScanResult(
                json.optBoolean("isAllowed", false),
                opt(json, "message"),
                json.optBoolean("userValid", false),
                json.optBoolean("machineValid", false),
                json.optBoolean("runcardValid", false)
        );
    }

    EmployeeProfileResult verifyEmployee(String empId) throws IOException, JSONException {
        JSONObject json = new JSONObject(get("/api/auth/verify/" + urlSegment(empId)));
        return new EmployeeProfileResult(
                opt(json, "empId"),
                opt(json, "empName"),
                opt(json, "position")
        );
    }

    SaveProductionResult saveProduction(
            String userId,
            String machineId,
            String runcardNo,
            ProductionDetail detail,
            OperTrackingRow activeRow,
            int goodQty,
            int scrapQty,
            String workCenter,
            String operation,
            long startDateMillis,
            long finishDateMillis,
            long postingDateMillis
    ) throws IOException, JSONException {
        int receiveQty = goodQty + scrapQty;
        double yieldTrig = receiveQty <= 0 ? 0.0 : (goodQty * 100.0) / receiveQty;
        String material = detail == null ? "-" : fallback(detail.material, "-");
        String materialDesc = detail == null ? "-" : fallback(detail.description, "-");
        String workOrder = detail == null ? "-" : fallback(detail.workOrder, "-");
        String workCenterText = activeRow == null ? "-" : fallback(activeRow.description, "-");

        JSONObject request = new JSONObject()
                .put("runcard_no", runcardNo)
                .put("work_order", workOrder)
                .put("routing_no", "-")
                .put("material", material)
                .put("material_desc", materialDesc)
                .put("operation", fallback(operation, "-"))
                .put("work_center", fallback(workCenter, "-"))
                .put("work_center_text", workCenterText)
                .put("receive_qty", receiveQty)
                .put("good_qty", goodQty)
                .put("scrap_qty", scrapQty)
                .put("yield_trig", yieldTrig)
                .put("user_id", userId)
                .put("plant", "2300");
        JSONObject json = new JSONObject(post("/api/production/confirm", request, false));
        return new SaveProductionResult(json.optBoolean("success", false), opt(json, "message"));
    }

    private String get(String path) throws IOException {
        IOException lastError = null;
        for (String baseUrl : baseUrls) {
            try {
                return getFromBaseUrl(baseUrl, path);
            } catch (IOException e) {
                lastError = e;
            }
        }
        throw lastError == null ? new IOException("No API base URL configured") : lastError;
    }

    private String post(String path, JSONObject json, boolean allowClientErrorBody) throws IOException {
        IOException lastError = null;
        for (String baseUrl : baseUrls) {
            try {
                return postToBaseUrl(baseUrl, path, json, allowClientErrorBody);
            } catch (IOException e) {
                lastError = e;
            }
        }
        throw lastError == null ? new IOException("No API base URL configured") : lastError;
    }

    private String getFromBaseUrl(String baseUrl, String path) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Accept", "application/json");

        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        String body = readAll(stream);
        connection.disconnect();

        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + ": " + body);
        }
        return body;
    }

    private String postToBaseUrl(
            String baseUrl,
            String path,
            JSONObject json,
            boolean allowClientErrorBody
    ) throws IOException {
        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setDoOutput(true);
        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(bytes);
        }

        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        String body = readAll(stream);
        connection.disconnect();

        if (code >= 200 && code < 300) {
            return body;
        }
        if (allowClientErrorBody && code >= 400 && code < 500 && !body.isEmpty()) {
            return body;
        }
        throw new IOException("HTTP " + code + ": " + body);
    }

    private String readAll(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private static String opt(JSONObject json, String key) {
        if (!json.has(key) || json.isNull(key)) {
            return "";
        }
        return json.optString(key, "");
    }

    private static String fallback(String value, String fallback) {
        if (value == null || value.trim().isEmpty() || value.trim().equals("-")) {
            return fallback;
        }
        return value.trim();
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String urlSegment(String value) {
        if (value == null) {
            return "";
        }
        try {
            return URLEncoder.encode(value.trim(), "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException ignored) {
            return value.trim().replace(" ", "%20");
        }
    }

    static final class ValidateScanResult {
        final boolean allowed;
        final String message;
        final boolean userValid;
        final boolean machineValid;
        final boolean runcardValid;

        ValidateScanResult(
                boolean allowed,
                String message,
                boolean userValid,
                boolean machineValid,
                boolean runcardValid
        ) {
            this.allowed = allowed;
            this.message = message;
            this.userValid = userValid;
            this.machineValid = machineValid;
            this.runcardValid = runcardValid;
        }
    }

    static final class EmployeeProfileResult {
        final String empId;
        final String empName;
        final String position;

        EmployeeProfileResult(String empId, String empName, String position) {
            this.empId = empId;
            this.empName = empName;
            this.position = position;
        }
    }

    static final class SaveProductionResult {
        final boolean success;
        final String message;

        SaveProductionResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
}
