package com.example.qr_ai;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface GigaChatService {
    @POST("api/v1/chat/completions")
    Call<GenerateImageResponse> generateImage(@Header("Authorization") String authHeader, @Body GenerateImageRequest request);
}
