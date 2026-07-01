package com.example.smt;

import android.content.Context;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smt.network.ProductionRetrofitApi;
import com.example.smt.network.RetrofitProvider;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Collections;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

final class SplitMergeManager implements ActionButtonModule {
    interface CallbackBridge {
        ProductionDetail getCurrentProductionDetail();

        String getCurrentWorkCenter();

        default String getCurrentOperation() {
            return "";
        }

        String getCurrentUserId();

        default void onOpenRequested() {
        }

        default void onClosed() {
        }

        default void onSplitSucceeded(String newRuncard) {
        }
    }

    private final Context context;
    private final View rootView;
    private final Button splitButton;
    private final Button mergeButton;
    private final View[] standardWorkflowViews;
    private final CallbackBridge callback;
    private final ProductionRetrofitApi api = RetrofitProvider.productionApi();
    private final TextView currentRuncard;
    private final TextView currentAssy;
    private final TextView motherQtyValue;
    private final Spinner customerSpinner;
    private final TextView newRuncardPreview;
    private final TextView newAssyPreview;
    private final EditText newQtyInput;
    private final TextView remainingMotherQtyValue;
    private final TextView validationMessage;
    private final Button confirmButton;
    private final Button closeButton;
    private final RecyclerView historyRecyclerView;
    private final TextView historyEmptyText;
    private SplitHistoryAdapter historyAdapter;
    private Call<ValidationResponse> validationCall;
    private Call<SplitResponse> splitCall;
    private Call<List<SplitHistoryItem>> historyCall;

    SplitMergeManager(
            Context context,
            View rootView,
            Button splitButton,
            Button mergeButton,
            View[] standardWorkflowViews,
            CallbackBridge callback
    ) {
        this.context = context;
        this.rootView = rootView;
        this.splitButton = splitButton;
        this.mergeButton = mergeButton;
        this.standardWorkflowViews = standardWorkflowViews;
        this.callback = callback;
        this.currentRuncard = rootView.findViewById(R.id.splitCurrentRuncard);
        this.currentAssy = rootView.findViewById(R.id.splitCurrentAssy);
        this.motherQtyValue = rootView.findViewById(R.id.splitMotherQtyValue);
        this.customerSpinner = rootView.findViewById(R.id.splitCustomerSpinner);
        this.newRuncardPreview = rootView.findViewById(R.id.splitNewRuncardPreview);
        this.newAssyPreview = rootView.findViewById(R.id.splitNewAssyPreview);
        this.newQtyInput = rootView.findViewById(R.id.splitNewQtyInput);
        this.remainingMotherQtyValue = rootView.findViewById(R.id.splitRemainingMotherQtyValue);
        this.validationMessage = rootView.findViewById(R.id.splitValidationMessage);
        this.confirmButton = rootView.findViewById(R.id.splitConfirmButton);
        this.closeButton = rootView.findViewById(R.id.splitCloseButton);
        this.historyRecyclerView = rootView.findViewById(R.id.splitHistoryRecyclerView);
        this.historyEmptyText = rootView.findViewById(R.id.splitHistoryEmptyText);
    }

