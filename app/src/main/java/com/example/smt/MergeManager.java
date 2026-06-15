package com.example.smt;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

final class MergeManager implements ActionButtonModule {
    interface CallbackBridge {
        ProductionDetail getCurrentProductionDetail();

        String getCurrentWorkCenter();

        String getCurrentUserId();

        default void onOpenRequested() {
        }

        default void onClosed() {
        }

        default void onMergeSucceeded(String mainRuncard) {
        }
    }

    private final Context context;
    private final View rootView;
    private final Button openButton;
    private final View[] standardWorkflowViews;
    private final CallbackBridge callback;
    private final ProductionRetrofitApi api = RetrofitProvider.productionApi();
    private final EditText mainRuncardInput;
    private final EditText sourceRuncardInput;
    private final Button addSourceButton;
    private final Button closeButton;
    private final Button confirmButton;
    private final RecyclerView sourceRecyclerView;
    private final TextView emptyText;
    private final TextView totalQtyText;
    private final MergeSourceAdapter adapter = new MergeSourceAdapter();
    private final List<MergeSourceRow> sourceRows = new ArrayList<>();
    private Call<ProductionDetail> sourceDetailCall;
    private Call<MergeResponse> mergeCall;

    MergeManager(
            Context context,
            View rootView,
            Button openButton,
            View[] standardWorkflowViews,
            CallbackBridge callback
    ) {
        this.context = context;
        this.rootView = rootView;
        this.openButton = openButton;
        this.standardWorkflowViews = standardWorkflowViews;
        this.callback = callback;
        this.mainRuncardInput = rootView.findViewById(R.id.mergeMainRuncardInput);
        this.sourceRuncardInput = rootView.findViewById(R.id.mergeSourceRuncardInput);
        this.addSourceButton = rootView.findViewById(R.id.mergeAddSourceButton);
        this.closeButton = rootView.findViewById(R.id.mergeCloseButton);
        this.confirmButton = rootView.findViewById(R.id.mergeConfirmButton);
        this.sourceRecyclerView = rootView.findViewById(R.id.mergeSourceRecyclerView);
        this.emptyText = rootView.findViewById(R.id.mergeEmptyText);
        this.totalQtyText = rootView.findViewById(R.id.mergeTotalQtyText);
    }

    @Override
    public void initialize() {
        sourceRecyclerView.setLayoutManager(new LinearLayoutManager(context));
        sourceRecyclerView.setAdapter(adapter);
        openButton.setOnClickListener(v -> open());
        closeButton.setOnClickListener(v -> close());
        addSourceButton.setOnClickListener(v -> addSourceRuncard());
        confirmButton.setOnClickListener(v -> confirmMerge());
        renderRows();
    }

