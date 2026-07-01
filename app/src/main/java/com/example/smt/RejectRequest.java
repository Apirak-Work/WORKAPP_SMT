package com.example.smt;

import java.util.List;

public final class RejectRequest {
    final String workOrder;
    final String runcard;
    final String wcNo;
    final String operation;
    final String station;
    final String cby;
    final List<RejectItem> rejects;

    RejectRequest(
            String workOrder,
            String runcard,
            String wcNo,
            String operation,
            String station,
            String cby,
            List<RejectItem> rejects
    ) {
        this.workOrder = workOrder;
        this.runcard = runcard;
        this.wcNo = wcNo;
        this.operation = operation;
        this.station = station;
        this.cby = cby;
        this.rejects = rejects;
    }

    static final class RejectItem {
        final String reasonCode;
        final String reasonDesc;
        final int rejectQty;

        RejectItem(String reasonCode, String reasonDesc, int rejectQty) {
            this.reasonCode = reasonCode;
            this.reasonDesc = reasonDesc;
            this.rejectQty = rejectQty;
        }
    }
}
