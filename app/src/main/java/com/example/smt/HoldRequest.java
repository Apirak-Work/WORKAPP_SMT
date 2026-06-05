package com.example.smt;

final class HoldRequest {
    final String workOrder;
    final String runcard;
    final String material;
    final String selectReason;
    final String topicDamage;
    final String holdComment;
    final String releaseComment;
    final String actionType;

    HoldRequest(
            String workOrder,
            String runcard,
            String material,
            String selectReason,
            String topicDamage,
            String holdComment,
            String releaseComment,
            String actionType
    ) {
        this.workOrder = workOrder;
        this.runcard = runcard;
        this.material = material;
        this.selectReason = selectReason;
        this.topicDamage = topicDamage;
        this.holdComment = holdComment;
        this.releaseComment = releaseComment;
        this.actionType = actionType;
    }
}
