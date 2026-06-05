package com.example.smt;

final class SplitHistoryRow {
    final String runcard;
    final String assyLot;
    final String qty;
    final String mother;
    final String motherQty;
    final String wc;
    final String cdate;

    SplitHistoryRow(
            String runcard,
            String assyLot,
            String qty,
            String mother,
            String motherQty,
            String wc,
            String cdate
    ) {
        this.runcard = runcard;
        this.assyLot = assyLot;
        this.qty = qty;
        this.mother = mother;
        this.motherQty = motherQty;
        this.wc = wc;
        this.cdate = cdate;
    }
}
