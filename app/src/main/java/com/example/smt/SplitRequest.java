package com.example.smt;

public final class SplitRequest {
    final String workOrder;
    final String material;
    final String motherRuncard;
    final String motherAssyLot;
    final int splitQty;
    final int motherQty;
    final String workCenter;
    final String operation;
    final String cby;
    final String customerType;
    final String runcardNo;
    final String empId;

    SplitRequest(
            String workOrder,
            String material,
            String motherRuncard,
            String motherAssyLot,
            int splitQty,
            int motherQty,
            String workCenter,
            String operation,
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
        this.operation = operation;
        this.cby = cby;
        this.customerType = customerType;
        this.runcardNo = motherRuncard;
        this.empId = cby;
    }
}
