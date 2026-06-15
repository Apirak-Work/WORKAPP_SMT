package com.example.smt;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private static final String CHECK_RUNCARD_LOG = "CheckRuncardDebug";
    private static final String DATAWEDGE_ACTION = "com.symbol.datawedge.data";
    private static final String DATAWEDGE_EXTRA_DATA_STRING = "com.symbol.datawedge.data_string";
    private static final String LEGACY_DATAWEDGE_EXTRA_DATA_STRING = "com.motorolasolutions.emdk.datawedge.data_string";
    private final StringBuilder scanBuffer = new StringBuilder();
    private final BroadcastReceiver dataWedgeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handleScannerIntent(intent);
        }
    };

    private ScanViewModel viewModel;
    private EditText userIdInput;
    private EditText machineInput;
    private EditText runcardInput;
    private LinearLayout miniHeader;
    private TextView miniHeaderUserValue;
    private TextView miniHeaderMachineValue;
    private TextView miniHeaderRuncardValue;
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
    private View productionDetailSection;
    private View modeButtonsSection;
    private LinearLayout operTrackingSection;
    private TextView operTrackingToggle;
    private View operTrackingTableContainer;
    private LinearLayout operRowsTable;
    private LinearLayout checkRuncardPanel;
    private RecyclerView checkRuncardRecyclerView;
    private TextView checkRuncardSubtitle;
    private TextView checkRuncardMessage;
    private ProgressBar checkRuncardProgress;
    private LinearLayout productionPanel;
    private Button verifyButton;
    private Button saveButton;
    private ProgressBar saveProgressBar;
    private Button checkRuncardButton;
    private Button closeCheckRuncardButton;
    private Button checkB2bButton;
    private Button holdForIrrButton;
    private Button holdButton;
    private Button splitButton;
    private Button mergeButton;
    private Button rejectButton;
    private Button testButton;
    private Button[] functionButtons = new Button[0];
    private TextView goodQtyLabel;
    private TextView scrapQtyLabel;
    private EditText goodQtyInput;
    private EditText scrapQtyInput;
    private ScanViewModel.CurrentScanState lastFocusedScanState;
    private final ProductionRetrofitApi retrofitApi = RetrofitProvider.productionApi();
    private RuncardOverviewAdapter checkRuncardAdapter;
    private Call<List<RuncardOverviewModel>> checkRuncardCall;
    private String activeCheckRuncardWorkOrder = "";
    private HoldFunctionManager holdFunctionManager;
    private SplitMergeManager splitMergeManager;
    private MergeManager mergeManager;
    private ScrapRejectManager scrapRejectManager;
    private boolean updatingGoodQtyFromScrap;
    private boolean operTrackingExpanded = true;
    private boolean wasProductionVisible;
    private String lastCustomAlertMessage = "";
    private final List<ActionButtonModule> actionButtonModules = new ArrayList<>();

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
        preventInitialKeyboard();
        viewModel = new ViewModelProvider(this).get(ScanViewModel.class);
        viewModel.getUiState().observe(this, this::render);
        wireActions();
        registerDataWedgeReceiver();
        handleScannerIntent(getIntent());
        findViewById(R.id.scanPanel).post(this::runInitialScanEntryAnimation);
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
        if (checkRuncardCall != null) {
            checkRuncardCall.cancel();
        }
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
        userIdInput = findViewById(R.id.userIdInput);
        machineInput = findViewById(R.id.machineInput);
        runcardInput = findViewById(R.id.runcardInput);
        miniHeader = findViewById(R.id.miniHeader);
        miniHeaderUserValue = findViewById(R.id.miniHeaderUserValue);
        miniHeaderMachineValue = findViewById(R.id.miniHeaderMachineValue);
        miniHeaderRuncardValue = findViewById(R.id.miniHeaderRuncardValue);
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
        productionDetailSection = findViewById(R.id.productionDetailSection);
        modeButtonsSection = findViewById(R.id.modeButtonsSection);
        operTrackingSection = findViewById(R.id.operTrackingSection);
        operTrackingToggle = findViewById(R.id.operTrackingToggle);
        operTrackingTableContainer = findViewById(R.id.operTrackingTableContainer);
        operRowsTable = findViewById(R.id.operRowsTable);
        checkRuncardPanel = findViewById(R.id.checkRuncardPanel);
        checkRuncardRecyclerView = findViewById(R.id.checkRuncardRecyclerView);
        checkRuncardSubtitle = findViewById(R.id.checkRuncardSubtitle);
        checkRuncardMessage = findViewById(R.id.checkRuncardMessage);
        checkRuncardProgress = findViewById(R.id.checkRuncardProgress);
        productionPanel = findViewById(R.id.productionPanel);
        verifyButton = findViewById(R.id.verifyButton);
        saveButton = findViewById(R.id.saveButton);
        saveProgressBar = findViewById(R.id.saveProgressBar);
        checkRuncardButton = findViewById(R.id.checkRuncardButton);
        closeCheckRuncardButton = findViewById(R.id.closeCheckRuncardButton);
        checkB2bButton = findViewById(R.id.checkB2bButton);
        holdForIrrButton = findViewById(R.id.holdForIrrButton);
        holdButton = findViewById(R.id.holdButton);
        splitButton = findViewById(R.id.splitButton);
        mergeButton = findViewById(R.id.mergeButton);
        rejectButton = findViewById(R.id.rejectButton);
        testButton = findViewById(R.id.testButton);
        functionButtons = new Button[]{
                checkRuncardButton,
                checkB2bButton,
                holdForIrrButton,
                holdButton,
                splitButton,
                mergeButton,
                rejectButton,
                testButton
        };
        goodQtyLabel = findViewById(R.id.goodQtyLabel);
        scrapQtyLabel = findViewById(R.id.scrapQtyLabel);
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
        saveButton.setOnClickListener(v -> viewModel.requestSave());
        operTrackingToggle.setOnClickListener(v -> toggleOperTrackingTable());
        scrapQtyInput.setOnClickListener(v -> rejectButton.performClick());
        checkRuncardButton.setOnClickListener(v -> openCheckRuncardByWorkOrder());
        checkB2bButton.setOnClickListener(v -> showSimpleFunctionReady(checkB2bButton, "CHECK B2B module ready"));
        holdForIrrButton.setOnClickListener(v -> showSimpleFunctionReady(holdForIrrButton, "HOLD for IRR module ready"));
        testButton.setOnClickListener(v -> showSimpleFunctionReady(testButton, "TEST module ready"));
        closeCheckRuncardButton.setOnClickListener(v -> closeCheckRuncardByWorkOrder());
        initializeHoldFunctionManager();
        initializePlaceholderActionManagers();
        checkRuncardAdapter = new RuncardOverviewAdapter(this::selectRuncardFromOverview);
        checkRuncardRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        checkRuncardRecyclerView.setAdapter(checkRuncardAdapter);

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
                if (!updatingGoodQtyFromScrap) {
                    autoCalculateGoodQtyFromScrap();
                }
            }
        });
    }

    private void initializeHoldFunctionManager() {
        holdFunctionManager = new HoldFunctionManager(
                this,
                findViewById(R.id.layoutHoldFunction),
                holdButton,
                standardWorkflowViews(),
                new HoldFunctionManager.Callback() {
                    @Override
                    public ProductionDetail getCurrentProductionDetail() {
                        return currentProductionDetail();
                    }

                    @Override
                    public void onOpenRequested() {
                        closeCheckRuncardByWorkOrder();
                        if (splitMergeManager != null && splitMergeManager.isOpen()) {
                            splitMergeManager.closeSplit();
                        }
                        if (mergeManager != null && mergeManager.isOpen()) {
                            mergeManager.close();
                        }
                        if (scrapRejectManager != null && scrapRejectManager.isOpen()) {
                            scrapRejectManager.close();
                        }
                        onInlinePanelOpened(holdButton);
                    }

                    @Override
                    public void onClosed() {
                        onInlinePanelClosed();
                    }

                    @Override
                    public void onHoldActionSucceeded(String actionType) {
                        ProductionDetail detail = currentProductionDetail();
                        if (detail != null && !TextUtils.isEmpty(detail.runcardNo)) {
                            viewModel.switchRuncardAndVerify(detail.runcardNo);
                        }
                    }
                }
        );
        holdFunctionManager.initialize();
    }

    private void initializePlaceholderActionManagers() {
        actionButtonModules.clear();
        scrapRejectManager = new ScrapRejectManager(
                this,
                findViewById(R.id.layoutScrapFunction),
                rejectButton,
                standardWorkflowViews(),
                new ScrapRejectManager.CallbackBridge() {
                    @Override
                    public ProductionDetail getCurrentProductionDetail() {
                        return currentProductionDetail();
                    }

                    @Override
                    public void onOpenRequested() {
                        closeCheckRuncardByWorkOrder();
                        if (holdFunctionManager != null && holdFunctionManager.isOpen()) {
                            holdFunctionManager.close();
                        }
                        if (splitMergeManager != null && splitMergeManager.isOpen()) {
                            splitMergeManager.closeSplit();
                        }
                        if (mergeManager != null && mergeManager.isOpen()) {
                            mergeManager.close();
                        }
                        onInlinePanelOpened(rejectButton);
                    }

                    @Override
                    public void onClosed() {
                        onInlinePanelClosed();
                    }

                    @Override
                    public void onRejectTotalChanged(String totalQty) {
                        setTextIfDifferent(scrapQtyInput, totalQty);
                    }
                }
        );
        scrapRejectManager.initialize();
        splitMergeManager = new SplitMergeManager(
                this,
                findViewById(R.id.layoutSplitFunction),
                splitButton,
                mergeButton,
                standardWorkflowViews(),
                new SplitMergeManager.CallbackBridge() {
                    @Override
                    public ProductionDetail getCurrentProductionDetail() {
                        return currentProductionDetail();
                    }

                    @Override
                    public String getCurrentWorkCenter() {
                        ScanViewModel.UiState state = viewModel.getUiState().getValue();
                        OperTrackingRow activeRow = state == null ? null : activeOperRow(state);
                        return activeRow == null ? "" : displayOrDash(activeRow.wc);
                    }

                    @Override
                    public String getCurrentUserId() {
                        ScanViewModel.UiState state = viewModel.getUiState().getValue();
                        return state == null ? "" : state.userId;
                    }

                    @Override
                    public void onOpenRequested() {
                        closeCheckRuncardByWorkOrder();
                        if (holdFunctionManager != null && holdFunctionManager.isOpen()) {
                            holdFunctionManager.close();
                        }
                        if (mergeManager != null && mergeManager.isOpen()) {
                            mergeManager.close();
                        }
                        if (scrapRejectManager != null && scrapRejectManager.isOpen()) {
                            scrapRejectManager.close();
                        }
                        onInlinePanelOpened(splitButton);
                    }

                    @Override
                    public void onClosed() {
                        onInlinePanelClosed();
                    }

                    @Override
                    public void onSplitSucceeded(String newRuncard) {
                        if (!TextUtils.isEmpty(newRuncard)) {
                            viewModel.switchRuncardAndVerify(newRuncard);
                        }
                    }
                }
        );
        actionButtonModules.add(splitMergeManager);
        mergeManager = new MergeManager(
                this,
                findViewById(R.id.layoutMergeFunction),
                mergeButton,
                standardWorkflowViews(),
                new MergeManager.CallbackBridge() {
                    @Override
                    public ProductionDetail getCurrentProductionDetail() {
                        return currentProductionDetail();
                    }

                    @Override
                    public String getCurrentWorkCenter() {
                        ScanViewModel.UiState state = viewModel.getUiState().getValue();
                        OperTrackingRow activeRow = state == null ? null : activeOperRow(state);
                        return activeRow == null ? "" : displayOrDash(activeRow.wc);
                    }

                    @Override
                    public String getCurrentUserId() {
                        ScanViewModel.UiState state = viewModel.getUiState().getValue();
                        return state == null ? "" : state.userId;
                    }

                    @Override
                    public void onOpenRequested() {
                        closeCheckRuncardByWorkOrder();
                        if (holdFunctionManager != null && holdFunctionManager.isOpen()) {
                            holdFunctionManager.close();
                        }
                        if (splitMergeManager != null && splitMergeManager.isOpen()) {
                            splitMergeManager.closeSplit();
                        }
                        if (scrapRejectManager != null && scrapRejectManager.isOpen()) {
                            scrapRejectManager.close();
                        }
                        onInlinePanelOpened(mergeButton);
                    }

                    @Override
                    public void onClosed() {
                        onInlinePanelClosed();
                    }

                    @Override
                    public void onMergeSucceeded(String mainRuncard) {
                        if (!TextUtils.isEmpty(mainRuncard)) {
                            viewModel.switchRuncardAndVerify(mainRuncard);
                        }
                    }
                }
        );
        actionButtonModules.add(mergeManager);
        for (ActionButtonModule module : actionButtonModules) {
            module.initialize();
        }
        setActiveFunctionButton(null);
    }

    private View[] standardWorkflowViews() {
        return new View[]{
                goodQtyLabel,
                goodQtyInput,
                scrapQtyLabel,
                scrapQtyInput,
                timestampValue,
                saveButton
        };
    }

    private ProductionDetail currentProductionDetail() {
        ScanViewModel.UiState state = viewModel.getUiState().getValue();
        return state == null ? null : state.productionDetail;
    }

    private void onInlinePanelOpened(Button activeButton) {
        if (operTrackingSection != null) {
            operTrackingSection.setVisibility(View.GONE);
        }
        setActiveFunctionButton(activeButton);
    }

    private void onInlinePanelClosed() {
        if (operTrackingSection != null) {
            operTrackingSection.setVisibility(View.VISIBLE);
        }
        operTrackingExpanded = true;
        updateOperTrackingToggle();
        setActiveFunctionButton(null);
    }

    private void setActiveFunctionButton(Button activeButton) {
        int activeColor = ContextCompat.getColor(this, R.color.function_button_active);
        int inactiveColor = ContextCompat.getColor(this, R.color.function_button_default);
        int activeTextColor = ContextCompat.getColor(this, R.color.white);
        int inactiveTextColor = ContextCompat.getColor(this, R.color.link_blue);
        for (Button button : functionButtons) {
            if (button == null) {
                continue;
            }
            boolean active = button == activeButton;
            button.setBackgroundTintList(ColorStateList.valueOf(active ? activeColor : inactiveColor));
            button.setTextColor(active ? activeTextColor : inactiveTextColor);
        }
    }

    private void showSimpleFunctionReady(Button activeButton, String message) {
        ProductionDetail detail = currentProductionDetail();
        if (detail == null) {
            Toast.makeText(this, "Production Detail is not loaded yet", Toast.LENGTH_SHORT).show();
            setActiveFunctionButton(null);
            return;
        }
        setActiveFunctionButton(activeButton);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        setActiveFunctionButton(null);
    }

    private void configureScanInput(EditText editText, ScanViewModel.CurrentScanState targetState) {
        editText.setSelectAllOnFocus(false);
        editText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                if (viewModel.isValueValidForState(targetState, editable.toString())) {
                    clearScanInputError(editText);
                }
            }
        });
    }

    private void preventInitialKeyboard() {
        View root = findViewById(R.id.main);
        if (root != null) {
            root.setFocusableInTouchMode(true);
            root.requestFocus();
        }
        hideKeyboard();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        View currentFocus = getCurrentFocus();
        if (imm != null && currentFocus != null) {
            imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
        }
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
            submitScanInput(targetState, getScanInput(targetState));
            return true;
        }
        return false;
    }

    private void render(ScanViewModel.UiState state) {
        setTextIfDifferent(userIdInput, state.userId);
        setTextIfDifferent(machineInput, state.machineId);
        setTextIfDifferent(runcardInput, state.runcard);
        renderProductionDetail(state);

        verifyButton.setVisibility(state.verifyVisible ? View.VISIBLE : View.GONE);
        verifyButton.setText(state.verifyButtonText);
        verifyButton.setEnabled(state.verifyEnabled);

        renderMiniHeader(state);
        if (state.productionVisible && !wasProductionVisible) {
            operTrackingExpanded = true;
            updateOperTrackingToggle();
        }
        if (!state.productionVisible) {
            operTrackingExpanded = true;
        }
        wasProductionVisible = state.productionVisible;

        productionPanel.setVisibility(state.productionVisible ? View.VISIBLE : View.GONE);
        findViewById(R.id.scanPanel).setVisibility(state.productionVisible ? View.GONE : View.VISIBLE);
        if (!state.productionVisible) {
            closeCheckRuncardByWorkOrder();
            if (holdFunctionManager != null && holdFunctionManager.isOpen()) {
                holdFunctionManager.close();
            }
            if (splitMergeManager != null && splitMergeManager.isOpen()) {
                splitMergeManager.closeSplit();
            }
            if (mergeManager != null && mergeManager.isOpen()) {
                mergeManager.close();
            }
            if (scrapRejectManager != null && scrapRejectManager.isOpen()) {
                scrapRejectManager.close();
            }
            setActiveFunctionButton(null);
        }
        String currentWorkOrder = state.productionDetail == null ? "" : displayOrDash(state.productionDetail.workOrder);
        if (checkRuncardPanel.getVisibility() == View.VISIBLE
                && !activeCheckRuncardWorkOrder.isEmpty()
                && !activeCheckRuncardWorkOrder.equals(currentWorkOrder)) {
            closeCheckRuncardByWorkOrder();
        }
        if (holdFunctionManager != null) {
            holdFunctionManager.refreshHeader();
        }
        setSaveLoadingState(state.saveLoading);
        if (!state.saveLoading) {
            saveButton.setText(state.saveButtonText);
            saveButton.setEnabled(state.saveEnabled);
        }
        if (state.saveLoading || !isSaveErrorMessage(state.scannerMessage)) {
            lastCustomAlertMessage = "";
        }

        timestampValue.setText(
                "Start: " + viewModel.formatTimestamp(state.startDateMillis)
                        + "\nFinish: " + viewModel.formatTimestamp(state.finishDateMillis)
                        + "\nPosting: " + viewModel.formatTimestamp(state.postingDateMillis)
        );
        renderOperTracking(state);
        refreshGoodQtyEditability(state);

        focusForScanState(state);

        if (state.accessDeniedEvent) {
            showAccessDeniedDialog();
            viewModel.consumeDialogEvents();
        } else if (state.saveCompleteEvent) {
            showCustomAlertDialog("Success", "Production Confirmed", true);
            viewModel.consumeDialogEvents();
        } else if (isSaveErrorMessage(state.scannerMessage)
                && !TextUtils.equals(lastCustomAlertMessage, state.scannerMessage)) {
            lastCustomAlertMessage = state.scannerMessage;
            showCustomAlertDialog("Save Error", state.scannerMessage, false);
        }
    }

    private void setSaveLoadingState(boolean isLoading) {
        saveButton.setEnabled(!isLoading);
        saveButton.setText(isLoading ? "กำลังบันทึก..." : "SAVE CONFIRM");
        if (saveProgressBar != null) {
            saveProgressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
    }

    private void runInitialScanEntryAnimation() {
        View scanPanel = findViewById(R.id.scanPanel);
        if (scanPanel.getVisibility() != View.VISIBLE) {
            return;
        }

        animateScanElement(findViewById(R.id.scanSessionHeader), 0L);
        animateScanElement(findViewById(R.id.userInputLayout), 200L);
        animateScanElement(findViewById(R.id.machineInputLayout), 400L);
        animateScanElement(findViewById(R.id.runcardInputLayout), 600L);
        animateScanElement(verifyButton, 800L);
    }

    private void animateScanElement(View view, long startDelayMs) {
        view.setAlpha(0f);
        view.setTranslationY(dp(18));
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(520L)
                .setStartDelay(startDelayMs)
                .start();
    }

    private void showCustomAlertDialog(String title, String message, boolean isSuccess) {
        int titleColor = ContextCompat.getColor(this, isSuccess ? R.color.teal_dark : R.color.danger);
        SpannableString styledTitle = new SpannableString(title);
        styledTitle.setSpan(
                new ForegroundColorSpan(titleColor),
                0,
                styledTitle.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        new AlertDialog.Builder(this)
                .setTitle(styledTitle)
                .setMessage(TextUtils.isEmpty(message) ? (isSuccess ? "Completed" : "API or validation error occurred") : message)
                .setIcon(isSuccess ? android.R.drawable.checkbox_on_background : android.R.drawable.ic_dialog_alert)
                .setCancelable(isSuccess)
                .setPositiveButton(isSuccess ? "OK" : "Close", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private boolean isSaveErrorMessage(String message) {
        return !TextUtils.isEmpty(message)
                && (message.startsWith("Save failed")
                || message.startsWith("Production save failed")
                || message.contains("Unable to save")
                || message.contains("Good Qty + Scrap Qty")
                || message.contains("Unable to determine Receive Qty"));
    }

    private void focusForScanState(ScanViewModel.UiState state) {
        lastFocusedScanState = state.scanState;
    }

    private void requestFieldFocus(EditText editText) {
        editText.requestFocus();
        editText.setSelection(editText.getText().length());
    }

    private void renderMiniHeader(ScanViewModel.UiState state) {
        if (miniHeader == null) {
            return;
        }
        miniHeader.setVisibility(state.productionVisible ? View.VISIBLE : View.GONE);
        miniHeaderUserValue.setText("USER: " + displayOrDash(state.userId));
        miniHeaderMachineValue.setText("MC: " + displayOrDash(state.machineId));
        miniHeaderRuncardValue.setText("RC: " + displayOrDash(state.runcard));
    }

    private void toggleOperTrackingTable() {
        operTrackingExpanded = !operTrackingExpanded;
        updateOperTrackingToggle();
    }

    private void updateOperTrackingToggle() {
        if (operTrackingTableContainer != null) {
            operTrackingTableContainer.setVisibility(operTrackingExpanded ? View.VISIBLE : View.GONE);
        }
        if (operTrackingToggle != null) {
            operTrackingToggle.setText((operTrackingExpanded ? "\u25BC " : "\u25B6 ") + "OPER TRACKING");
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

    private void refreshGoodQtyEditability(ScanViewModel.UiState state) {
        boolean editable = canEditGoodQtyForCurrentOperation(state);
        goodQtyInput.setEnabled(editable);
        goodQtyInput.setFocusable(editable);
        goodQtyInput.setFocusableInTouchMode(editable);
        goodQtyInput.setAlpha(editable ? 1f : 0.72f);
        if (!editable) {
            autoCalculateGoodQtyFromScrap();
        }
    }

    private boolean canEditGoodQtyForCurrentOperation(ScanViewModel.UiState state) {
        OperTrackingRow activeRow = activeOperRow(state);
        if (activeRow == null) {
            return state.productionDetail != null && currentReceiveQty(state) == 0;
        }
        String operText = (displayOrDash(activeRow.oper) + " "
                + displayOrDash(activeRow.wc) + " "
                + displayOrDash(activeRow.description) + " "
                + displayOrDash(activeRow.workCenter)).toUpperCase();
        if (operText.contains("GITPUT")) {
            return true;
        }
        String receive = displayOrDash(activeRow.receive);
        return isFirstOperRow(state, activeRow)
                && (receive.equals("-") || receive.equals("0") || parseQtyOrZero(receive) == 0);
    }

    private boolean isFirstOperRow(ScanViewModel.UiState state, OperTrackingRow targetRow) {
        if (state.operRows == null || targetRow == null) {
            return false;
        }
        for (OperTrackingRow row : state.operRows) {
            if (isSummaryOperRow(row)) {
                continue;
            }
            return row == targetRow;
        }
        return false;
    }

    private void autoCalculateGoodQtyFromScrap() {
        ScanViewModel.UiState state = viewModel.getUiState().getValue();
        if (state == null) {
            return;
        }
        int receiveQty = currentReceiveQty(state);
        int scrapQty = parseQtyOrZero(scrapQtyInput.getText().toString());
        int goodQty = receiveQty - scrapQty;
        updatingGoodQtyFromScrap = true;
        setTextIfDifferent(goodQtyInput, String.valueOf(goodQty));
        updatingGoodQtyFromScrap = false;
    }

    private int currentReceiveQty(ScanViewModel.UiState state) {
        OperTrackingRow activeRow = activeOperRow(state);
        if (activeRow != null) {
            int rowReceive = parseQtyOrZero(activeRow.receive);
            if (rowReceive > 0 || displayOrDash(activeRow.receive).equals("0")) {
                return rowReceive;
            }
        }
        ProductionDetail detail = state.productionDetail;
        if (detail == null) {
            return 0;
        }
        int rcQty = parseQtyOrZero(detail.rcQuantity);
        if (rcQty > 0) {
            return rcQty;
        }
        return parseQtyOrZero(detail.qtyRc);
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
        if (operRowsTable == null) {
            return;
        }
        while (operRowsTable.getChildCount() > 0) {
            operRowsTable.removeViewAt(0);
        }

        if (state.operRows == null || state.operRows.isEmpty()) {
            operRowsTable.addView(createOperPlaceholderRow("No OPER data loaded"));
            return;
        }

        OperTrackingRow activeRow = activeOperRow(state);
        for (OperTrackingRow row : state.operRows) {
            if (isSummaryOperRow(row)) {
                operRowsTable.addView(createOperSummaryRow(row));
            } else {
                operRowsTable.addView(createOperRow(row, row == activeRow));
            }
        }
    }

    private void openCheckRuncardByWorkOrder() {
        ScanViewModel.UiState state = viewModel.getUiState().getValue();
        ProductionDetail detail = state == null ? null : state.productionDetail;
        String workOrderNo = detail == null ? "" : displayOrDash(detail.workOrder);
        if (workOrderNo.equals("-")) {
            Toast.makeText(this, "Work Order is not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }

        activeCheckRuncardWorkOrder = workOrderNo;
        toggleCheckRuncardView(true);
        setActiveFunctionButton(checkRuncardButton);
        checkRuncardSubtitle.setText("WO: " + workOrderNo);
        checkRuncardProgress.setVisibility(View.VISIBLE);
        checkRuncardMessage.setVisibility(View.VISIBLE);
        checkRuncardMessage.setText("Loading Runcards from database...");
        clearCheckRuncardRows();

        if (checkRuncardCall != null) {
            checkRuncardCall.cancel();
        }
        checkRuncardCall = retrofitApi.getRuncardsByWorkOrder(workOrderNo);
        checkRuncardCall.enqueue(new Callback<List<RuncardOverviewModel>>() {
            @Override
            public void onResponse(
                    Call<List<RuncardOverviewModel>> call,
                    Response<List<RuncardOverviewModel>> response
            ) {
                if (!workOrderNo.equals(activeCheckRuncardWorkOrder)) {
                    return;
                }
                checkRuncardProgress.setVisibility(View.GONE);
                if (response.isSuccessful()) {
                    Log.d(CHECK_RUNCARD_LOG, "WO " + workOrderNo + " parsed rows: "
                            + (response.body() == null ? 0 : response.body().size()));
                    renderCheckRuncardRows(response.body());
                    return;
                }
                Log.d(CHECK_RUNCARD_LOG, "WO " + workOrderNo + " failed HTTP " + response.code());
                checkRuncardMessage.setVisibility(View.VISIBLE);
                checkRuncardMessage.setText("Load failed: HTTP " + response.code());
            }

            @Override
            public void onFailure(Call<List<RuncardOverviewModel>> call, Throwable throwable) {
                if (call.isCanceled() || !workOrderNo.equals(activeCheckRuncardWorkOrder)) {
                    return;
                }
                checkRuncardProgress.setVisibility(View.GONE);
                Log.d(CHECK_RUNCARD_LOG, "WO " + workOrderNo + " request failed", throwable);
                checkRuncardMessage.setVisibility(View.VISIBLE);
                checkRuncardMessage.setText("Load failed: " + (throwable.getMessage() == null
                        ? "Unable to load Runcards"
                        : throwable.getMessage()));
            }
        });
    }

    private void closeCheckRuncardByWorkOrder() {
        activeCheckRuncardWorkOrder = "";
        if (checkRuncardCall != null) {
            checkRuncardCall.cancel();
            checkRuncardCall = null;
        }
        toggleCheckRuncardView(false);
        if (checkRuncardProgress != null) {
            checkRuncardProgress.setVisibility(View.GONE);
        }
        if (checkRuncardMessage != null) {
            checkRuncardMessage.setVisibility(View.GONE);
        }
        clearCheckRuncardRows();
        setActiveFunctionButton(null);
    }

    private void toggleCheckRuncardView(boolean show) {
        int checkVisibility = show ? View.VISIBLE : View.GONE;
        if (show && holdFunctionManager != null && holdFunctionManager.isOpen()) {
            holdFunctionManager.close();
        }
        if (show && splitMergeManager != null && splitMergeManager.isOpen()) {
            splitMergeManager.closeSplit();
        }
        if (show && mergeManager != null && mergeManager.isOpen()) {
            mergeManager.close();
        }
        if (show && scrapRejectManager != null && scrapRejectManager.isOpen()) {
            scrapRejectManager.close();
        }

        if (checkRuncardPanel != null) {
            checkRuncardPanel.setVisibility(checkVisibility);
        }
        setStandardWorkflowVisible(!show);
    }

    private void setStandardWorkflowVisible(boolean visible) {
        int standardVisibility = visible ? View.VISIBLE : View.GONE;
        if (operTrackingSection != null) {
            operTrackingSection.setVisibility(standardVisibility);
        }
        if (goodQtyLabel != null) {
            goodQtyLabel.setVisibility(standardVisibility);
        }
        if (goodQtyInput != null) {
            goodQtyInput.setVisibility(standardVisibility);
        }
        if (scrapQtyLabel != null) {
            scrapQtyLabel.setVisibility(standardVisibility);
        }
        if (scrapQtyInput != null) {
            scrapQtyInput.setVisibility(standardVisibility);
        }
        if (timestampValue != null) {
            timestampValue.setVisibility(standardVisibility);
        }
        if (saveButton != null) {
            saveButton.setVisibility(standardVisibility);
        }
    }

    private void clearCheckRuncardRows() {
        if (checkRuncardAdapter != null) {
            checkRuncardAdapter.clear();
        }
    }

    private void renderCheckRuncardRows(List<RuncardOverviewModel> rows) {
        clearCheckRuncardRows();
        if (rows == null || rows.isEmpty()) {
            checkRuncardMessage.setVisibility(View.VISIBLE);
            checkRuncardMessage.setText("No Runcards found for WO " + activeCheckRuncardWorkOrder);
            return;
        }
        checkRuncardMessage.setVisibility(View.GONE);
        for (int i = 0; i < rows.size(); i++) {
            RuncardOverviewModel row = rows.get(i);
            Log.d(CHECK_RUNCARD_LOG, "row " + i
                    + " type=" + row.getType()
                    + ", rc=" + row.getRuncardNo()
                    + ", assy=" + row.getAssy()
                    + ", qty=" + row.getQty()
                    + ", rcAction=" + row.getRcAction()
                    + ", status=" + row.getStatus());
        }
        checkRuncardAdapter.submitRows(rows);
    }

    private void selectRuncardFromOverview(String selectedRuncard) {
        String nextRuncard = cleanScanValue(selectedRuncard);
        if (nextRuncard.isEmpty() || "-".equals(nextRuncard)) {
            Toast.makeText(this, "Runcard number is empty", Toast.LENGTH_SHORT).show();
            return;
        }

        setTextIfDifferent(runcardInput, nextRuncard);
        closeCheckRuncardByWorkOrder();
        viewModel.switchRuncardAndVerify(nextRuncard);
        hideKeyboard();
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
        if (active) {
            row.setBackgroundResource(R.drawable.bg_oper_active_cell);
        }
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

    private View createOperSummaryRow(OperTrackingRow oper) {
        LinearLayout row = createOperRowContainer();
        row.setBackgroundResource(R.drawable.bg_oper_summary_cell);
        row.setClickable(false);
        row.setFocusable(false);
        row.addView(createOperSummaryCell(displayOrDash(oper.oper), 64));
        row.addView(createOperSummaryCell(displayOrDash(oper.wc), 72));
        row.addView(createOperSummaryCell(displayOrDash(oper.description), 248));
        row.addView(createOperSummaryCell(displayOrDash(oper.workCenter), 140));
        row.addView(createOperSummaryCell(displayOrDash(oper.receive), 112));
        row.addView(createOperSummaryCell(displayOrDash(oper.yield), 112));
        row.addView(createOperSummaryCell(displayOrDash(oper.scrap), 112));
        row.addView(createOperSummaryCell(displayOrDash(oper.move), 112));
        row.addView(createOperSummaryCell(displayOrDash(oper.percentYield), 112));
        row.addView(createOperSummaryCell(displayOrDash(oper.receiveDate), 156));
        row.addView(createOperSummaryCell(displayOrDash(oper.confirmDate), 156));
        row.addView(createOperSummaryCell(displayOrDash(oper.en), 72));
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
            cell.setBackgroundResource(R.drawable.bg_oper_active_cell);
            cell.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            cell.setTextColor(ContextCompat.getColor(this, R.color.link_blue));
        }
        return cell;
    }

    private TextView createOperSummaryCell(String text, int widthDp) {
        TextView cell = new TextView(this, null, 0, R.style.OperBodyCell);
        cell.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), dp(42)));
        cell.setBackgroundResource(R.drawable.bg_oper_summary_cell);
        cell.setClickable(false);
        cell.setFocusable(false);
        cell.setStateListAnimator(null);
        cell.setText(text);
        cell.setTextColor(text.toUpperCase().contains("SUMMARY")
                ? ContextCompat.getColor(this, R.color.summary_magenta)
                : ContextCompat.getColor(this, R.color.text_primary));
        cell.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return cell;
    }

    private boolean isSummaryOperRow(OperTrackingRow row) {
        String oper = displayOrDash(row.oper).trim();
        String description = displayOrDash(row.description).toUpperCase();
        return oper.equals("999") || description.contains("SUMMARY");
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
            if (isSummaryOperRow(row)) {
                continue;
            }
            if (!displayOrDash(row.receive).equals("-") && !displayOrDash(row.receive).equals("0")
                    && displayOrDash(row.yield).equals("-")) {
                return row;
            }
        }
        for (int index = state.operRows.size() - 1; index >= 0; index--) {
            OperTrackingRow row = state.operRows.get(index);
            if (!isSummaryOperRow(row)) {
                return row;
            }
        }
        return null;
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
            clearScanInputError(targetInput);
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
        submitScanInput(targetState, input);
    }

    private void submitScanInput(ScanViewModel.CurrentScanState targetState, EditText input) {
        String value = input.getText().toString();
        if (viewModel.isValueValidForState(targetState, value)) {
            clearScanInputError(input);
            viewModel.submitScanValue(targetState, value);
        } else {
            input.setText("");
            input.setError("Invalid " + labelForState(targetState));
            viewModel.submitScanValue(targetState, value);
        }
    }

    private void clearScanInputError(EditText input) {
        input.setError(null);
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

    private int parseQtyOrZero(String value) {
        String normalized = value == null ? "" : value.replace(",", "").trim();
        if (normalized.isEmpty() || normalized.equals("-") || normalized.equalsIgnoreCase("null")) {
            return 0;
        }
        try {
            return Math.round(Float.parseFloat(normalized));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @SuppressWarnings("deprecation")
    private void playSuccessFeedback() {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(200L, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(200L);
        }
    }

    @SuppressWarnings("deprecation")
    private void playErrorFeedback() {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern = {0L, 500L, 200L, 500L};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(pattern, -1);
            }
        }

        ToneGenerator toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 350);
        new Handler(Looper.getMainLooper()).postDelayed(toneGenerator::release, 450L);
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
