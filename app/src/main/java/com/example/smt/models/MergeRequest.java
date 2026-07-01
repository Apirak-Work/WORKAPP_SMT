package com.example.smt.models;

import java.util.List;

public final class MergeRequest {
    public final List<String> sourceRuncards;
    public final String operatorId;

    public MergeRequest(List<String> sourceRuncards, String operatorId) {
        this.sourceRuncards = sourceRuncards;
        this.operatorId = operatorId;
    }
}