    void open() {
        ProductionDetail detail = callback.getCurrentProductionDetail();
        if (detail == null || displayOrDash(detail.runcardNo).equals("-")) {
            Toast.makeText(context, "Production Detail is not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }

        callback.onOpenRequested();
        setStandardWorkflowVisible(false);
        rootView.setVisibility(View.VISIBLE);
        resetInputs();
        mainRuncardInput.setText(displayOrDash(detail.runcardNo));
    }

    void close() {
        cancelCalls();
        rootView.setVisibility(View.GONE);
        setStandardWorkflowVisible(true);
        resetInputs();
        callback.onClosed();
    }

    boolean isOpen() {
        return rootView.getVisibility() == View.VISIBLE;
    }

    private void addSourceRuncard() {
        String main = normalize(mainRuncardInput.getText().toString());
        String source = normalize(sourceRuncardInput.getText().toString());
        if (TextUtils.isEmpty(main)) {
            mainRuncardInput.setError("Main Runcard is required");
            mainRuncardInput.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(source)) {
            sourceRuncardInput.setError("Source Runcard is required");
            sourceRuncardInput.requestFocus();
            return;
        }
        if (main.equals(source)) {
            sourceRuncardInput.setError("Source cannot be the same as Main Runcard");
            sourceRuncardInput.requestFocus();
            return;
        }
        for (MergeSourceRow row : sourceRows) {
            if (row.runcard.equals(source)) {
                sourceRuncardInput.setError("Source Runcard already added");
                sourceRuncardInput.requestFocus();
                return;
            }
        }

        addSourceButton.setEnabled(false);
        if (sourceDetailCall != null) {
            sourceDetailCall.cancel();
        }
        sourceDetailCall = api.getProductionDetail(source);
        sourceDetailCall.enqueue(new Callback<ProductionDetail>() {
            @Override
            public void onResponse(Call<ProductionDetail> call, Response<ProductionDetail> response) {
                sourceDetailCall = null;
                addSourceButton.setEnabled(true);
                ProductionDetail detail = response.body();
                if (!response.isSuccessful() || detail == null) {
                    sourceRuncardInput.setError("Source Runcard not found");
                    sourceRuncardInput.requestFocus();
                    return;
                }

                sourceRows.add(new MergeSourceRow(source, parseIntOrZero(detail.rcQuantity)));
                sourceRuncardInput.setText("");
                sourceRuncardInput.setError(null);
                renderRows();
            }

            @Override
            public void onFailure(Call<ProductionDetail> call, Throwable throwable) {
                if (call.isCanceled()) {
                    return;
                }
                sourceDetailCall = null;
                addSourceButton.setEnabled(true);
                Toast.makeText(context, "Unable to load source runcard", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmMerge() {
        String main = normalize(mainRuncardInput.getText().toString());
        if (TextUtils.isEmpty(main)) {
            mainRuncardInput.setError("Main Runcard is required");
            mainRuncardInput.requestFocus();
            return;
        }
        if (sourceRows.isEmpty()) {
            Toast.makeText(context, "Add at least one source runcard", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> sources = new ArrayList<>();
        for (MergeSourceRow row : sourceRows) {
            sources.add(row.runcard);
        }

        confirmButton.setEnabled(false);
        MergeRequest request = new MergeRequest(
                main,
                sources,
                displayOrDash(callback.getCurrentWorkCenter()),
                displayOrDash(callback.getCurrentUserId())
        );
        if (mergeCall != null) {
            mergeCall.cancel();
        }
        mergeCall = api.mergeRuncards(request);
        mergeCall.enqueue(new Callback<MergeResponse>() {
            @Override
            public void onResponse(Call<MergeResponse> call, Response<MergeResponse> response) {
                mergeCall = null;
                confirmButton.setEnabled(true);
                MergeResponse body = response.body();
                if (!response.isSuccessful() || body == null || !body.success) {
                    Toast.makeText(context, "Merge failed: HTTP " + response.code(), Toast.LENGTH_LONG).show();
                    return;
                }

                Toast.makeText(context, body.message == null ? "Merge saved" : body.message, Toast.LENGTH_SHORT).show();
                callback.onMergeSucceeded(main);
                close();
            }

            @Override
            public void onFailure(Call<MergeResponse> call, Throwable throwable) {
                if (call.isCanceled()) {
                    return;
                }
                mergeCall = null;
                confirmButton.setEnabled(true);
                Toast.makeText(
                        context,
                        "Merge failed: " + (throwable.getMessage() == null
                                ? "Unable to connect to backend"
                                : throwable.getMessage()),
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }

    private void renderRows() {
        adapter.submitRows(sourceRows);
        emptyText.setVisibility(sourceRows.isEmpty() ? View.VISIBLE : View.GONE);
        int total = 0;
        for (MergeSourceRow row : sourceRows) {
            total += row.qty;
        }
        totalQtyText.setText("Total Merged QTY: " + total);
    }

    private void resetInputs() {
        sourceRows.clear();
        mainRuncardInput.setText("");
        mainRuncardInput.setError(null);
        sourceRuncardInput.setText("");
        sourceRuncardInput.setError(null);
        addSourceButton.setEnabled(true);
        confirmButton.setEnabled(true);
        renderRows();
    }

    private void cancelCalls() {
        if (sourceDetailCall != null) {
            sourceDetailCall.cancel();
            sourceDetailCall = null;
        }
        if (mergeCall != null) {
            mergeCall.cancel();
            mergeCall = null;
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

    private int parseIntOrZero(String value) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.US);
    }

    private String displayOrDash(String value) {
        return TextUtils.isEmpty(value) ? "-" : value;
    }
}
