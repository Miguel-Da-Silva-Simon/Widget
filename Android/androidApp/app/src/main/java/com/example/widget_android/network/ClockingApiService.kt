package com.example.widget_android.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ClockingApiService {

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): LoginResponseDto

    @GET("auth/session")
    suspend fun session(): SessionResponseDto

    @POST("auth/logout")
    suspend fun logout()

    @GET("me")
    suspend fun me(): UserDto

    @GET("clockings/today")
    suspend fun today(): ClockingStateDto

    @POST("clockings/next")
    suspend fun next(): ClockingStateDto

    @POST("clockings/reset")
    suspend fun reset(): ClockingStateDto

    @POST("clockings/mode")
    suspend fun setMode(@Body body: SetModeRequestDto): ClockingStateDto

    @POST("attendance/actions")
    suspend fun action(@Body body: AttendanceActionRequestDto): ClockingStateDto
}
