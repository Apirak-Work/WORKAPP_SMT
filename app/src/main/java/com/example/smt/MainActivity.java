package com.example.smt;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

public class MainActivity extends AppCompatActivity {

    private static final String DATAWEDGE_ACTION = "com.symbol.datawedge.data";
    private static final String DATAWEDGE_EXTRA_DATA_STRING = "com.symbol.datawedge.data_string";
    private static final String LEGACY_DATAWEDGE_EXTRA_DATA_STRING = "com.motorolasolutions.emdk.datawedge.data_string";
    private static final long SCANNER_AUTO_SUBMIT_DELAY_MS = 350L;

    private final StringBuilder scanBuffer = new StringBuilder();
    private final Handler scannerHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoSubmitFocusedScanField = this::submitFocusedScanFieldIfValid;
    private final BroadcastReceiver dataWedgeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handleScannerIntent(intent);
        }
    };

    private ScanViewModel viewModel;
    private TextView stepScan;
    private TextView stepVerify;
    private TextView stepInput;
    private TextView stepSummary;
    private TextView scannerStatus;
    private EditText userIdInput;
    private EditText machineInput;
    private EditText runcardInput;
    private TextView descriptionValue;
    private TextView materialValue;
    private TextView rcQuantityValue;
    private TextView dateCodeValue;
    private TextView workOrderValue;
    private TextView assyLotValue;
    private TextView waferLotValue;
    private TextView orderTypeValue;
    private TextView uomValue;
    private TextView lotTypeValue;
    private EditText reelNumberInput;
    private TextView timestampValue;
    private TextView kitPullReceiveDateValue;
    private TextView operYieldValue;
    private TextView operScrapValue;
    private TextView operPercentYieldValue;
    private TextView summaryPostingDateValue;
    private TextView summaryConfirmDateValue;
    private LinearLayout operRowsTable;
    private LinearLayout productionPanel;
    private Button verifyButton;
    private Button saveButton;
    private Button summaryButton;
    private EditText goodQtyInput;
    private EditText scrapQtyInput;
    private ScanViewModel.CurrentScanState lastFocusedScanState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        bindViews();
        viewModel = new ViewModelProvider(this).get(ScanViewModel.class);
        viewModel.getUiState().observe(this, this::render);
        wireActions();
        registerDataWedgeReceiver();
        handleScannerIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleScannerIntent(intent);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(dataWedgeReceiver);
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_MULTIPLE && event.getCharacters() != null) {
            injectScannedValue(event.getCharacters());
            return true;
        }

        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event);
        }

        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                || keyCode == KeyEvent.KEYCODE_TAB) {
            if (isScanFieldFocused()) {
                submitFocusedScanField();
                return true;
            }
            submitBufferedScan();
            return true;
        }

        if (isScanFieldFocused() || isQtyInputFocused()) {
            return super.dispatchKeyEvent(event);
        }

        char scannedChar = (char) event.getUnicodeChar(event.getMetaState());
        if (scannedChar > 31 && scannedChar != 127) {
            scanBuffer.append(scannedChar);
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    private void bindViews() {
        stepScan = findViewById(R.id.stepScan);
        stepVerify = findViewById(R.id.stepVerify);
        stepInput = findViewById(R.id.stepInput);
        stepSummary = findViewById(R.id.stepSummary);
        scannerStatus = findViewById(R.id.scannerStatus);
        userIdInput = findViewById(R.id.userIdInput);
        machineInput = findViewById(R.id.machineInput);
        runcardInput = findViewById(R.id.runcardInput);
        descriptionValue = findViewById(R.id.descriptionValue);
        materialValue = findViewById(R.id.materialValue);
        rcQuantityValue = findViewById(R.id.rcQuantityValue);
        dateCodeValue = findViewById(R.id.dateCodeValue);
        workOrderValue = findViewById(R.id.workOrderValue);
        assyLotValue = findViewById(R.id.assyLotValue);
        waferLotValue = findViewById(R.id.waferLotValue);
        orderTypeValue = findViewById(R.id.orderTypeValue);
        uomValue = findViewById(R.id.uomValue);
        lotTypeValue = findViewById(R.id.lotTypeValue);
        reelNumberInput = findViewById(R.id.reelNumberInput);
        timestampValue = findViewById(R.id.timestampValue);
        kitPullReceiveDateValue = findViewById(R.id.kitPullReceiveDateValue);
        operYieldValue = findViewById(R.id.operYieldValue);
        operScrapValue = findViewById(R.id.operScrapValue);
        operPercentYieldValue = findViewById(R.id.operPercentYieldValue);
        summaryPostingDateValue = findViewById(R.id.summaryPostingDateValue);
        summaryConfirmDateValue = findViewById(R.id.summaryConfirmDateValue);
        operRowsTable = findViewById(R.id.operRowsTable);
        productionPanel = findViewById(R.id.productionPanel);
        verifyButton = findViewById(R.id.verifyButton);
        saveButton = findViewById(R.id.saveButton);
        summaryButton = findViewById(R.id.summaryButton);
        goodQtyInput = findViewById(R.id.goodQtyInput);
        scrapQtyInput = findViewById(R.id.scrapQtyInput);
    }

    private void registerDataWedgeReceiver() {
        IntentFilter filter = new IntentFilter(DATAWEDGE_ACTION);
        ContextCompat.registerReceiver(
                this,
                dataWedgeReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
        );
    }

    private void wireActions() {
        verifyButton.setOnClickListener(v -> viewModel.verifyAndProceed());
        summaryButton.setOnClickListener(v -> viewModel.enterSummary());
        saveButton.setOnClickListener(v -> viewModel.requestSave());

        configureScanInput(userIdInput, ScanViewModel.CurrentScanState.USER);
        configureScanInput(machineInput, ScanViewModel.CurrentScanState.MACHINE);
        configureScanInput(runcardInput, ScanViewModel.CurrentScanState.RUNCARD);

        userIdInput.setOnEditorActionListener((v, actionId, event) ->
                handleScanEditorAction(ScanViewModel.CurrentScanState.USER, actionId, event));
        machineInput.setOnEditorActionListener((v, actionId, event) ->
                handleScanEditorAction(ScanViewModel.CurrentScanState.MACHINE, actionId, event));
        runcardInput.setOnEditorActionListener((v, actionId, event) ->
                handleScanEditorAction(ScanViewModel.CurrentScanState.RUNCARD, actionId, event));

        goodQtyInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(android.text.Editable editable) {
                viewModel.setGoodQty(editable.toString());
            }
        });
        scrapQtyInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(android.text.Editable editable) {
                viewModel.setScrapQty(editable.toString());
            }
        });
    }

    private void configureScanInput(EditText editText, ScanViewModel.CurrentScanState targetState) {
        editText.setShowSoftInputOnFocus(false);
        editText.setSelectAllOnFocus(false);
        editText.setOnClickListener(v -> requestFieldFocus(editText));
        editText.setOnLongClickListener(v -> {
            editText.setShowSoftInputOnFocus(true);
            editText.requestFocus();
            editText.post(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
                }
            });
            return true;
        });
        editText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                if (!editText.hasFocus()) {
                    return;
                }
                scannerHandler.removeCallbacks(autoSubmitFocusedScanField);
                if (viewModel.isValueValidForState(targetState, editable.toString())) {
                    scannerHandler.postDelayed(autoSubmitFocusedScanField, SCANNER_AUTO_SUBMIT_DELAY_MS);
                }
            }
        });
    }

    private boolean handleScanEditorAction(
            ScanViewModel.CurrentScanState targetState,
            int actionId,
            KeyEvent event
    ) {
        boolean isImeNextOrDone = actionId == EditorInfo.IME_ACTION_NEXT
                || actionId == EditorInfo.IME_ACTION_DONE;
        boolean isHardwareEnter = event != null
                && event.getAction() == KeyEvent.ACTION_DOWN
                && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                || event.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER
                || event.getKeyCode() == KeyEvent.KEYCODE_TAB);

        if (isImeNextOrDone || isHardwareEnter) {
            viewModel.submitScanValue(targetState, getScanInput(targetState).getText().toString());
            return true;
        }
        return false;
    }

    private void render(ScanViewModel.UiState state) {
        scannerStatus.setText(state.scannerMessage);
        scannerStatus.setTextColor(ContextCompat.getColor(
                this,
                isErrorMessage(state.scannerMessage) ? R.color.danger : R.color.teal_dark
        ));
        setTextIfDifferent(userIdInput, state.userId);
        setTextIfDifferent(machineInput, state.machineId);
        setTextIfDifferent(runcardInput, state.runcard);
        renderProductionDetail(state);

        verifyButton.setVisibility(state.verifyVisible ? View.VISIBLE : View.GONE);
        verifyButton.setText(state.verifyButtonText);
        verifyButton.setEnabled(state.verifyEnabled);

        productionPanel.setVisibility(state.productionVisible ? View.VISIBLE : View.GONE);
        summaryButton.setEnabled(state.step == ScanViewModel.WorkflowStep.INPUT_QTY);
        saveButton.setText(state.saveButtonText);
        saveButton.setEnabled(state.saveEnabled);

        timestampValue.setText(
                "Start: " + viewModel.formatTimestamp(state.startDateMillis)
                        + "\nFinish: " + viewModel.formatTimestamp(state.finishDateMillis)
                        + "\nPosting: " + viewModel.formatTimestamp(state.postingDateMillis)
        );
        renderOperTracking(state);

        updateStepper(state.step);
        focusForScanState(state);

        if (state.accessDeniedEvent) {
            showAccessDeniedDialog();
            viewModel.consumeDialogEvents();
        } else if (state.saveCompleteEvent) {
            Toast.makeText(this, "Production saved successfully", Toast.LENGTH_LONG).show();
            viewModel.consumeDialogEvents();
        }
    }

    private boolean isErrorMessage(String message) {
        return message.startsWith("Invalid")
                || message.startsWith("Backend failed")
                || message.startsWith("Save failed")
                || message.startsWith("Access Denied")
                || message.contains("Mismatch");
    }

    private void focusForScanState(ScanViewModel.UiState state) {
        if (state.productionVisible || state.scanState == lastFocusedScanState) {
            return;
        }

        lastFocusedScanState = state.scanState;
        switch (state.scanState) {
            case USER:
                requestFieldFocus(userIdInput);
                break;
            case MACHINE:
                requestFieldFocus(machineInput);
                break;
            case RUNCARD:
                requestFieldFocus(runcardInput);
                break;
            case COMPLETE:
                verifyButton.requestFocus();
                break;
            default:
                break;
        }
    }

    private void requestFieldFocus(EditText editText) {
        editText.setShowSoftInputOnFocus(false);
        editText.requestFocus();
        editText.setSelection(editText.getText().length());
    }

    private void updateStepper(ScanViewModel.WorkflowStep step) {
        styleStep(stepScan, step, ScanViewModel.WorkflowStep.SCAN);
        styleStep(stepVerify, step, ScanViewModel.WorkflowStep.VERIFY);
        styleStep(stepInput, step, ScanViewModel.WorkflowStep.INPUT_QTY);
        styleStep(stepSummary, step, ScanViewModel.WorkflowStep.SUMMARY);
    }

    private void styleStep(TextView view, ScanViewModel.WorkflowStep current, ScanViewModel.WorkflowStep target) {
        if (target.ordinal() < current.ordinal()) {
            view.setBackgroundResource(R.drawable.bg_step_done);
            view.setTextColor(ContextCompat.getColor(this, R.color.white));
        } else if (target == current) {
            view.setBackgroundResource(R.drawable.bg_step_active);
            view.setTextColor(ContextCompat.getColor(this, R.color.white));
        } else {
            view.setBackgroundResource(R.drawable.bg_step_inactive);
            view.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
    }

    private void renderOperTracking(ScanViewModel.UiState state) {
        kitPullReceiveDateValue.setText(viewModel.formatTimestamp(state.startDateMillis));
        summaryPostingDateValue.setText(viewModel.formatTimestamp(state.postingDateMillis));
        summaryConfirmDateValue.setText(viewModel.formatTimestamp(state.finishDateMillis));
        renderOperRows(state);

        OperTrackingRow latestOper = activeOperRow(state);
        if (latestOper != null) {
            operYieldValue.setText(displayOrDash(latestOper.yield));
            operScrapValue.setText(displayOrDash(latestOper.scrap));
            operPercentYieldValue.setText(displayOrDash(latestOper.percentYield));
        } else {
            operYieldValue.setText(state.goodQty.isEmpty() ? "-" : state.goodQty);
            operScrapValue.setText(state.scrapQty.isEmpty() ? "-" : state.scrapQty);
            operPercentYieldValue.setText(calculatePercentYield(state.goodQty, state.scrapQty));
        }
    }

    private void renderProductionDetail(ScanViewModel.UiState state) {
        if (state.productionDataLoading) {
            descriptionValue.setText("Loading...");
            materialValue.setText("Loading...");
            return;
        }
        ProductionDetail detail = state.productionDetail;
        if (detail == null) {
            if (!state.productionDataError.isEmpty()) {
                descriptionValue.setText("Load error");
                materialValue.setText(state.productionDataError);
                return;
            }
            descriptionValue.setText("?");
            materialValue.setText("?");
            rcQuantityValue.setText("?");
            dateCodeValue.setText("?");
            workOrderValue.setText("?");
            assyLotValue.setText("?");
            waferLotValue.setText("?");
            orderTypeValue.setText("?");
            uomValue.setText("?");
            lotTypeValue.setText("?");
            setTextIfDifferent(reelNumberInput, "");
            return;
        }

        descriptionValue.setText(displayOrDash(detail.description));
        materialValue.setText(displayOrDash(detail.material));
        rcQuantityValue.setText(formatRcQuantity(detail));
        dateCodeValue.setText(displayOrDash(detail.dateCode));
        workOrderValue.setText(formatWorkOrder(detail));
        assyLotValue.setText(displayOrDash(detail.assyLot));
        waferLotValue.setText(displayOrDash(detail.waferLot));
        orderTypeValue.setText(displayOrDash(detail.orderType));
        uomValue.setText(displayOrDash(detail.uom));
        lotTypeValue.setText(displayOrDash(detail.lotType));
        setTextIfDifferent(reelNumberInput, detail.reelNumber);
    }

    private void renderOperRows(ScanViewModel.UiState state) {
        if (operRowsTable == null || operRowsTable.getChildCount() == 0) {
            return;
        }
        while (operRowsTable.getChildCount() > 1) {
            operRowsTable.removeViewAt(1);
        }

        if (state.operRows == null || state.operRows.isEmpty()) {
            operRowsTable.addView(createOperPlaceholderRow("No OPER data loaded"));
            return;
        }

        OperTrackingRow activeRow = activeOperRow(state);
        for (OperTrackingRow row : state.operRows) {
            operRowsTable.addView(createOperRow(row, row == activeRow));
        }
    }

    private View createOperPlaceholderRow(String message) {
        LinearLayout row = createOperRowContainer();
        row.addView(createOperCell("-", 64, false));
        row.addView(createOperCell("-", 72, false));
        row.addView(createOperCell(message, 248, true));
        row.addView(createOperCell("-", 140, false));
        row.addView(createOperCell("-", 112, false));
        row.addView(createOperCell("-", 112, false));
        row.addView(createOperCell("-", 112, false));
        row.addView(createOperCell("-", 112, false));
        row.addView(createOperCell("-", 112, false));
        row.addView(createOperCell("-", 156, false));
        row.addView(createOperCell("-", 156, false));
        row.addView(createOperCell("-", 72, false));
        return row;
    }

    private View createOperRow(OperTrackingRow oper, boolean active) {
        LinearLayout row = createOperRowContainer();
        row.addView(createOperCell(displayOrDash(oper.oper), 64, active));
        row.addView(createOperCell(displayOrDash(oper.wc), 72, active));
        row.addView(createOperCell(displayOrDash(oper.description), 248, active));
        row.addView(createOperCell(displayOrDash(oper.workCenter), 140, active));
        row.addView(createOperCell(displayOrDash(oper.receive), 112, active));
        row.addView(createOperCell(displayOrDash(oper.yield), 112, active));
        row.addView(createOperCell(displayOrDash(oper.scrap), 112, active));
        row.addView(createOperCell(displayOrDash(oper.move), 112, active));
        row.addView(createOperCell(displayOrDash(oper.percentYield), 112, active));
        row.addView(createOperCell(displayOrDash(oper.receiveDate), 156, active));
        row.addView(createOperCell(displayOrDash(oper.confirmDate), 156, active));
        row.addView(createOperCell(displayOrDash(oper.en), 72, active));
        return row;
    }

    private LinearLayout createOperRowContainer() {
        LinearLayout row = new LinearLayout(this);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private TextView createOperCell(String text, int widthDp, boolean active) {
        TextView cell = new TextView(this, null, 0, R.style.OperBodyCell);
        cell.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), dp(42)));
        cell.setText(text);
        if (active) {
            cell.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            cell.setTextColor(ContextCompat.getColor(this, R.color.link_blue));
        }
        return cell;
    }

    private String formatRcQuantity(ProductionDetail detail) {
        if (!detail.qtyRc.isEmpty() || !detail.qtyWo.isEmpty()) {
            return displayOrDash(detail.qtyRc) + " / " + displayOrDash(detail.qtyWo);
        }
        return displayOrDash(detail.rcQuantity);
    }

    private String formatWorkOrder(ProductionDetail detail) {
        if (!detail.mpq.isEmpty()) {
            return displayOrDash(detail.workOrder) + "   MPQ: " + detail.mpq;
        }
        return displayOrDash(detail.workOrder);
    }

    private OperTrackingRow activeOperRow(ScanViewModel.UiState state) {
        if (state.operRows == null || state.operRows.isEmpty()) {
            return null;
        }
        for (OperTrackingRow row : state.operRows) {
            if (!displayOrDash(row.receive).equals("-") && !displayOrDash(row.receive).equals("0")
                    && displayOrDash(row.yield).equals("-")) {
                return row;
            }
        }
        return state.operRows.get(state.operRows.size() - 1);
    }

    private String calculatePercentYield(String goodQtyText, String scrapQtyText) {
        if (goodQtyText.isEmpty() || scrapQtyText.isEmpty()) {
            return "-";
        }
        try {
            int goodQty = Integer.parseInt(goodQtyText);
            int scrapQty = Integer.parseInt(scrapQtyText);
            int totalQty = goodQty + scrapQty;
            if (totalQty == 0) {
                return "0%";
            }
            return Math.round((goodQty * 100f) / totalQty) + "%";
        } catch (NumberFormatException ignored) {
            return "-";
        }
    }

    private void showAccessDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Access Denied / Data Mismatch")
                .setMessage("The scanned User, Machine, and Runcard combination is not authorized for this production flow.")
                .setPositiveButton("REVIEW SCANS", null)
                .show();
    }

    private void handleScannerIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        String scannedValue = intent.getStringExtra(DATAWEDGE_EXTRA_DATA_STRING);
        if (scannedValue == null) {
            scannedValue = intent.getStringExtra(LEGACY_DATAWEDGE_EXTRA_DATA_STRING);
        }
        if (!TextUtils.isEmpty(scannedValue)) {
            injectScannedValue(scannedValue);
        }
    }

    private void injectScannedValue(String rawValue) {
        String value = cleanScanValue(rawValue);
        if (value.isEmpty()) {
            return;
        }

        ScanViewModel.CurrentScanState targetState = focusedScanState();
        if (targetState == null) {
            ScanViewModel.UiState currentState = viewModel.getUiState().getValue();
            targetState = currentState == null
                    ? ScanViewModel.CurrentScanState.USER
                    : currentState.scanState;
        }

        EditText targetInput = getScanInput(targetState);
        if (viewModel.isValueValidForState(targetState, value)) {
            targetInput.setText(value);
            targetInput.setSelection(targetInput.getText().length());
            viewModel.submitScanValue(targetState, value);
        } else {
            targetInput.setText("");
            targetInput.setError("Invalid " + labelForState(targetState));
            viewModel.submitScanValue(targetState, value);
        }
        scanBuffer.setLength(0);
    }

    private void submitBufferedScan() {
        if (scanBuffer.length() == 0) {
            return;
        }
        injectScannedValue(scanBuffer.toString());
    }

    private void submitFocusedScanField() {
        ScanViewModel.CurrentScanState targetState = focusedScanState();
        if (targetState == null) {
            submitBufferedScan();
            return;
        }
        EditText input = getScanInput(targetState);
        String value = input.getText().toString();
        if (viewModel.isValueValidForState(targetState, value)) {
            viewModel.submitScanValue(targetState, value);
        } else {
            input.setText("");
            input.setError("Invalid " + labelForState(targetState));
            viewModel.submitScanValue(targetState, value);
        }
    }

    private void submitFocusedScanFieldIfValid() {
        ScanViewModel.CurrentScanState targetState = focusedScanState();
        if (targetState == null) {
            return;
        }
        EditText input = getScanInput(targetState);
        String value = input.getText().toString();
        if (viewModel.isValueValidForState(targetState, value)) {
            viewModel.submitScanValue(targetState, value);
        }
    }

    private ScanViewModel.CurrentScanState focusedScanState() {
        if (userIdInput.hasFocus()) {
            return ScanViewModel.CurrentScanState.USER;
        }
        if (machineInput.hasFocus()) {
            return ScanViewModel.CurrentScanState.MACHINE;
        }
        if (runcardInput.hasFocus()) {
            return ScanViewModel.CurrentScanState.RUNCARD;
        }
        return null;
    }

    private EditText getScanInput(ScanViewModel.CurrentScanState targetState) {
        switch (targetState) {
            case MACHINE:
                return machineInput;
            case RUNCARD:
                return runcardInput;
            case USER:
            case COMPLETE:
            default:
                return userIdInput;
        }
    }

    private boolean isScanFieldFocused() {
        return userIdInput.hasFocus() || machineInput.hasFocus() || runcardInput.hasFocus();
    }

    private boolean isQtyInputFocused() {
        return goodQtyInput.hasFocus() || scrapQtyInput.hasFocus();
    }

    private String cleanScanValue(String value) {
        return value == null
                ? ""
                : value.replace("\r", "").replace("\n", "").replace("\t", "").trim();
    }

    private String labelForState(ScanViewModel.CurrentScanState state) {
        switch (state) {
            case USER:
                return "User ID";
            case MACHINE:
                return "Machine";
            case RUNCARD:
                return "Runcard";
            case COMPLETE:
            default:
                return "scan value";
        }
    }

    private String displayOrDash(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void setTextIfDifferent(EditText editText, String value) {
        String nextValue = value == null ? "" : value;
        if (!nextValue.contentEquals(editText.getText())) {
            editText.setText(nextValue);
            editText.setSelection(editText.getText().length());
        }
    }

    private abstract static class SimpleTextWatcher implements android.text.TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // No-op.
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // No-op.
        }
    }
}
