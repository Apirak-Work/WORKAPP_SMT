package com.example.smt;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

interface ProductionRetrofitApi {
    @GET("api/production/workorders/{workOrderNo}/runcards")
    Call<List<RuncardOverviewModel>> getRuncardsByWorkOrder(
            @Path("workOrderNo") String workOrderNo
    );

    @POST("api/production/runcard/hold")
    Call<ResponseBody> saveHoldAction(@Body HoldRequest request);

    @POST("api/production/runcard/split")
    Call<SplitResponse> splitRuncard(@Body SplitRequest request);
}
