package com.example.qr_ai;

import retrofit2.Call;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;

public interface AuthService {
    @FormUrlEncoded
    @POST("api/v2/oauth")
    Call<AuthResponse> getToken(
            @Header("Authorization") String basicAuth,
            @Header("RqUID") String rquid,
            @Field("scope") String scope
    );
}
