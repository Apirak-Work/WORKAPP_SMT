package com.example.smt;

import com.google.gson.annotations.SerializedName;

public final class RuncardOverviewModel {
    @SerializedName(value = "type", alternate = {"Type", "rcType", "RcType", "RC_TYPE"})
    private String type;

    @SerializedName(value = "rc", alternate = {"Rc", "runcardNo", "RuncardNo"})
    private String runcardNo;

    @SerializedName(value = "assy", alternate = {"Assy"})
    private String assy;

    @SerializedName(value = "qty", alternate = {"Qty"})
    private String qty;

    @SerializedName(value = "rcAction", alternate = {"RcAction", "RCAction"})
    private String rcAction;

    @SerializedName(value = "status", alternate = {"Status"})
    private String status;

    String getType() {
        return clean(type);
    }

    String getRuncardNo() {
        return clean(runcardNo);
    }

    String getAssy() {
        return clean(assy);
    }

    String getQty() {
        return clean(qty);
    }

    String getRcAction() {
        return clean(rcAction);
    }

    String getStatus() {
        return clean(status);
    }

    private static String clean(String value) {
        return value == null ? "" : value;
    }
}
