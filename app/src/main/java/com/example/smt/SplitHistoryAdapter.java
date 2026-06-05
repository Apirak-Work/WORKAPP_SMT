package com.example.smt;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

final class SplitHistoryAdapter extends RecyclerView.Adapter<SplitHistoryAdapter.ViewHolder> {
    private final List<SplitHistoryRow> rows = new ArrayList<>();

    void addRow(SplitHistoryRow row) {
        rows.add(0, row);
        notifyItemInserted(0);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_split_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SplitHistoryRow row = rows.get(position);
        holder.runcard.setText(row.runcard);
        holder.assyLot.setText(row.assyLot);
        holder.qty.setText(row.qty);
        holder.mother.setText(row.mother);
        holder.motherQty.setText(row.motherQty);
        holder.wc.setText(row.wc);
        holder.cdate.setText(row.cdate);
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final TextView runcard;
        final TextView assyLot;
        final TextView qty;
        final TextView mother;
        final TextView motherQty;
        final TextView wc;
        final TextView cdate;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            runcard = itemView.findViewById(R.id.splitHistoryRuncard);
            assyLot = itemView.findViewById(R.id.splitHistoryAssy);
            qty = itemView.findViewById(R.id.splitHistoryQty);
            mother = itemView.findViewById(R.id.splitHistoryMother);
            motherQty = itemView.findViewById(R.id.splitHistoryMotherQty);
            wc = itemView.findViewById(R.id.splitHistoryWc);
            cdate = itemView.findViewById(R.id.splitHistoryCdate);
        }
    }
}
