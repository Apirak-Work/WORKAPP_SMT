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

import java.util.ArrayList;
import java.util.List;

final class ScrapRejectManager {
    interface CallbackBridge {
        ProductionDetail getCurrentProductionDetail();

        void onOpenRequested();

        default void onClosed() {
        }

        void onRejectTotalChanged(String totalQty);
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
        groupSpinner.setAdapter(new ArrayAdapter<>(
                context,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Select Group Criteria", "Assembly Defect", "Inspection Defect", "Machine / Process"}
        ));
        reasonSpinner.setAdapter(new ArrayAdapter<>(
                context,
                android.R.layout.simple_spinner_dropdown_item,
                reasonLabels()
        ));
        openTrigger.setOnClickListener(v -> open());
        closeButton.setOnClickListener(v -> close());
        confirmButton.setOnClickListener(v -> close());
        addRejectButton.setOnClickListener(v -> addReject());
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
    }

    void close() {
        rootView.setVisibility(View.GONE);
        setStandardWorkflowVisible(true);
        callback.onClosed();
    }

    boolean isOpen() {
        return rootView.getVisibility() == View.VISIBLE;
    }

    private void addReject() {
        int qty = parseIntOrZero(rejectQtyInput.getText().toString());
        if (qty <= 0) {
            rejectQtyInput.setError("Reject Qty is required");
            rejectQtyInput.requestFocus();
            return;
        }
        int selectedReason = reasonSpinner.getSelectedItemPosition() - 1;
        if (selectedReason < 0 || selectedReason >= REASONS.length) {
            Toast.makeText(context, "Select Reason / Defect Code", Toast.LENGTH_SHORT).show();
            return;
        }

        rows.add(new ScrapRejectRow(
                REASONS[selectedReason][0],
                REASONS[selectedReason][1],
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
        int total = 0;
        for (ScrapRejectRow row : rows) {
            total += row.qty;
        }
        callback.onRejectTotalChanged(total == 0 ? "" : String.valueOf(total));
    }

    private String[] reasonLabels() {
        String[] labels = new String[REASONS.length + 1];
        labels[0] = "Select Reason / Defect Code";
        for (int index = 0; index < REASONS.length; index++) {
            labels[index + 1] = REASONS[index][0] + " - " + REASONS[index][1];
        }
        return labels;
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
