package com.example.smt;

import android.os.CountDownTimer;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class ScanViewModel extends ViewModel {

    enum WorkflowStep {
        SCAN,
        VERIFY,
        INPUT_QTY,
        SUMMARY
    }

    enum CurrentScanState {
        USER,
        MACHINE,
        RUNCARD,
        COMPLETE
    }

    static class UiState {
        final WorkflowStep step;
        final CurrentScanState scanState;
        final String userId;
        final String machineId;
        final String runcard;
        final String scannerMessage;
        final String verifyButtonText;
        final boolean verifyVisible;
        final boolean verifyEnabled;
        final boolean productionVisible;
        final String goodQty;
        final String scrapQty;
        final boolean qtyComplete;
        final String saveButtonText;
        final boolean saveEnabled;
        final long startDateMillis;
        final long finishDateMillis;
        final long postingDateMillis;
        final boolean productionDataLoading;
        final String productionDataError;
        final ProductionDetail productionDetail;
        final List<OperTrackingRow> operRows;
        final boolean accessDeniedEvent;
        final boolean saveCompleteEvent;

        UiState(
                WorkflowStep step,
                CurrentScanState scanState,
                String userId,
                String machineId,
                String runcard,
                String scannerMessage,
                String verifyButtonText,
                boolean verifyVisible,
                boolean verifyEnabled,
                boolean productionVisible,
                String goodQty,
                String scrapQty,
                boolean qtyComplete,
                String saveButtonText,
                boolean saveEnabled,
                long startDateMillis,
                long finishDateMillis,
                long postingDateMillis,
                boolean productionDataLoading,
                String productionDataError,
                ProductionDetail productionDetail,
                List<OperTrackingRow> operRows,
                boolean accessDeniedEvent,
                boolean saveCompleteEvent
        ) {
            this.step = step;
            this.scanState = scanState;
            this.userId = userId;
            this.machineId = machineId;
            this.runcard = runcard;
            this.scannerMessage = scannerMessage;
            this.verifyButtonText = verifyButtonText;
            this.verifyVisible = verifyVisible;
            this.verifyEnabled = verifyEnabled;
            this.productionVisible = productionVisible;
            this.goodQty = goodQty;
            this.scrapQty = scrapQty;
            this.qtyComplete = qtyComplete;
            this.saveButtonText = saveButtonText;
            this.saveEnabled = saveEnabled;
            this.startDateMillis = startDateMillis;
            this.finishDateMillis = finishDateMillis;
            this.postingDateMillis = postingDateMillis;
            this.productionDataLoading = productionDataLoading;
            this.productionDataError = productionDataError;
            this.productionDetail = productionDetail;
            this.operRows = operRows;
            this.accessDeniedEvent = accessDeniedEvent;
            this.saveCompleteEvent = saveCompleteEvent;
        }
    }

    private static final long COUNTDOWN_MS = 3000L;
    private static final Pattern USER_PATTERN = Pattern.compile("^EN[A-Z0-9-]{1,}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MACHINE_PATTERN = Pattern.compile("^MC[A-Z0-9-]{1,}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RUNCARD_PATTERN = Pattern.compile("^\\d{10}$");

    private final MutableLiveData<UiState> uiState = new MutableLiveData<>();
    private final ProductionRepository productionRepository = new ProductionRepository();
    private CountDownTimer verifyTimer;
    private CountDownTimer saveTimer;

    private WorkflowStep step = WorkflowStep.SCAN;
    private CurrentScanState scanState = CurrentScanState.USER;
    private String userId = "";
    private String machineId = "";
    private String runcard = "";
    private String scannerMessage = "Scan or enter User ID";
    private String verifyButtonText = "VERIFY & PROCEED";
    private boolean verifyCountdownRunning;
    private boolean verifyReady;
    private String goodQty = "";
    private String scrapQty = "";
    private String saveButtonText = "SAVE CONFIRM";
    private boolean saveCountdownRunning;
    private long startDateMillis;
    private long finishDateMillis;
    private long postingDateMillis;
    private boolean productionDataLoading;
    private String productionDataError = "";
    private ProductionDetail productionDetail;
    private List<OperTrackingRow> operRows = new ArrayList<>();
    private boolean scanValidated;

    public ScanViewModel() {
        publish(false, false);
    }

    LiveData<UiState> getUiState() {
        return uiState;
    }

    void submitCurrentScanValue(String rawValue) {
        submitScanValue(scanState, rawValue);
    }

    void submitScanValue(CurrentScanState targetState, String rawValue) {
        String value = normalize(rawValue);
        if (value.isEmpty() || targetState == CurrentScanState.COMPLETE) {
            return;
        }
        if (scanState != CurrentScanState.COMPLETE && targetState != scanState) {
            scannerMessage = "Complete " + labelForState(scanState) + " before continuing";
            publish(false, false);
            return;
        }
        if (!isValidForState(targetState, value)) {
            scannerMessage = "Invalid " + labelForState(targetState) + " barcode. Scan " + hintForState(targetState);
            publish(false, false);
            return;
        }

        switch (targetState) {
            case USER:
                userId = value;
                scanState = CurrentScanState.MACHINE;
                scannerMessage = "User captured. Scan or enter Machine";
                break;
            case MACHINE:
                machineId = value;
                scanState = CurrentScanState.RUNCARD;
                scannerMessage = "Machine captured. Scan or enter Runcard";
                break;
            case RUNCARD:
                runcard = value;
                scanState = CurrentScanState.COMPLETE;
                step = WorkflowStep.VERIFY;
                scannerMessage = "Checking backend and validating scan data...";
                verifyReady = false;
                verifyButtonText = "CHECKING BACKEND...";
                scanValidated = false;
                loadProductionData(value);
                break;
            default:
                break;
        }
        publish(false, false);
    }

    void updateTypedValue(CurrentScanState targetState, String rawValue) {
        String value = normalize(rawValue);
        switch (targetState) {
            case USER:
                userId = value;
                break;
            case MACHINE:
                machineId = value;
                break;
            case RUNCARD:
                runcard = value;
                break;
            default:
                break;
        }
        if (!hasScanSet()) {
            verifyReady = false;
            scanValidated = false;
            verifyButtonText = "VERIFY & PROCEED";
            cancelTimer(verifyTimer);
            verifyCountdownRunning = false;
            productionDataError = "";
            productionDetail = null;
            operRows = new ArrayList<>();
        }
        recalculateScanState();
        publish(false, false);
    }

    boolean isValueValidForState(CurrentScanState targetState, String rawValue) {
        return isValidForState(targetState, normalize(rawValue));
    }

    void verifyAndProceed() {
        if (!canVerify()) {
            return;
        }

        step = WorkflowStep.INPUT_QTY;
        ensureStartTimestampForStatus("KIT PULL GENERATION");
        publish(false, false);
    }

    void setGoodQty(String value) {
        goodQty = normalize(value);
        publish(false, false);
    }

    void setScrapQty(String value) {
        scrapQty = normalize(value);
        publish(false, false);
    }

    void enterSummary() {
        if (step != WorkflowStep.INPUT_QTY) {
            return;
        }
        step = WorkflowStep.SUMMARY;
        if (finishDateMillis == 0L) {
            finishDateMillis = System.currentTimeMillis();
            postingDateMillis = finishDateMillis;
        }
        publish(false, false);
    }

    void requestSave() {
        if (saveCountdownRunning || !canSave()) {
            return;
        }
        startSaveCountdown();
    }

    void consumeDialogEvents() {
        publish(false, false);
    }

    String formatTimestamp(long millis) {
        if (millis == 0L) {
            return "-";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(millis));
    }

    private void recalculateScanState() {
        if (userId.isEmpty()) {
            scanState = CurrentScanState.USER;
            step = WorkflowStep.SCAN;
            scannerMessage = "Scan or enter User ID";
        } else if (machineId.isEmpty()) {
            scanState = CurrentScanState.MACHINE;
            step = WorkflowStep.SCAN;
            scannerMessage = "User captured. Scan or enter Machine";
        } else if (runcard.isEmpty()) {
            scanState = CurrentScanState.RUNCARD;
            step = WorkflowStep.SCAN;
            scannerMessage = "Machine captured. Scan or enter Runcard";
        } else {
            scanState = CurrentScanState.COMPLETE;
            step = WorkflowStep.VERIFY;
            if (scanValidated) {
                scannerMessage = "Runcard data loaded and scan validated. Review before confirming";
                if (!verifyCountdownRunning && !verifyReady) {
                    startVerifyCountdown();
                }
            } else if (!productionDataLoading && productionDataError.isEmpty()) {
                scannerMessage = "Checking backend and validating scan data...";
            }
        }
    }

    private void ensureStartTimestampForStatus(String status) {
        if ("KIT PULL GENERATION".equals(status) && startDateMillis == 0L) {
            startDateMillis = System.currentTimeMillis();
        }
    }

    private void startVerifyCountdown() {
        if (verifyCountdownRunning || verifyReady) {
            return;
        }
        verifyCountdownRunning = true;
        verifyReady = false;
        cancelTimer(verifyTimer);
        verifyTimer = new CountDownTimer(COUNTDOWN_MS, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = Math.max(1L, (millisUntilFinished + 999L) / 1000L);
                verifyButtonText = "Confirming in " + seconds + "...";
                publish(false, false);
            }

            @Override
            public void onFinish() {
                verifyCountdownRunning = false;
                verifyReady = true;
                verifyButtonText = "VERIFY & PROCEED";
                publish(false, false);
            }
        };
        verifyTimer.start();
    }

    private void startSaveCountdown() {
        saveCountdownRunning = true;
        cancelTimer(saveTimer);
        saveTimer = new CountDownTimer(COUNTDOWN_MS, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = Math.max(1L, (millisUntilFinished + 999L) / 1000L);
                saveButtonText = "Saving in " + seconds + "...";
                publish(false, false);
            }

            @Override
            public void onFinish() {
                finishDateMillis = System.currentTimeMillis();
                postingDateMillis = finishDateMillis;
                saveButtonText = "POSTING TO API...";
                publish(false, false);
                saveProduction();
            }
        };
        saveTimer.start();
    }

    private void loadProductionData(String runcardNo) {
        productionDataLoading = true;
        productionDataError = "";
        productionDetail = null;
        operRows = new ArrayList<>();
        publish(false, false);

        productionRepository.loadProductionData(userId, machineId, runcardNo, new ProductionRepository.ProductionDataCallback() {
            @Override
            public void onSuccess(
                    ProductionDetail detail,
                    List<OperTrackingRow> rows,
                    ProductionApiClient.ValidateScanResult validation
            ) {
                productionDataLoading = false;
                productionDetail = detail;
                operRows = rows == null ? new ArrayList<>() : rows;
                scanValidated = validation != null && validation.allowed;
                if (scanValidated) {
                    productionDataError = "";
                    scannerMessage = "Backend connected. Scan data validated.";
                    startVerifyCountdown();
                } else {
                    productionDataError = validationErrorMessage(validation);
                    scannerMessage = productionDataError;
                    verifyReady = false;
                    verifyButtonText = "VERIFY LOCKED";
                    cancelTimer(verifyTimer);
                    verifyCountdownRunning = false;
                    publish(true, false);
                    return;
                }
                publish(false, false);
            }

            @Override
            public void onError(String message) {
                productionDataLoading = false;
                productionDataError = message == null ? "Unable to load production data" : message;
                scannerMessage = "Backend failed: " + productionDataError;
                scanValidated = false;
                verifyReady = false;
                verifyButtonText = "VERIFY LOCKED";
                cancelTimer(verifyTimer);
                verifyCountdownRunning = false;
                publish(false, false);
            }
        });
    }

    private void saveProduction() {
        int goodQtyNumber = parseQty(goodQty);
        int scrapQtyNumber = parseQty(scrapQty);
        productionRepository.saveProduction(
                userId,
                machineId,
                runcard,
                goodQtyNumber,
                scrapQtyNumber,
                startDateMillis,
                finishDateMillis,
                postingDateMillis,
                new ProductionRepository.SaveProductionCallback() {
                    @Override
                    public void onSuccess(ProductionApiClient.SaveProductionResult result) {
                        saveCountdownRunning = false;
                        saveButtonText = "SAVE CONFIRM";
                        if (result != null && result.success) {
                            scannerMessage = result.message.isEmpty()
                                    ? "Production saved successfully"
                                    : result.message;
                            publish(false, true);
                        } else {
                            scannerMessage = result == null || result.message.isEmpty()
                                    ? "Production save failed"
                                    : result.message;
                            publish(false, false);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        saveCountdownRunning = false;
                        saveButtonText = "SAVE CONFIRM";
                        scannerMessage = "Save failed: " + (message == null ? "Unable to save production data" : message);
                        publish(false, false);
                    }
                }
        );
    }

    private boolean hasScanSet() {
        return !userId.isEmpty() && !machineId.isEmpty() && !runcard.isEmpty();
    }

    private boolean isQtyComplete() {
        return isNonNegativeInteger(goodQty) && isNonNegativeInteger(scrapQty);
    }

    private boolean canVerify() {
        return verifyReady
                && !verifyCountdownRunning
                && hasScanSet()
                && scanValidated
                && productionDetail != null
                && productionDataError.isEmpty();
    }

    private boolean canSave() {
        return step == WorkflowStep.INPUT_QTY
                && scanValidated
                && !saveCountdownRunning
                && isQtyComplete();
    }

    private boolean isNonNegativeInteger(String value) {
        if (value.isEmpty()) {
            return false;
        }
        try {
            return Integer.parseInt(value) >= 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private int parseQty(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String validationErrorMessage(ProductionApiClient.ValidateScanResult validation) {
        if (validation == null) {
            return "Access Denied / Data Mismatch";
        }
        List<String> failed = new ArrayList<>();
        if (!validation.userValid) {
            failed.add("User");
        }
        if (!validation.machineValid) {
            failed.add("Machine");
        }
        if (!validation.runcardValid) {
            failed.add("Runcard");
        }
        String message = validation.message.isEmpty() ? "Access Denied / Data Mismatch" : validation.message;
        return failed.isEmpty() ? message : message + " (" + String.join(", ", failed) + ")";
    }

    private boolean isValidForState(CurrentScanState targetState, String value) {
        switch (targetState) {
            case USER:
                return USER_PATTERN.matcher(value).matches();
            case MACHINE:
                return MACHINE_PATTERN.matcher(value).matches();
            case RUNCARD:
                return RUNCARD_PATTERN.matcher(value).matches();
            case COMPLETE:
            default:
                return false;
        }
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.replace("\r", "").replace("\n", "").replace("\t", "").trim();
    }

    private String labelForState(CurrentScanState state) {
        switch (state) {
            case USER:
                return "User ID";
            case MACHINE:
                return "Machine";
            case RUNCARD:
                return "Runcard";
            case COMPLETE:
            default:
                return "the current scan";
        }
    }

    private String hintForState(CurrentScanState state) {
        switch (state) {
            case USER:
                return "User ID starting with EN";
            case MACHINE:
                return "Machine starting with MC";
            case RUNCARD:
                return "10-digit Runcard";
            case COMPLETE:
            default:
                return "the expected barcode";
        }
    }

    private void publish(boolean accessDeniedEvent, boolean saveCompleteEvent) {
        boolean productionVisible = step == WorkflowStep.INPUT_QTY
                || step == WorkflowStep.SUMMARY
                || productionDataLoading
                || productionDetail != null
                || !productionDataError.isEmpty();
        boolean qtyComplete = isQtyComplete();
        boolean canSave = canSave();
        uiState.postValue(new UiState(
                step,
                scanState,
                userId,
                machineId,
                runcard,
                scannerMessage,
                verifyButtonText,
                hasScanSet(),
                canVerify(),
                productionVisible,
                goodQty,
                scrapQty,
                qtyComplete,
                saveButtonText,
                canSave,
                startDateMillis,
                finishDateMillis,
                postingDateMillis,
                productionDataLoading,
                productionDataError,
                productionDetail,
                new ArrayList<>(operRows),
                accessDeniedEvent,
                saveCompleteEvent
        ));
    }

    private void cancelTimer(CountDownTimer timer) {
        if (timer != null) {
            timer.cancel();
        }
    }

    @Override
    protected void onCleared() {
        cancelTimer(verifyTimer);
        cancelTimer(saveTimer);
        productionRepository.shutdown();
        super.onCleared();
    }
}
