package com.example.smt;

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
                ProductionApiClient.ValidateScanResult validation = apiClient.validateScan(userId, machineId, runcardNo);
                callback.onSuccess(detail, operRows, validation);
            } catch (Exception e) {
                callback.onError(e.getMessage() == null ? "Unable to load production data" : e.getMessage());
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
                callback.onSuccess(result);
            } catch (Exception e) {
                callback.onError(e.getMessage() == null ? "Unable to save production data" : e.getMessage());
            }
        });
    }

    void shutdown() {
        executor.shutdownNow();
    }
}
