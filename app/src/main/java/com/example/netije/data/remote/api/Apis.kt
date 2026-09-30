package com.example.netije.data.remote.api

import com.example.netije.data.remote.dto.AuthResponse
import com.example.netije.data.remote.dto.GoogleSignInRequest
import com.example.netije.data.remote.dto.PersonDto
import com.example.netije.data.remote.dto.SyncPayload
import com.example.netije.data.remote.dto.SyncResponse
import com.example.netije.data.remote.dto.TransactionDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

interface AuthApi {
    @POST("auth/google")
    suspend fun signInWithGoogle(@Body request: GoogleSignInRequest): Response<AuthResponse>

    @POST("auth/signout")
    suspend fun signOut(): Response<Unit>
}

interface PersonsApi {
    @GET("persons")
    suspend fun getAll(): Response<List<PersonDto>>

    @GET("persons/{id}")
    suspend fun getById(@Path("id") id: String): Response<PersonDto>

    @POST("persons")
    suspend fun create(@Body person: PersonDto): Response<PersonDto>

    @PUT("persons/{id}")
    suspend fun update(@Path("id") id: String, @Body person: PersonDto): Response<PersonDto>

    @DELETE("persons/{id}")
    suspend fun delete(@Path("id") id: String): Response<Unit>
}

interface TransactionsApi {
    @GET("transactions")
    suspend fun getAll(
        @Query("personId") personId: String? = null,
        @Query("fromDate") fromDate: Long? = null,
        @Query("toDate") toDate: Long? = null
    ): Response<List<TransactionDto>>

    @GET("transactions/{id}")
    suspend fun getById(@Path("id") id: String): Response<TransactionDto>

    @POST("transactions")
    suspend fun create(@Body transaction: TransactionDto): Response<TransactionDto>

    @PUT("transactions/{id}")
    suspend fun update(@Path("id") id: String, @Body t: TransactionDto): Response<TransactionDto>

    @DELETE("transactions/{id}")
    suspend fun delete(@Path("id") id: String): Response<Unit>
}

interface SyncApi {
    @POST("sync")
    suspend fun sync(@Body payload: SyncPayload): Response<SyncResponse>
}

interface ReportsApi {
    @GET("reports/user/{personId}")
    @Streaming
    suspend fun userReport(
        @Path("personId") personId: String,
        @Query("format") format: String,
        @Query("fromDate") fromDate: Long? = null,
        @Query("toDate") toDate: Long? = null
    ): Response<ResponseBody>

    @GET("reports/summary")
    @Streaming
    suspend fun summaryReport(
        @Query("format") format: String,
        @Query("fromDate") fromDate: Long? = null,
        @Query("toDate") toDate: Long? = null
    ): Response<ResponseBody>

    @GET("reports/transactions")
    @Streaming
    suspend fun transactionsReport(
        @Query("format") format: String,
        @Query("fromDate") fromDate: Long? = null,
        @Query("toDate") toDate: Long? = null,
        @Query("personId") personId: String? = null
    ): Response<ResponseBody>
}
