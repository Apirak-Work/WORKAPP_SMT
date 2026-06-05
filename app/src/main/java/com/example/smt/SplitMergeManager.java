package com.example.smt;

import android.app.Dialog;
import android.content.Context;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

final class SplitMergeManager implements ActionButtonModule {
    interface CallbackBridge {
        ProductionDetail getCurrentProductionDetail();

        String getCurrentWorkCenter();

        String getCurrentUserId();

        default void onSplitSucceeded(String newRuncard) {
        }
    }

    private final Context context;
    private final Button splitButton;
    private final Button mergeButton;
    private final CallbackBridge callback;
    private final ProductionRetrofitApi api = RetrofitProvider.productionApi();
    private SplitHistoryAdapter historyAdapter;
    private Call<SplitResponse> splitCall;

    SplitMergeManager(Context context, Button splitButton, Button mergeButton, CallbackBridge callback) {
        this.context = context;
        this.splitButton = splitButton;
        this.mergeButton = mergeButton;
        this.callback = callback;
    }

    @Override
    public void initialize() {
        splitButton.setOnClickListener(v -> openSplit());
        mergeButton.setOnClickListener(v -> openMerge());
    }

    private void openSplit() {
        ProductionDetail detail = callback.getCurrentProductionDetail();
        if (detail == null) {
            Toast.makeText(context, "Production Detail is not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View content = LayoutInflater.from(context).inflate(R.layout.dialog_split_function, null);
        dialog.setContentView(content);

        TextView currentRuncard = content.findViewById(R.id.splitCurrentRuncard);
        TextView currentAssy = content.findViewById(R.id.splitCurrentAssy);
        TextView currentQty = content.findViewById(R.id.splitCurrentQty);
        TextView newRuncardPreview = content.findViewById(R.id.splitNewRuncardPreview);
        TextView newAssyPreview = content.findViewById(R.id.splitNewAssyPreview);
        EditText newQtyInput = content.findViewById(R.id.splitNewQtyInput);
        Button addButton = content.findViewById(R.id.splitAddButton);
        Button closeButton = content.findViewById(R.id.splitCloseButton);
        RecyclerView historyRecyclerView = content.findViewById(R.id.splitHistoryRecyclerView);

        historyAdapter = new SplitHistoryAdapter();
        historyRecyclerView.setLayoutManager(new LinearLayoutManager(context));
        historyRecyclerView.setAdapter(historyAdapter);

        currentRuncard.setText("RC: " + displayOrDash(detail.runcardNo));
        currentAssy.setText("ASSY: " + displayOrDash(detail.assyLot));
        currentQty.setText("QTY: " + displayOrDash(detail.rcQuantity));
        newRuncardPreview.setText(generateClientRuncardPreview());
        newAssyPreview.setText(generateClientAssyPreview(detail.assyLot));

        newQtyInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                newAssyPreview.setText(generateClientAssyPreview(detail.assyLot));
            }
        });

        addButton.setOnClickListener(v -> submitSplit(dialog, detail, newQtyInput, addButton));
        closeButton.setOnClickListener(v -> {
            if (splitCall != null) {
                splitCall.cancel();
                splitCall = null;
            }
            dialog.dismiss();
        });

        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(
                    (int) (context.getResources().getDisplayMetrics().widthPixels * 0.96f),
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT
            );
        }
    }

    private void submitSplit(Dialog dialog, ProductionDetail detail, EditText qtyInput, Button addButton) {
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
        if (splitQty <= 0 || (motherQty > 0 && splitQty >= motherQty)) {
            qtyInput.setError("New Qty must be greater than 0 and less than current Qty");
            qtyInput.requestFocus();
            return;
        }

        addButton.setEnabled(false);
        SplitRequest request = new SplitRequest(
                displayOrDash(detail.workOrder),
                displayOrDash(detail.material),
                displayOrDash(detail.runcardNo),
                displayOrDash(detail.assyLot),
                splitQty,
                motherQty,
                displayOrDash(callback.getCurrentWorkCenter()),
                displayOrDash(callback.getCurrentUserId()),
                inferCustomerType(detail)
        );

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
                callback.onSplitSucceeded(body.newRuncard);
                dialog.dismiss();
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

    private void openMerge() {
        if (callback.getCurrentProductionDetail() == null) {
            Toast.makeText(context, "Production Detail is not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(context, "MERGE/COMBINE module ready", Toast.LENGTH_SHORT).show();
    }

    private String inferCustomerType(ProductionDetail detail) {
        String assy = detail.assyLot == null ? "" : detail.assyLot.toUpperCase(Locale.US);
        if (assy.startsWith("S4") || assy.startsWith("S5")) {
            return "onsemi";
        }
        return "microchip";
    }

    private String generateClientRuncardPreview() {
        return new SimpleDateFormat("yyMMdd", Locale.US).format(new Date()) + "XXXX";
    }

    private String generateClientAssyPreview(String motherAssy) {
        String base = displayOrDash(motherAssy);
        if (base.startsWith("S4")) {
            base = "S5" + base.substring(2);
        }
        return base + "A";
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
