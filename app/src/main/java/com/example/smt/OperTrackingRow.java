package com.example.smt;

final class OperTrackingRow {
    final String oper;
    final String wc;
    final String description;
    final String workCenter;
    final String receive;
    final String yield;
    final String scrap;
    final String move;
    final String percentYield;
    final String receiveDate;
    final String confirmDate;
    final String en;

    OperTrackingRow(
            String oper,
            String wc,
            String description,
            String workCenter,
            String receive,
            String yield,
            String scrap,
            String move,
            String percentYield,
            String receiveDate,
            String confirmDate,
            String en
    ) {
        this.oper = oper;
        this.wc = wc;
        this.description = description;
        this.workCenter = workCenter;
        this.receive = receive;
        this.yield = yield;
        this.scrap = scrap;
        this.move = move;
        this.percentYield = percentYield;
        this.receiveDate = receiveDate;
        this.confirmDate = confirmDate;
        this.en = en;
    }
}
