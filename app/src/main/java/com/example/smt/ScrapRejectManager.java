package com.example.smt;

import android.content.Context;
import android.text.Editable;
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

import com.example.smt.network.ProductionRetrofitApi;
import com.example.smt.network.RetrofitProvider;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

final class ScrapRejectManager {
    interface CallbackBridge {
        ProductionDetail getCurrentProductionDetail();

        String getCurrentWorkCenter();

        String getCurrentOperation();

        String getCurrentUserId();

        String getCurrentStation();

        void onOpenRequested();

        default void onClosed() {
        }

        void onRejectTotalChanged(String totalQty);

        default void onRejectSaved(String runcardNo) {
        }
    }

    private static final String[][] REASONS = {
            {"A0101", "Tombstone"},
            {"QA002", "Non Wetting"},
            {"QAM08", "FLOODING DAMAGE"},
            {"QAM09", "Solder Bridge"},
            {"QAM10", "Missing Component"}
    };

    private final Context context;
    private final View rootView;
    private final View openTrigger;
    private final View[] standardWorkflowViews;
    private final CallbackBridge callback;
    private final EditText rejectQtyInput;
    private final Spinner groupSpinner;
    private final Spinner reasonSpinner;
    private final Button addRejectButton;
    private final Button closeButton;
    private final Button confirmButton;
    private final RecyclerView rejectRecyclerView;
    private final TextView emptyText;
    private final ScrapRejectAdapter adapter;
    private final List<ScrapRejectRow> rows = new ArrayList<>();
    private final List<RejectReason> rejectReasons = new ArrayList<>();
    private final List<RejectReason> visibleReasons = new ArrayList<>();
    private final ProductionRetrofitApi api = RetrofitProvider.productionApi();
    private Call<RejectResponse> rejectCall;
    private Call<List<RejectReason>> rejectReasonsCall;
    private ArrayAdapter<String> groupAdapter;
    private ArrayAdapter<String> reasonAdapter;

    ScrapRejectManager(
            Context context,
            View rootView,
            View openTrigger,
            View[] standardWorkflowViews,
            CallbackBridge callback
    ) {
        this.context = context;
        this.rootView = rootView;
        this.openTrigger = openTrigger;
        this.standardWorkflowViews = standardWorkflowViews;
        this.callback = callback;
        this.rejectQtyInput = rootView.findViewById(R.id.scrapRejectQtyInput);
        this.groupSpinner = rootView.findViewById(R.id.scrapGroupSpinner);
        this.reasonSpinner = rootView.findViewById(R.id.scrapReasonSpinner);
        this.addRejectButton = rootView.findViewById(R.id.scrapAddRejectButton);
        this.closeButton = rootView.findViewById(R.id.scrapCloseButton);
        this.confirmButton = rootView.findViewById(R.id.scrapConfirmButton);
        this.rejectRecyclerView = rootView.findViewById(R.id.scrapRejectRecyclerView);
        this.emptyText = rootView.findViewById(R.id.scrapRejectEmptyText);
        this.adapter = new ScrapRejectAdapter(this::deleteRow);
    }

