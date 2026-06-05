package com.example.smt;

import android.content.Context;
import android.widget.Button;
import android.widget.Toast;

final class CheckB2bManager implements ActionButtonModule {
    interface Callback {
        ProductionDetail getCurrentProductionDetail();
    }

    private final Context context;
    private final Button openButton;
    private final Callback callback;

    CheckB2bManager(Context context, Button openButton, Callback callback) {
        this.context = context;
        this.openButton = openButton;
        this.callback = callback;
    }

    @Override
    public void initialize() {
        openButton.setOnClickListener(v -> open());
    }

    private void open() {
        ProductionDetail detail = callback.getCurrentProductionDetail();
        if (detail == null) {
            Toast.makeText(context, "Production Detail is not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(context, "CHECK B2B module ready", Toast.LENGTH_SHORT).show();
    }
}
