package com.example.smt.network;

import android.util.Log;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class RetrofitProvider {
    private static final HttpLoggingInterceptor RAW_JSON_LOGGER = new HttpLoggingInterceptor(
            message -> Log.d("CheckRuncardRawJson", message)
    ).setLevel(HttpLoggingInterceptor.Level.BODY);

    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .addInterceptor(RAW_JSON_LOGGER)
            .build();

    private static final Retrofit RETROFIT = new Retrofit.Builder()
            .baseUrl(ApiConfig.primaryBaseUrl())
            .client(HTTP_CLIENT)
            .addConverterFactory(GsonConverterFactory.create())
            .build();

    private RetrofitProvider() {
    }

    public static ProductionRetrofitApi productionApi() {
        return RETROFIT.create(ProductionRetrofitApi.class);
    }
}
