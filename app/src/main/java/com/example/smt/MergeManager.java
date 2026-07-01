package com.example.smt;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smt.models.MergeRequest;
import com.example.smt.network.ProductionRetrofitApi;
import com.example.smt.network.RetrofitProvider;
import com.example.smt.utils.ErrorHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

final class MergeManager implements ActionButtonModule {
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
    private final Spinner sourceRuncardSpinner;
    private final Button addSourceButton;
    private final Button closeButton;
    private final Button confirmButton;
    private final RecyclerView sourceRecyclerView;
    private final TextView emptyText;
    private final TextView totalQtyText;
    private final MergeSourceAdapter adapter = new MergeSourceAdapter(this::deleteSourceRow);
    private final List<MergeSourceRow> sourceRows = new ArrayList<>();
    private final List<MergeSourceRow> availableSourceRows = new ArrayList<>();
    private ArrayAdapter<String> sourceSpinnerAdapter;
    private Call<List<RuncardOverviewModel>> siblingRuncardsCall;
    private Call<ResponseBody> mergeCall;

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
        this.sourceRuncardSpinner = rootView.findViewById(R.id.mergeSourceRuncardSpinner);
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
        sourceSpinnerAdapter = new ArrayAdapter<>(
                context,
                android.R.layout.simple_spinner_item,
                new ArrayList<>()
        );
        sourceSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sourceRuncardSpinner.setAdapter(sourceSpinnerAdapter);
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
        loadSiblingRuncards(detail);
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
        int selectedIndex = sourceRuncardSpinner.getSelectedItemPosition() - 1;
        if (TextUtils.isEmpty(main)) {
            mainRuncardInput.setError("Main Runcard is required");
            mainRuncardInput.requestFocus();
            return;
        }
        if (selectedIndex < 0 || selectedIndex >= availableSourceRows.size()) {
            Toast.makeText(context, "Select Source Runcard to Merge", Toast.LENGTH_SHORT).show();
            return;
        }