    @Override
    public void initialize() {
        splitButton.setOnClickListener(v -> openSplit());
        mergeButton.setOnClickListener(v -> openMerge());

        historyAdapter = new SplitHistoryAdapter();
        historyRecyclerView.setLayoutManager(new LinearLayoutManager(context));
        historyRecyclerView.setAdapter(historyAdapter);
        historyEmptyText.setVisibility(View.VISIBLE);
        setupCustomerSpinner();

        closeButton.setOnClickListener(v -> closeSplit());
        newQtyInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                ProductionDetail detail = callback.getCurrentProductionDetail();
                updateSplitQtyState(detail);
            }
        });
    }

    private void setupCustomerSpinner() {
        ArrayAdapter<String> customerAdapter = new ArrayAdapter<>(
                context,
                android.R.layout.simple_spinner_item,
                new String[]{"Microchip", "onsemi"}
        );
        customerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        customerSpinner.setAdapter(customerAdapter);
        customerSpinner.setSelection(0);
        customerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ProductionDetail detail = callback.getCurrentProductionDetail();
                if (detail != null) {
                    newAssyPreview.setText(generateClientAssyPreview(detail.assyLot, getSelectedCustomerType()));
                    updateSplitQtyState(detail);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void openSplit() {
        ProductionDetail detail = callback.getCurrentProductionDetail();
        if (detail == null) {
            Toast.makeText(context, "Production Detail is not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }

        callback.onOpenRequested();
        setStandardWorkflowVisible(false);
        rootView.setVisibility(View.VISIBLE);
        resetSplitInputs();
        historyEmptyText.setVisibility(View.VISIBLE);

        currentRuncard.setText(displayOrDash(detail.runcardNo));
        currentAssy.setText(displayOrDash(detail.assyLot));
        motherQtyValue.setText(displayOrDash(detail.rcQuantity));
        newRuncardPreview.setText(generateClientRuncardPreview());
        customerSpinner.setSelection(0);
        newAssyPreview.setText(generateClientAssyPreview(detail.assyLot, getSelectedCustomerType()));
        remainingMotherQtyValue.setText(displayOrDash(detail.rcQuantity));
        remainingMotherQtyValue.setTextColor(color(R.color.link_blue));
        updateSplitQtyState(detail);

        confirmButton.setOnClickListener(v -> submitSplit(detail, newQtyInput, confirmButton, historyEmptyText));
        loadSplitHistory(detail.runcardNo, historyEmptyText);
    }

    void closeSplit() {
        cancelCalls();
        rootView.setVisibility(View.GONE);
        setStandardWorkflowVisible(true);
        resetSplitInputs();
        callback.onClosed();
    }

    boolean isOpen() {
        return rootView.getVisibility() == View.VISIBLE;
    }

    private void cancelCalls() {
        if (historyCall != null) {
            historyCall.cancel();
            historyCall = null;
        }
        if (validationCall != null) {
            validationCall.cancel();
            validationCall = null;
        }
        if (splitCall != null) {
            splitCall.cancel();
            splitCall = null;
        }
    }

    private void resetSplitInputs() {
        newQtyInput.setText("");
        newQtyInput.setError(null);
        confirmButton.setEnabled(false);
        hideValidationMessage();
        remainingMotherQtyValue.setText("-");
        remainingMotherQtyValue.setTextColor(color(R.color.link_blue));
        if (historyAdapter != null) {
            historyAdapter.submitRows(Collections.emptyList());
        }
    }

    private void setStandardWorkflowVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        for (View view : standardWorkflowViews) {
            if (view != null) {
                view.setVisibility(visibility);
            }
        }
    }

    private void loadSplitHistory(String runcardNo, TextView emptyText) {
        if (historyCall != null) {
            historyCall.cancel();
        }
        historyCall = api.getSplitHistory(displayOrDash(runcardNo));
        historyCall.enqueue(new Callback<List<SplitHistoryItem>>() {
            @Override
            public void onResponse(
                    Call<List<SplitHistoryItem>> call,
                    Response<List<SplitHistoryItem>> response
            ) {
                historyCall = null;
                List<SplitHistoryItem> body = response.body();
                if (!response.isSuccessful() || body == null || body.isEmpty()) {
                    historyAdapter.submitRows(Collections.emptyList());
                    emptyText.setVisibility(View.VISIBLE);
                    return;
                }

                List<SplitHistoryRow> rows = new ArrayList<>();
                for (SplitHistoryItem item : body) {
                    rows.add(new SplitHistoryRow(
                            displayOrDash(item.runcard),
                            displayOrDash(item.assyLot),
                            item.qty == null ? "-" : String.valueOf(item.qty),
                            displayOrDash(item.mother),
                            item.motherQty == null ? "-" : String.valueOf(item.motherQty),
                            displayOrDash(item.wc),
                            displayOrDash(item.cdate)
                    ));
                }
                historyAdapter.submitRows(rows);
                emptyText.setVisibility(View.GONE);
            }

            @Override
            public void onFailure(Call<List<SplitHistoryItem>> call, Throwable throwable) {
                if (call.isCanceled()) {
                    return;
                }
                historyCall = null;
                historyAdapter.submitRows(Collections.emptyList());
                emptyText.setVisibility(View.VISIBLE);
                Toast.makeText(context, "Unable to load split history", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateSplitQtyState(ProductionDetail detail) {
        if (detail == null) {
            confirmButton.setEnabled(false);
            remainingMotherQtyValue.setText("-");
            return;
        }

        newAssyPreview.setText(generateClientAssyPreview(detail.assyLot, getSelectedCustomerType()));
        hideValidationMessage();

        int motherQty = parseIntOrZero(detail.rcQuantity);
        String qtyText = newQtyInput.getText().toString().trim();
        if (TextUtils.isEmpty(qtyText)) {
            confirmButton.setEnabled(false);
            remainingMotherQtyValue.setText(String.valueOf(motherQty));
            remainingMotherQtyValue.setTextColor(color(R.color.link_blue));
            return;
        }

        int splitQty;
        try {
            splitQty = Integer.parseInt(qtyText);
        } catch (NumberFormatException ex) {
            confirmButton.setEnabled(false);
            remainingMotherQtyValue.setText("-");
            remainingMotherQtyValue.setTextColor(color(R.color.danger));
            return;
        }

        int remaining = motherQty - splitQty;
        remainingMotherQtyValue.setText(String.valueOf(remaining));
        // Valid only when split is positive and strictly less than a known-positive mother qty.
        // A mother qty <= 0 (missing/unparseable) is treated as invalid so the mother can never hit 0.
        if (splitQty <= 0 || motherQty <= 0 || splitQty >= motherQty) {
            confirmButton.setEnabled(false);
            remainingMotherQtyValue.setTextColor(color(R.color.danger));
            return;
        }

        remainingMotherQtyValue.setTextColor(color(R.color.link_blue));
        confirmButton.setEnabled(true);
    }

    private void submitSplit(
            ProductionDetail detail,
            EditText qtyInput,
            Button addButton,
            TextView historyEmptyText
    ) {
        String qtyText = qtyInput.getText().toString().trim();
        if (TextUtils.isEmpty(qtyText)) {
            qtyInput.setError("New Qty is required");
            qtyInput.requestFocus();
            return;
        }

        int splitQty;
        try {
            splitQty = Integer.parseInt(qtyText);
        } catch (NumberFormatException ex) {
            qtyInput.setError("New Qty must be a number");
            qtyInput.requestFocus();
            return;
        }

        int motherQty = parseIntOrZero(detail.rcQuantity);
        if (splitQty <= 0 || motherQty <= 0 || splitQty >= motherQty) {
            qtyInput.setError("Split Qty must be greater than 0 and less than Mother QTY");
            qtyInput.requestFocus();
            updateSplitQtyState(detail);
            return;
        }

        addButton.setEnabled(false);
        hideValidationMessage();
        SplitRequest request = new SplitRequest(
                displayOrDash(detail.workOrder),
                displayOrDash(detail.material),
                displayOrDash(detail.runcardNo),
                displayOrDash(detail.assyLot),
                splitQty,
                motherQty,
                displayOrDash(callback.getCurrentWorkCenter()),
                displayOrDash(callback.getCurrentOperation()),
                displayOrDash(callback.getCurrentUserId()),
                getSelectedCustomerType()
        );

        if (validationCall != null) {
            validationCall.cancel();
        }
        validationCall = api.validateRuncard(request.motherRuncard, request.workCenter);
        validationCall.enqueue(new Callback<ValidationResponse>() {
            @Override
            public void onResponse(Call<ValidationResponse> call, Response<ValidationResponse> response) {
                validationCall = null;
                ValidationResponse body = response.body();
                if (!response.isSuccessful() || body == null) {
                    addButton.setEnabled(true);
                    showValidationErrors(Collections.singletonList("Validation failed: HTTP " + response.code()));
                    updateSplitQtyState(detail);
                    return;
                }

                if (!body.isValid) {
                    addButton.setEnabled(true);
                    showValidationErrors(body.errorMessages == null
                            ? Collections.singletonList("Runcard validation failed.")
                            : body.errorMessages);
                    updateSplitQtyState(detail);
                    return;
                }

                executeSplit(detail, request, addButton, historyEmptyText);
            }

            @Override
            public void onFailure(Call<ValidationResponse> call, Throwable throwable) {
                if (call.isCanceled()) {
                    return;
                }
                validationCall = null;
                addButton.setEnabled(true);
                showValidationErrors(Collections.singletonList("Validation failed: "
                        + (throwable.getMessage() == null ? "Unable to connect to backend" : throwable.getMessage())));
                updateSplitQtyState(detail);
            }
        });
    }

    private void executeSplit(
            ProductionDetail detail,
            SplitRequest request,
            Button addButton,
            TextView historyEmptyText
    ) {
        if (splitCall != null) {
            splitCall.cancel();
        }
        splitCall = api.splitRuncard(request);
        splitCall.enqueue(new Callback<SplitResponse>() {
            @Override
            public void onResponse(Call<SplitResponse> call, Response<SplitResponse> response) {
                splitCall = null;
                addButton.setEnabled(true);
                SplitResponse body = response.body();
                if (!response.isSuccessful() || body == null || !body.success) {
                    Toast.makeText(context, "Split failed: HTTP " + response.code(), Toast.LENGTH_LONG).show();
                    return;
                }

                Toast.makeText(context, body.message == null ? "Split saved" : body.message, Toast.LENGTH_SHORT).show();
                historyAdapter.addRow(new SplitHistoryRow(
                        displayOrDash(body.newRuncard),
                        displayOrDash(body.newAssy),
                        String.valueOf(body.splitQty),
                        displayOrDash(detail.runcardNo),
                        String.valueOf(body.motherQty),
                        displayOrDash(body.workCenter),
                        displayOrDash(body.cdate)
                ));
                historyEmptyText.setVisibility(View.GONE);
                callback.onSplitSucceeded(body.newRuncard);
                closeSplit();
            }

            @Override
            public void onFailure(Call<SplitResponse> call, Throwable throwable) {
                if (call.isCanceled()) {
                    return;
                }
                splitCall = null;
                addButton.setEnabled(true);
                Toast.makeText(
                        context,
                        "Split failed: " + (throwable.getMessage() == null
                                ? "Unable to connect to backend"
                                : throwable.getMessage()),
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }

    private void showValidationErrors(java.util.List<String> errorMessages) {
        String message = TextUtils.join("\n", errorMessages);
        validationMessage.setText(TextUtils.isEmpty(message) ? "Runcard validation failed." : message);
        validationMessage.setVisibility(View.VISIBLE);
    }

    private void hideValidationMessage() {
        validationMessage.setText("");
        validationMessage.setVisibility(View.GONE);
    }

    private int color(int colorRes) {
        return context.getResources().getColor(colorRes, context.getTheme());
    }

    private void openMerge() {
        if (callback.getCurrentProductionDetail() == null) {
            Toast.makeText(context, "Production Detail is not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(context, "MERGE/COMBINE module ready", Toast.LENGTH_SHORT).show();
    }

    private String getSelectedCustomerType() {
        Object selected = customerSpinner.getSelectedItem();
        String value = selected == null ? "Microchip" : selected.toString();
        return value.equalsIgnoreCase("onsemi") ? "onsemi" : "microchip";
    }

    private String generateClientRuncardPreview() {
        return new SimpleDateFormat("yyMMdd", Locale.US).format(new Date()) + "XXXX";
    }

    private String generateClientAssyPreview(String motherAssy, String customerType) {
        String base = displayOrDash(motherAssy);
        if ("onsemi".equalsIgnoreCase(customerType) && base.startsWith("S4")) {
            base = "S5" + base.substring(2);
        }
        if ("onsemi".equalsIgnoreCase(customerType)) {
            return base + "A";
        }
        return "-".equals(base) ? "CHILDA" : base + "-A";
    }

    private int parseIntOrZero(String value) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String displayOrDash(String value) {
        return TextUtils.isEmpty(value) ? "-" : value;
    }
}
