package com.example.smt;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

final class MergeSourceAdapter extends RecyclerView.Adapter<MergeSourceAdapter.ViewHolder> {
    private final List<MergeSourceRow> rows = new ArrayList<>();

    void submitRows(List<MergeSourceRow> nextRows) {
        rows.clear();
        rows.addAll(nextRows);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_merge_source, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MergeSourceRow row = rows.get(position);
        holder.runcard.setText(row.runcard);
        holder.qty.setText(String.valueOf(row.qty));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final TextView runcard;
        final TextView qty;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            runcard = itemView.findViewById(R.id.mergeSourceRuncardCell);
            qty = itemView.findViewById(R.id.mergeSourceQtyCell);
        }
    }
}
