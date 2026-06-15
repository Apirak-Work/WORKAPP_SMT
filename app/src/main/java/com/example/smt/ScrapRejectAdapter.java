package com.example.smt;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

final class ScrapRejectAdapter extends RecyclerView.Adapter<ScrapRejectAdapter.ViewHolder> {
    interface DeleteCallback {
        void onDelete(int position);
    }

    private final List<ScrapRejectRow> rows = new ArrayList<>();
    private final DeleteCallback deleteCallback;

    ScrapRejectAdapter(DeleteCallback deleteCallback) {
        this.deleteCallback = deleteCallback;
    }

    void submitRows(List<ScrapRejectRow> nextRows) {
        rows.clear();
        rows.addAll(nextRows);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_scrap_reject, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ScrapRejectRow row = rows.get(position);
        holder.code.setText(row.code);
        holder.description.setText(row.description);
        holder.qty.setText(String.valueOf(row.qty));
        holder.deleteButton.setOnClickListener(v -> deleteCallback.onDelete(holder.getAdapterPosition()));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final TextView code;
        final TextView description;
        final TextView qty;
        final Button deleteButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            code = itemView.findViewById(R.id.rejectCodeCell);
            description = itemView.findViewById(R.id.rejectDescriptionCell);
            qty = itemView.findViewById(R.id.rejectQtyCell);
            deleteButton = itemView.findViewById(R.id.rejectDeleteButton);
        }
    }
}
