package com.example.smt;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.example.smt.network.ProductionRetrofitApi;
import com.example.smt.network.RetrofitProvider;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

import java.util.ArrayList;
import java.util.List;

final class HoldFunctionManager {
    interface Callback {
        ProductionDetail getCurrentProductionDetail();

        default String getCurrentWorkCenter() {
            return "";
        }

        default String getCurrentOperation() {
            return "";
        }

        default String getCurrentUserId() {
            return "";
        }

        void onOpenRequested();

        void onClosed();

        void onHoldActionSucceeded(String actionType);

        void onInputChanged();
    }

    private static final String[] HOLD_TOPICS_DEFAULT = {"Select Topic Damage"};
    private static final String[] HOLD_TOPICS_INT = {
            "Select Topic Damage",
            "OS Hold (Internal)",
            "Mark & Lead Hold",
            "Wait for FA",
            "Setup Fail",
            "Assy Low Yield",
            "Machine Down / No spare Part",
            "Material Shortage",
            "Process Engineer Request",
            "Wafer Quality",
            "Customer's Engineer Request",
            "Test Summary Problem",
            "Residue Lot not Full Reel",
            "Hold QC Reject (Internal)",
            "Test Low Yield (Internal)",
            "Wafer Related",
            "OS Hold",
            "Packing Go-Live On-Hold",
            "No Test Program",
            "Tester fail",
            "Summary lose",
            "Summary Mismatch",
            "DI Bath fail",
            "KELVIN Hold > 0.5% (Internal)",
            "UNTESTED on Hold. (Test low yield)",
            "Not show test spec at SLRC",
            "No Test Traveler",
            "New Option",
            "New Test program",
            "ISB Hold low yield",
            "SS (Sample test low yield)",
            "Unit lose > 1%",
            "Hardware issue",
            "AOI lot low yield < 99.80%",
            "AOI units lose >0.5%",
            "Other (INT)",
            "Lot Delayed in process",
            "Change priority",
            "Capacity constraint",
            "Safe launch",
            "Invalid reject/No issued",
            "Document / MBOI issue",
            "Material shortage",
            "ENG Evaluation",
            "New Option",
            "Material Shortage"
    };
    private static final String[] HOLD_TOPICS_STOP = {
            "Select Topic Damage",
            "STOP ALL PROCESS (PRD,B2B)",
            "HOLD for wait QA create IRR",
            "HOLD for wait QA INSPECTION"
    };
    private static final String[] HOLD_TOPICS_MFG = {
            "Select Topic Damage",
            "Test Low Yield",
            "Test program related",
            "Test spec related",
            "Hold QC Reject",
            "Low yield-Test : Device related[MPE]",
            "Low yield-Test Related[Subcon issue]MPE",
            "Map file issue/DCV/MBOI/Document related",
            "Low yield-Assy related[Subcon issue]MPE",
            "Material quality issue[MPE]"
    };
    private static final String[] HOLD_TOPICS_ENG = {
            "Select Topic Damage",
            "Wait scrap",
            "Low yield-Test : Device related[MPE]",
            "Low yield-Test Related[Subcon issue]MPE",
            "Low yield-Assy related[Subcon issue]MPE",
            "Material quality issue[MPE]"
    };

    private final Context context;
    private final View rootView;
    private final Button openButton;
    private final View[] standardWorkflowViews;
    private final Callback callback;
    private final ProductionRetrofitApi api = RetrofitProvider.productionApi();
    private final TextView workOrderValue;
    private final TextView runcardValue;
    private final TextView materialValue;
    private final TextView statusValue;
    private final Spinner reasonSpinner;
    private final Spinner topicDamageSpinner;
    private final EditText holdComment;
    private final EditText releaseHoldComment;
    private final Button closeButton;
    private final Button holdRuncardButton;
    private final Button releaseHoldRuncardButton;
    private final List<HoldReason> holdReasons = new ArrayList<>();
    private ArrayAdapter<String> reasonAdapter;
    private Call<ResponseBody> holdActionCall;
    private Call<List<HoldReason>> holdReasonsCall;

