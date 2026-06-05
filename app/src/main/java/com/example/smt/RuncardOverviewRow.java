package com.example.smt;

final class RuncardOverviewRow {
    final String type;
    final String rc;
    final String assy;
    final String qty;
    final String rcAction;
    final String status;

    RuncardOverviewRow(
            String type,
            String rc,
            String assy,
            String qty,
            String rcAction,
            String status
    ) {
        this.type = type == null ? "" : type;
        this.rc = rc == null ? "" : rc;
        this.assy = assy == null ? "" : assy;
        this.qty = qty == null ? "" : qty;
        this.rcAction = rcAction == null ? "" : rcAction;
        this.status = status == null ? "" : status;
    }
}