    void initialize() {
        rejectRecyclerView.setLayoutManager(new LinearLayoutManager(context));
        rejectRecyclerView.setAdapter(adapter);
        groupAdapter = new ArrayAdapter<>(
                context,
                android.R.layout.simple_spinner_dropdown_item,
                new ArrayList<>()
        );
        reasonAdapter = new ArrayAdapter<>(
                context,
                android.R.layout.simple_spinner_dropdown_item,
                new ArrayList<>()
        );
        groupSpinner.setAdapter(groupAdapter);
        reasonSpinner.setAdapter(reasonAdapter);
        openTrigger.setOnClickListener(v -> open());
        closeButton.setOnClickListener(v -> close());
        confirmButton.setOnClickListener(v -> submitRejectDetails());
        addRejectButton.setOnClickListener(v -> addReject());
        groupSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                bindReasonSpinner();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        rejectQtyInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                addRejectButton.setEnabled(!editable.toString().trim().isEmpty());
            }
        });
        addRejectButton.setEnabled(false);
        renderRows();
    }

    void open() {
        ProductionDetail detail = callback.getCurrentProductionDetail();
        if (detail == null || TextUtils.isEmpty(detail.runcardNo)) {
            Toast.makeText(context, "Production Detail is not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }
        callback.onOpenRequested();
        setStandardWorkflowVisible(false);
        rootView.setVisibility(View.VISIBLE);
        loadRejectReasons();
    }

    void close() {
        if (rejectCall != null) {
            rejectCall.cancel();
            rejectCall = null;
        }
        if (rejectReasonsCall != null) {
            rejectReasonsCall.cancel();
            rejectReasonsCall = null;
        }
        rootView.setVisibility(View.GONE);
        setStandardWorkflowVisible(true);
        callback.onClosed();
    }

    boolean isOpen() {
        return rootView.getVisibility() == View.VISIBLE;
    }

    private void addReject() {
        ProductionDetail detail = callback.getCurrentProductionDetail();
        if (detail == null) {
            Toast.makeText(context, "Production Detail is not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }

        String qtyText = rejectQtyInput.getText().toString().trim();
        if (TextUtils.isEmpty(qtyText)) {
            rejectQtyInput.setError("Reject Qty is required");
            rejectQtyInput.requestFocus();
            return;
        }

        int qty;
        try {
            qty = Integer.parseInt(qtyText);
        } catch (NumberFormatException ex) {
            rejectQtyInput.setError("Reject Qty must be a number");
            rejectQtyInput.requestFocus();
            return;
        }

        if (qty <= 0) {
            rejectQtyInput.setError("Reject Qty must be greater than 0");
            rejectQtyInput.requestFocus();
            return;
        }
        int currentQty = parseIntOrZero(detail.rcQuantity);
        if (currentQty > 0 && totalRejectQty() + qty > currentQty) {
            rejectQtyInput.setError("Reject Qty must not exceed current QTY");
            rejectQtyInput.requestFocus();
            return;
        }

        int selectedReason = reasonSpinner.getSelectedItemPosition() - 1;
        if (selectedReason < 0 || selectedReason >= visibleReasons.size()) {
            Toast.makeText(context, "Select Reason / Defect Code", Toast.LENGTH_SHORT).show();
            return;
        }
        RejectReason reason = visibleReasons.get(selectedReason);

        rows.add(new ScrapRejectRow(
                displayOrEmpty(reason.reasonCode),
                displayOrEmpty(reason.description),
                qty
        ));
        rejectQtyInput.setText("");
        rejectQtyInput.setError(null);
        renderRows();
        publishTotal();
    }

    private void deleteRow(int position) {
        if (position < 0 || position >= rows.size()) {
            return;
        }
        rows.remove(position);
        renderRows();
        publishTotal();
    }

    private void renderRows() {
        adapter.submitRows(rows);
        emptyText.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        confirmButton.setEnabled(!rows.isEmpty());
    }

    private void publishTotal() {
        int total = totalRejectQty();
        callback.onRejectTotalChanged(total == 0 ? "" : String.valueOf(total));
    }

    private void submitRejectDetails() {
        ProductionDetail detail = callback.getCurrentProductionDetail();
        if (detail == null || rows.isEmpty()) {
            Toast.makeText(context, "Reject data is not ready", Toast.LENGTH_SHORT).show();
            return;
        }
        int currentQty = parseIntOrZero(detail.rcQuantity);
        int totalRejectQty = totalRejectQty();
        if (totalRejectQty <= 0 || (currentQty > 0 && totalRejectQty > currentQty)) {
            Toast.makeText(context, "Reject Qty must be greater than 0 and not exceed current QTY", Toast.LENGTH_LONG).show();
            return;
        }

        List<RejectRequest.RejectItem> rejects = new ArrayList<>();
        for (ScrapRejectRow row : rows) {
            rejects.add(new RejectRequest.RejectItem(row.code, row.description, row.qty));
        }

        RejectRequest request = new RejectRequest(
                displayOrEmpty(detail.workOrder),
                displayOrEmpty(detail.runcardNo),
                displayOrEmpty(callback.getCurrentWorkCenter()),
                displayOrEmpty(callback.getCurrentOperation()),
                displayOrEmpty(callback.getCurrentStation()),
                displayOrEmpty(callback.getCurrentUserId()),
                rejects
        );

        confirmButton.setEnabled(false);
        rejectCall = api.saveRejectDetails(request);
        rejectCall.enqueue(new Callback<RejectResponse>() {
            @Override
            public void onResponse(Call<RejectResponse> call, Response<RejectResponse> response) {
                rejectCall = null;
                confirmButton.setEnabled(true);
                RejectResponse body = response.body();
                if (!response.isSuccessful() || body == null || !body.success) {
                    Toast.makeText(context, "Reject save failed: HTTP " + response.code(), Toast.LENGTH_LONG).show();
                    return;
                }

                Toast.makeText(context, body.message == null ? "Reject details saved" : body.message, Toast.LENGTH_SHORT).show();
                callback.onRejectSaved(displayOrEmpty(detail.runcardNo));
                rows.clear();
                renderRows();
                publishTotal();
                close();
            }

            @Override
            public void onFailure(Call<RejectResponse> call, Throwable throwable) {
                if (call.isCanceled()) {
                    return;
                }
                rejectCall = null;
                confirmButton.setEnabled(true);
                Toast.makeText(
                        context,
                        "Reject save failed: " + (throwable.getMessage() == null
                                ? "Unable to connect to backend"
                                : throwable.getMessage()),
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }

    private void loadRejectReasons() {
        if (rejectReasonsCall != null) {
            rejectReasonsCall.cancel();
        }
        rejectReasonsCall = api.getRejectReasons();
        rejectReasonsCall.enqueue(new Callback<List<RejectReason>>() {
            @Override
            public void onResponse(Call<List<RejectReason>> call, Response<List<RejectReason>> response) {
                rejectReasonsCall = null;
                if (!response.isSuccessful() || response.body() == null) {
                    bindRejectReasons(fallbackRejectReasons());
                    Toast.makeText(context, "Unable to load reject reasons. Using fallback list.", Toast.LENGTH_LONG).show();
                    return;
                }
                bindRejectReasons(response.body());
            }

            @Override
            public void onFailure(Call<List<RejectReason>> call, Throwable throwable) {
                if (call.isCanceled()) {
                    return;
                }
                rejectReasonsCall = null;
                bindRejectReasons(fallbackRejectReasons());
                Toast.makeText(context, "Unable to load reject reasons. Using fallback list.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void bindRejectReasons(List<RejectReason> reasons) {
        rejectReasons.clear();
        for (RejectReason reason : reasons) {
            if (reason == null || TextUtils.isEmpty(reason.reasonCode)) {
                continue;
            }
            rejectReasons.add(reason);
        }

        groupAdapter.clear();
        groupAdapter.add("Select Group Criteria");
        Set<String> groups = new LinkedHashSet<>();
        for (RejectReason reason : rejectReasons) {
            String group = displayOrEmpty(reason.reasonGroup);
            if (!group.isEmpty()) {
                groups.add(group);
            }
        }
        for (String group : groups) {
            groupAdapter.add(group);
        }
        groupAdapter.notifyDataSetChanged();
        groupSpinner.setSelection(0);
        bindReasonSpinner();
    }

    private void bindReasonSpinner() {
        visibleReasons.clear();
        reasonAdapter.clear();
        reasonAdapter.add("Select Reason / Defect Code");
        String selectedGroup = groupSpinner.getSelectedItem() == null ? "" : groupSpinner.getSelectedItem().toString();
        for (RejectReason reason : rejectReasons) {
            boolean groupMatches = groupSpinner.getSelectedItemPosition() <= 0
                    || selectedGroup.equals(displayOrEmpty(reason.reasonGroup));
            if (!groupMatches) {
                continue;
            }
            visibleReasons.add(reason);
            reasonAdapter.add(reason.label());
        }
        reasonAdapter.notifyDataSetChanged();
        reasonSpinner.setSelection(0);
    }

    private List<RejectReason> fallbackRejectReasons() {
        List<RejectReason> fallback = new ArrayList<>();
        for (String[] row : REASONS) {
            RejectReason reason = new RejectReason();
            reason.reasonCode = row[0];
            reason.description = row[1];
            reason.reasonGroup = "Default";
            fallback.add(reason);
        }
        return fallback;
    }

    private int totalRejectQty() {
        int total = 0;
        for (ScrapRejectRow row : rows) {
            total += row.qty;
        }
        return total;
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

    private String displayOrEmpty(String value) {
        return TextUtils.isEmpty(value) || "-".equals(value) ? "" : value;
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
