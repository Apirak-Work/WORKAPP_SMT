package com.example.smt;

import android.graphics.Paint;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

final class RuncardOverviewAdapter extends RecyclerView.Adapter<RuncardOverviewAdapter.RowViewHolder> {
    private static final String LOG_TAG = "CheckRuncardAdapter";

    interface OnRuncardClickListener {
        void onRuncardClicked(String runcardNo);
    }

    private final List<RuncardOverviewModel> rows = new ArrayList<>();
    private final OnRuncardClickListener listener;

    RuncardOverviewAdapter(OnRuncardClickListener listener) {
        this.listener = listener;
    }

    void submitRows(List<RuncardOverviewModel> nextRows) {
        rows.clear();
        if (nextRows != null) {
            rows.addAll(nextRows);
        }
        notifyDataSetChanged();
    }

    void clear() {
        rows.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RowViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_runcard_overview, parent, false);
        return new RowViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RowViewHolder holder, int position) {
        RuncardOverviewModel row = rows.get(position);
        Log.d(LOG_TAG, "bind position=" + position
                + " type=" + row.getType()
                + ", rc=" + row.getRuncardNo()
                + ", assy=" + row.getAssy()
                + ", qty=" + row.getQty()
                + ", action=" + row.getRcAction()
                + ", status=" + row.getStatus());
        holder.type.setText(row.getType());
        holder.runcardNo.setText(display(row.getRuncardNo()));
        holder.assy.setText(display(row.getAssy()));
        holder.qty.setText(display(row.getQty()));
        holder.rcAction.setText(display(row.getRcAction()));
        holder.status.setText(display(row.getStatus()));
        holder.runcardNo.setPaintFlags(holder.runcardNo.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        holder.runcardNo.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRuncardClicked(row.getRuncardNo());
            }
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    private static String display(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }

    static final class RowViewHolder extends RecyclerView.ViewHolder {
        final TextView type;
        final TextView runcardNo;
        final TextView assy;
        final TextView qty;
        final TextView rcAction;
        final TextView status;

        RowViewHolder(@NonNull View itemView) {
            super(itemView);
            type = itemView.findViewById(R.id.runcardType);
            runcardNo = itemView.findViewById(R.id.runcardNo);
            assy = itemView.findViewById(R.id.runcardAssy);
            qty = itemView.findViewById(R.id.runcardQty);
            rcAction = itemView.findViewById(R.id.runcardAction);
            status = itemView.findViewById(R.id.runcardStatus);
        }
    }
}
