package com.example.smt.network;

import com.example.smt.EmployeeProfile;
import com.example.smt.HoldReason;
import com.example.smt.HoldRequest;
import com.example.smt.ProductionDetail;
import com.example.smt.RejectReason;
import com.example.smt.RejectRequest;
import com.example.smt.RejectResponse;
import com.example.smt.RuncardOverviewModel;
import com.example.smt.SplitHistoryItem;
import com.example.smt.SplitRequest;
import com.example.smt.SplitResponse;
import com.example.smt.ValidationResponse;
import com.example.smt.models.MergeRequest;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ProductionRetrofitApi {
    @GET("api/production/detail/{runcardNo}")
    Call<ProductionDetail> getProductionDetail(
            @Path("runcardNo") String runcardNo
    );

    @GET("api/auth/verify/{empId}")
    Call<EmployeeProfile> verifyEmployee(
            @Path("empId") String empId
    );

    @GET("api/production/workorders/{workOrderNo}/runcards")
    Call<List<RuncardOverviewModel>> getRuncardsByWorkOrder(
            @Path("workOrderNo") String workOrderNo
    );

    @GET("api/production/reasons/hold")
    Call<List<HoldReason>> getHoldReasons();

    @GET("api/production/reasons/reject")
    Call<List<RejectReason>> getRejectReasons();

    @POST("api/production/hold")
    Call<ResponseBody> saveHoldAction(@Body HoldRequest request);

    @POST("api/production/release")
    Call<ResponseBody> releaseHoldAction(@Body HoldRequest request);

    @POST("api/production/split")
    Call<SplitResponse> splitRuncard(@Body SplitRequest request);

    @POST("api/production/runcards/{rc}/merge")
    Call<ResponseBody> mergeRuncards(
            @Path("rc") String hostRuncard,
            @Body MergeRequest request
    );

    @POST("api/production/runcards/{rc}/combine")
    Call<ResponseBody> combineRuncards(
            @Path("rc") String hostRuncard,
            @Body MergeRequest request
    );

    @POST("api/production/reject")
    Call<RejectResponse> saveRejectDetails(@Body RejectRequest request);

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
