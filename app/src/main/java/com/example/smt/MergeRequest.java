package com.example.smt;

import java.util.List;

final class MergeRequest {
    final String mainRuncard;
    final List<String> sourceRuncards;
    final String workCenter;
    final String cby;

    MergeRequest(String mainRuncard, List<String> sourceRuncards, String workCenter, String cby) {
        this.mainRuncard = mainRuncard;
        this.sourceRuncards = sourceRuncards;
        this.workCenter = workCenter;
        this.cby = cby;
    }
}
