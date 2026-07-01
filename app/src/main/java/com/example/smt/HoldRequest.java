package com.example.smt;

public final class HoldRequest {
    final String workOrder;
    final String runcard;
    final String material;
    final String workCenter;
    final String operation;
    final String cby;
    final String selectReason;
    final String topicDamage;
    final String holdComment;
    final String releaseComment;
    final String actionType;

    HoldRequest(
            String workOrder,
            String runcard,
            String material,
            String workCenter,
            String operation,
            String cby,
            String selectReason,
            String topicDamage,
            String holdComment,
            String releaseComment,
            String actionType
    ) {
        this.workOrder = workOrder;
        this.runcard = runcard;
        this.material = material;
        this.workCenter = workCenter;
        this.operation = operation;
        this.cby = cby;
        this.selectReason = selectReason;
        this.topicDamage = topicDamage;
        this.holdComment = holdComment;
        this.releaseComment = releaseComment;
        this.actionType = actionType;
    }
}
