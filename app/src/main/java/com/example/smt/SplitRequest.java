package com.example.smt;

final class SplitRequest {
    final String workOrder;
    final String material;
    final String motherRuncard;
    final String motherAssyLot;
    final int splitQty;
    final int motherQty;
    final String workCenter;
    final String cby;
    final String customerType;

    SplitRequest(
            String workOrder,
            String material,
            String motherRuncard,
            String motherAssyLot,
            int splitQty,
            int motherQty,
            String workCenter,
            String cby,
            String customerType
    ) {
        this.workOrder = workOrder;
        this.material = material;
        this.motherRuncard = motherRuncard;
        this.motherAssyLot = motherAssyLot;
        this.splitQty = splitQty;
        this.motherQty = motherQty;
        this.workCenter = workCenter;
        this.cby = cby;
        this.customerType = customerType;
    }
}