    HoldFunctionManager(
            Context context,
            View rootView,
            Button openButton,
            View[] standardWorkflowViews,
            Callback callback
    ) {
        this.context = context;
        this.rootView = rootView;
        this.openButton = openButton;
        this.standardWorkflowViews = standardWorkflowViews;
        this.callback = callback;
        this.workOrderValue = rootView.findViewById(R.id.holdWorkOrderValue);
        this.runcardValue = rootView.findViewById(R.id.holdRuncardValue);
        this.materialValue = rootView.findViewById(R.id.holdMaterialValue);
        this.statusValue = rootView.findViewById(R.id.holdStatusValue);
        this.reasonSpinner = rootView.findViewById(R.id.spinnerSelectReason);
        this.topicDamageSpinner = rootView.findViewById(R.id.spinnerTopicDamage);
        this.holdComment = rootView.findViewById(R.id.edtHoldComment);
        this.releaseHoldComment = rootView.findViewById(R.id.edtReleaseHoldComment);
        this.closeButton = rootView.findViewById(R.id.closeHoldFunctionButton);
        this.holdRuncardButton = rootView.findViewById(R.id.holdRuncardButton);
        this.releaseHoldRuncardButton = rootView.findViewById(R.id.releaseHoldRuncardButton);
    }

    void initialize() {
        configureSpinners();
        openButton.setOnClickListener(v -> open());
        closeButton.setOnClickListener(v -> close());
        holdRuncardButton.setOnClickListener(v -> submitHold());
        releaseHoldRuncardButton.setOnClickListener(v -> submitRelease());
    }

    void open() {
        ProductionDetail detail = callback.getCurrentProductionDetail();
        if (detail == null || displayOrDash(detail.runcardNo).equals("-")) {
            Toast.makeText(context, "Runcard is not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }

        callback.onOpenRequested();
        setStandardWorkflowVisible(false);
        rootView.setVisibility(View.VISIBLE);
        bindHeader(detail);
        resetInputs();
        loadHoldReasons();
    }

    void close() {
        if (holdActionCall != null) {
            holdActionCall.cancel();
            holdActionCall = null;
        }
        if (holdReasonsCall != null) {
            holdReasonsCall.cancel();
            holdReasonsCall = null;
        }
        rootView.setVisibility(View.GONE);
        setStandardWorkflowVisible(true);
        resetInputs();
        callback.onClosed();
    }

    void refreshHeader() {
        if (rootView.getVisibility() == View.VISIBLE) {
            bindHeader(callback.getCurrentProductionDetail());
        }
    }

    boolean isOpen() {
        return rootView.getVisibility() == View.VISIBLE;
    }

    private void configureSpinners() {
        reasonAdapter = new ArrayAdapter<>(
                context,
                android.R.layout.simple_spinner_item,
                new ArrayList<>()
        );
        reasonAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        reasonSpinner.setAdapter(reasonAdapter);
        bindHoldReasons(new ArrayList<>());
        setTopicDamageOptions(HOLD_TOPICS_DEFAULT);

        reasonSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                HoldReason selectedReason = selectedHoldReason();
                String reasonText = selectedReason == null ? "" : selectedReason.label().toUpperCase();
                if (reasonText.contains("INTERNAL") || reasonText.contains("INT")) {
                    setTopicDamageOptions(HOLD_TOPICS_INT);
                } else if (reasonText.contains("IRR") || reasonText.contains("STOP")) {
                    setTopicDamageOptions(HOLD_TOPICS_STOP);
                } else if (reasonText.contains("MFG") || reasonText.contains("QC") || reasonText.contains("YIELD")) {
                    setTopicDamageOptions(HOLD_TOPICS_MFG);
                } else if (reasonText.contains("ENG")) {
                    setTopicDamageOptions(HOLD_TOPICS_ENG);
                } else {
                    setTopicDamageOptions(HOLD_TOPICS_DEFAULT);
                }
                if (position > 0) {
                    callback.onInputChanged();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                setTopicDamageOptions(HOLD_TOPICS_DEFAULT);
            }
        });
    }