        MergeSourceRow selectedSource = availableSourceRows.get(selectedIndex);
        String source = normalize(selectedSource.runcard);
        if (TextUtils.isEmpty(source)) {
            Toast.makeText(context, "Source Runcard is required", Toast.LENGTH_SHORT).show();
            return;
        }
        if (main.equals(source)) {
            Toast.makeText(context, "Source cannot be the same as Main Runcard", Toast.LENGTH_SHORT).show();
            return;
        }
        for (MergeSourceRow row : sourceRows) {
            if (row.runcard.equals(source)) {
                Toast.makeText(context, "Source Runcard already added", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        sourceRows.add(selectedSource);
        sourceRuncardSpinner.setSelection(0);
        renderRows();
    }

    private void loadSiblingRuncards(ProductionDetail detail) {
        String workOrder = displayOrDash(detail.workOrder);
        String main = normalize(detail.runcardNo);
        if (workOrder.equals("-")) {
            bindSourceOptions(new ArrayList<>());
            Toast.makeText(context, "Work Order is not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }

        addSourceButton.setEnabled(false);
        bindSourceOptions(new ArrayList<>());
        if (siblingRuncardsCall != null) {
            siblingRuncardsCall.cancel();
        }
        siblingRuncardsCall = api.getRuncardsByWorkOrder(workOrder);
        siblingRuncardsCall.enqueue(new Callback<List<RuncardOverviewModel>>() {
            @Override
            public void onResponse(
                    Call<List<RuncardOverviewModel>> call,
                    Response<List<RuncardOverviewModel>> response
            ) {
                siblingRuncardsCall = null;
                addSourceButton.setEnabled(true);
                List<RuncardOverviewModel> body = response.body();
                if (!response.isSuccessful() || body == null) {
                    bindSourceOptions(new ArrayList<>());
                    Toast.makeText(context, "Unable to load source runcards", Toast.LENGTH_SHORT).show();
                    return;
                }

                List<MergeSourceRow> filteredRows = new ArrayList<>();
                for (RuncardOverviewModel row : body) {
                    String candidate = normalize(row.getRuncardNo());
                    if (TextUtils.isEmpty(candidate) || candidate.equals(main)) {
                        continue;
                    }
                    filteredRows.add(new MergeSourceRow(candidate, parseIntOrZero(row.getQty())));
                }
                bindSourceOptions(filteredRows);
            }

            @Override
            public void onFailure(Call<List<RuncardOverviewModel>> call, Throwable throwable) {
                if (call.isCanceled()) {
                    return;
                }
                siblingRuncardsCall = null;
                addSourceButton.setEnabled(true);
                bindSourceOptions(new ArrayList<>());
                Toast.makeText(context, "Unable to load source runcards", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindSourceOptions(List<MergeSourceRow> rows) {
        availableSourceRows.clear();
        availableSourceRows.addAll(rows);
        sourceSpinnerAdapter.clear();
        sourceSpinnerAdapter.add("Select source runcard");
        for (MergeSourceRow row : rows) {
            sourceSpinnerAdapter.add(row.runcard + "  |  Qty " + row.qty);
        }
        sourceSpinnerAdapter.notifyDataSetChanged();
        sourceRuncardSpinner.setSelection(0);
    }

    private void confirmMerge() {
        String main = normalize(mainRuncardInput.getText().toString());
        if (TextUtils.isEmpty(main)) {
            mainRuncardInput.setError("Main Runcard is required");
            mainRuncardInput.requestFocus();
            return;
        }
        if (sourceRows.isEmpty()) {
            Toast.makeText(context, "Add at least one child runcard to merge", Toast.LENGTH_SHORT).show();
            return;
        }

        // Build + validate the child runcard list before firing the API:
        //  (a) list not empty (checked above), (b) no blank entries, (c) none equal to the mother.
        List<String> children = new ArrayList<>();
        for (MergeSourceRow row : sourceRows) {
            String child = normalize(row == null ? null : row.runcard);
            if (TextUtils.isEmpty(child)) {
                Toast.makeText(context, "A child runcard is empty. Remove it and try again.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (child.equals(main)) {
                Toast.makeText(context, "A child runcard cannot be the same as the Main Runcard", Toast.LENGTH_SHORT).show();
                return;
            }
            children.add(child);
        }

        confirmButton.setEnabled(false);
        MergeRequest request = new MergeRequest(children, displayOrDash(callback.getCurrentUserId()));
        if (mergeCall != null) {
            mergeCall.cancel();
        }
        mergeCall = api.mergeRuncards(main, request);
        mergeCall.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                mergeCall = null;
                confirmButton.setEnabled(true);
                if (!response.isSuccessful()) {
                    Toast.makeText(context, ErrorHandler.parseError(response.errorBody()), Toast.LENGTH_LONG).show();
                    return;
                }

                Toast.makeText(context, "Merge saved", Toast.LENGTH_SHORT).show();
                callback.onMergeSucceeded(main);
                close();
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable throwable) {
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

    private void deleteSourceRow(int position) {
        if (position < 0 || position >= sourceRows.size()) {
            return;
        }
        sourceRows.remove(position);
        renderRows();
    }

    private void resetInputs() {
        sourceRows.clear();
        availableSourceRows.clear();
        mainRuncardInput.setText("");
        mainRuncardInput.setError(null);
        if (sourceSpinnerAdapter != null) {
            sourceSpinnerAdapter.clear();
            sourceSpinnerAdapter.add("Select source runcard");
            sourceSpinnerAdapter.notifyDataSetChanged();
        }
        sourceRuncardSpinner.setSelection(0);
        addSourceButton.setEnabled(true);
        confirmButton.setEnabled(true);
        renderRows();
    }

    private void cancelCalls() {
        if (siblingRuncardsCall != null) {
            siblingRuncardsCall.cancel();
            siblingRuncardsCall = null;
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
