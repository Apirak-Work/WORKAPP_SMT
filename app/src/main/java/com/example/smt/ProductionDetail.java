package com.example.smt;

public final class ProductionDetail {
    final String runcardNo;
    final String description;
    final String material;
    final String rcQuantity;
    final String qtyRc;
    final String qtyWo;
    final String dateCode;
    final String workOrder;
    final String mpq;
    final String assyLot;
    final String waferLot;
    final String orderType;
    final String uom;
    final String lotType;
    final String reelNumber;

    ProductionDetail(
            String runcardNo,
            String description,
            String material,
            String rcQuantity,
            String qtyRc,
            String qtyWo,
            String dateCode,
            String workOrder,
            String mpq,
            String assyLot,
            String waferLot,
            String orderType,
            String uom,
            String lotType,
            String reelNumber
    ) {
        this.runcardNo = runcardNo;
        this.description = description;
        this.material = material;
        this.rcQuantity = rcQuantity;
        this.qtyRc = qtyRc;
        this.qtyWo = qtyWo;
        this.dateCode = dateCode;
        this.workOrder = workOrder;
        this.mpq = mpq;
        this.assyLot = assyLot;
        this.waferLot = waferLot;
        this.orderType = orderType;
        this.uom = uom;
        this.lotType = lotType;
        this.reelNumber = reelNumber;
    }
}
