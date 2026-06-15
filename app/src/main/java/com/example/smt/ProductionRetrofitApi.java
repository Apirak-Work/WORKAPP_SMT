package com.example.smt;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

interface ProductionRetrofitApi {
    @GET("api/production/runcards/{runcardNo}")
    Call<ProductionDetail> getProductionDetail(
            @Path("runcardNo") String runcardNo
    );

    @GET("api/production/workorders/{workOrderNo}/runcards")
    Call<List<RuncardOverviewModel>> getRuncardsByWorkOrder(
            @Path("workOrderNo") String workOrderNo
    );

    @POST("api/production/runcard/hold")
    Call<ResponseBody> saveHoldAction(@Body HoldRequest request);

    @POST("api/production/runcard/split")
    Call<SplitResponse> splitRuncard(@Body SplitRequest request);

    @POST("api/production/runcard/merge")
    Call<MergeResponse> mergeRuncards(@Body MergeRequest request);

    @GET("api/production/runcard/{runcardNo}/validate")
    Call<ValidationResponse> validateRuncard(
            @Path("runcardNo") String runcardNo,
            @Query("workCenter") String workCenter
    );

    @GET("api/production/runcard/{runcardNo}/split-history")
    Call<List<SplitHistoryItem>> getSplitHistory(
            @Path("runcardNo") String runcardNo
    );
}