    private void loadHoldReasons() {
        if (holdReasonsCall != null) {
            holdReasonsCall.cancel();
        }
        holdReasonsCall = api.getHoldReasons();
        holdReasonsCall.enqueue(new retrofit2.Callback<List<HoldReason>>() {
            @Override
            public void onResponse(Call<List<HoldReason>> call, Response<List<HoldReason>> response) {
                holdReasonsCall = null;
                if (!response.isSuccessful() || response.body() == null) {
                    bindHoldReasons(new ArrayList<>());
                    Toast.makeText(context, "Unable to load HOLD reasons", Toast.LENGTH_LONG).show();
                    return;
                }
                bindHoldReasons(response.body());
            }

            @Override
            public void onFailure(Call<List<HoldReason>> call, Throwable throwable) {
                if (call.isCanceled()) {
                    return;
                }
                holdReasonsCall = null;
                bindHoldReasons(new ArrayList<>());
                Toast.makeText(context, "Unable to load HOLD reasons", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void bindHoldReasons(List<HoldReason> rows) {
        holdReasons.clear();
        reasonAdapter.clear();
        reasonAdapter.add("Select Reason");
        for (HoldReason row : rows) {
            if (row == null || TextUtils.isEmpty(row.reasonCode)) {
                continue;
            }
            holdReasons.add(row);
            reasonAdapter.add(row.label());
        }
        reasonAdapter.notifyDataSetChanged();
        reasonSpinner.setSelection(0);
    }

    private void setTopicDamageOptions(String[] options) {
        ArrayAdapter<String> topicAdapter = new ArrayAdapter<>(
                context,
                android.R.layout.simple_spinner_item,
                options
        );
        topicAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        topicDamageSpinner.setAdapter(topicAdapter);
        topicDamageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    callback.onInputChanged();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // No action is needed when no topic is selected.
            }
        });
    }

    private void bindHeader(ProductionDetail detail) {
        String workOrder = detail == null ? "-" : displayOrDash(detail.workOrder);
        String runcard = detail == null ? "-" : displayOrDash(detail.runcardNo);
        String material = detail == null ? "-" : displayOrDash(detail.material);
        boolean onHold = isRuncardOnHold(detail);

        workOrderValue.setText(workOrder);
        runcardValue.setText(runcard);
        materialValue.setText(material);
        statusValue.setText(onHold ? "ON HOLD" : "NOT ON HOLD");
        statusValue.setTextColor(ContextCompat.getColor(context, onHold ? R.color.warning : R.color.teal_dark));
    }

    private void submitHold() {
        ProductionDetail detail = callback.getCurrentProductionDetail();
        if (detail == null || displayOrDash(detail.runcardNo).equals("-")) {
            Toast.makeText(context, "Runcard is not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!validateHoldInputs()) {
            return;
        }

        HoldReason selectedReason = selectedHoldReason();
        executeHoldAction(
                "HOLD",
                detail,
                holdComment.getText().toString().trim(),
                selectedReason == null ? "" : selectedReason.reasonCode,
                topicDamageSpinner.getSelectedItem().toString()
        );
    }

    private void submitRelease() {
        ProductionDetail detail = callback.getCurrentProductionDetail();
        if (detail == null || displayOrDash(detail.runcardNo).equals("-")) {
            Toast.makeText(context, "Runcard is not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!validateReleaseInputs()) {
            return;
        }

        executeHoldAction(
                "RELEASE",
                detail,
                releaseHoldComment.getText().toString().trim(),
                "",
                ""
        );
    }

    private boolean validateHoldInputs() {
        if (TextUtils.isEmpty(holdComment.getText().toString().trim())) {
            holdComment.setError("Required");
            holdComment.requestFocus();
            return false;
        }
        if (reasonSpinner.getSelectedItemPosition() <= 0) {
            Toast.makeText(context, "Please select HOLD reason", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (topicDamageSpinner.getSelectedItemPosition() <= 0) {
            Toast.makeText(context, "Please select type of damage", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private boolean validateReleaseInputs() {
        if (TextUtils.isEmpty(releaseHoldComment.getText().toString().trim())) {
            releaseHoldComment.setError("Required");
            releaseHoldComment.requestFocus();
            return false;
        }
        return true;
    }

    private void executeHoldAction(
            String actionType,
            ProductionDetail detail,
            String comment,
            String reason,
            String topicDamage
    ) {
        holdRuncardButton.setEnabled(false);
        releaseHoldRuncardButton.setEnabled(false);
        Toast.makeText(context, "Submitting " + actionType + " request...", Toast.LENGTH_SHORT).show();

        HoldRequest request = new HoldRequest(
                displayOrDash(detail.workOrder),
                displayOrDash(detail.runcardNo),
                displayOrDash(detail.material),
                displayOrDash(callback.getCurrentWorkCenter()),
                displayOrDash(callback.getCurrentOperation()),
                displayOrDash(callback.getCurrentUserId()),
                reason,
                topicDamage,
                "HOLD".equals(actionType) ? comment : "",
                "RELEASE".equals(actionType) ? comment : "",
                actionType
        );

        if (holdActionCall != null) {
            holdActionCall.cancel();
        }
        holdActionCall = "RELEASE".equals(actionType)
                ? api.releaseHoldAction(request)
                : api.saveHoldAction(request);
        holdActionCall.enqueue(new retrofit2.Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                holdActionCall = null;
                setActionButtonsEnabled(true);
                if (!response.isSuccessful()) {
                    Toast.makeText(
                            context,
                            actionType + " failed: HTTP " + response.code(),
                            Toast.LENGTH_LONG
                    ).show();
                    return;
                }

                Toast.makeText(context, actionType + " saved", Toast.LENGTH_SHORT).show();
                rootView.setVisibility(View.GONE);
                setStandardWorkflowVisible(true);
                resetInputs();
                callback.onHoldActionSucceeded(actionType);
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable throwable) {
                if (call.isCanceled()) {
                    return;
                }
                holdActionCall = null;
                setActionButtonsEnabled(true);
                Toast.makeText(
                        context,
                        actionType + " failed: " + (throwable.getMessage() == null
                                ? "Unable to connect to backend"
                                : throwable.getMessage()),
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }

    private HoldReason selectedHoldReason() {
        int selectedIndex = reasonSpinner.getSelectedItemPosition() - 1;
        if (selectedIndex < 0 || selectedIndex >= holdReasons.size()) {
            return null;
        }
        return holdReasons.get(selectedIndex);
    }

    private void setActionButtonsEnabled(boolean enabled) {
        holdRuncardButton.setEnabled(enabled);
        releaseHoldRuncardButton.setEnabled(enabled);
    }

    private void resetInputs() {
        reasonSpinner.setSelection(0);
        holdComment.setText("");
        holdComment.setError(null);
        releaseHoldComment.setText("");
        releaseHoldComment.setError(null);
    }

    private void setStandardWorkflowVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        for (View view : standardWorkflowViews) {
            if (view != null) {
                view.setVisibility(visibility);
            }
        }
    }

    private boolean isRuncardOnHold(ProductionDetail detail) {
        if (detail == null) {
            return false;
        }
        return containsHoldText(detail.description)
                || containsHoldText(detail.orderType)
                || containsHoldText(detail.lotType);
    }

    private boolean containsHoldText(String value) {
        return value != null && value.toUpperCase().contains("HOLD");
    }

    private String displayOrDash(String value) {
        return TextUtils.isEmpty(value) ? "-" : value;
    }
}
