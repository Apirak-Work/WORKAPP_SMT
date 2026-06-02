package com.example.smt;

import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ProductionRepository {
    interface ProductionDataCallback {
        void onSuccess(
                ProductionDetail detail,
                List<OperTrackingRow> operRows,
                ProductionApiClient.ValidateScanResult validation
        );

        void onError(String message);
    }

    interface SaveProductionCallback {
        void onSuccess(ProductionApiClient.SaveProductionResult result);

        void onError(String message);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ProductionApiClient apiClient = new ProductionApiClient(ApiConfig.BASE_URLS);

    void loadProductionData(
            String userId,
            String machineId,
            String runcardNo,
            ProductionDataCallback callback
    ) {
        executor.execute(() -> {
            try {
                ProductionDetail detail = apiClient.getProductionDetail(runcardNo);
                List<OperTrackingRow> operRows = apiClient.getOperTracking(runcardNo);
                ProductionApiClient.ValidateScanResult validation =
                        apiClient.validateScan(userId, machineId, runcardNo);
                mainHandler.post(() -> callback.onSuccess(detail, operRows, validation));
            } catch (Exception e) {
                String message = e.getMessage() == null
                        ? "Unable to load production data"
                        : e.getMessage();
                mainHandler.post(() -> callback.onError(message));
            }
        });
    }

    void saveProduction(
            String userId,
            String machineId,
            String runcardNo,
            int goodQty,
            int scrapQty,
            long startDateMillis,
            long finishDateMillis,
            long postingDateMillis,
            SaveProductionCallback callback
    ) {
        executor.execute(() -> {
            try {
                ProductionApiClient.SaveProductionResult result = apiClient.saveProduction(
                        userId,
                        machineId,
                        runcardNo,
                        goodQty,
                        scrapQty,
                        startDateMillis,
                        finishDateMillis,
                        postingDateMillis
                );
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                String message = e.getMessage() == null
                        ? "Unable to save production data"
                        : e.getMessage();
                mainHandler.post(() -> callback.onError(message));
            }
        });
    }

    void shutdown() {
        executor.shutdownNow();
    }
}
