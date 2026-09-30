package com.example.netije.di

import android.content.Context
import com.example.netije.data.local.db.NetijeDatabase
import com.example.netije.data.local.db.PersonDao
import com.example.netije.data.local.db.TransactionDao
import com.example.netije.data.local.db.UserDao
import com.example.netije.data.local.datastore.SettingsDataStore
import com.example.netije.data.remote.api.AuthApi
import com.example.netije.data.remote.api.PersonsApi
import com.example.netije.data.remote.api.ReportsApi
import com.example.netije.data.remote.api.SyncApi
import com.example.netije.data.remote.api.TransactionsApi
import com.example.netije.data.repository.AuthRepositoryImpl
import com.example.netije.data.repository.BackupRepositoryImpl
import com.example.netije.data.repository.ContactsRepositoryImpl
import com.example.netije.data.repository.FinanceRepositoryImpl
import com.example.netije.data.repository.PersonRepositoryImpl
import com.example.netije.data.repository.SettingsRepositoryImpl
import com.example.netije.data.repository.TransactionRepositoryImpl
import com.example.netije.domain.repository.AuthRepository
import com.example.netije.domain.repository.BackupRepository
import com.example.netije.domain.repository.ContactsRepository
import com.example.netije.domain.repository.FinanceRepository
import com.example.netije.domain.repository.PersonRepository
import com.example.netije.BuildConfig
import com.example.netije.domain.repository.SettingsRepository
import com.example.netije.domain.repository.TransactionRepository
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private const val AUTH_PREFS_NAME = "netije_auth_cache"
private const val KEY_ACCESS_TOKEN = "access_token"

/** Package-level: mirrors access token into SharedPreferences cache for OkHttp interceptor. */
internal fun saveAccessToken(ctx: Context, token: String?) {
    ctx.getSharedPreferences(AUTH_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_ACCESS_TOKEN, token)
        .apply()
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().setPrettyPrinting().create()

    private fun baseUrl(): String = BuildConfig.BASE_URL
        .takeIf { it.endsWith("/") }
        ?: (BuildConfig.BASE_URL + "/")

    @Provides
    @Singleton
    fun provideAuthInterceptor(
        @ApplicationContext ctx: Context
    ): Interceptor = Interceptor { chain ->
        val original = chain.request()
        val token = ctx.getSharedPreferences(AUTH_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACCESS_TOKEN, null)
        val request = if (!token.isNullOrBlank()) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else original
        val response = chain.proceed(request)
        if (response.code == 401) {
            saveAccessToken(ctx, null)
            AuthEventBus.emitSessionExpired()
            val body = response.body
            body?.close()
            return@Interceptor response.newBuilder()
                .code(401)
                .protocol(response.protocol)
                .request(response.request)
                .message(response.message)
                .build()
        }
        response
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: Interceptor): OkHttpClient {
        val logger = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(logger)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okhttp: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl())
            .client(okhttp)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)
    @Provides
    @Singleton fun providePersonsApi(retrofit: Retrofit): PersonsApi = retrofit.create(PersonsApi::class.java)
    @Provides
    @Singleton fun provideTransactionsApi(retrofit: Retrofit): TransactionsApi = retrofit.create(TransactionsApi::class.java)
    @Provides
    @Singleton fun provideSyncApi(retrofit: Retrofit): SyncApi = retrofit.create(SyncApi::class.java)
    @Provides
    @Singleton fun provideReportsApi(retrofit: Retrofit): ReportsApi = retrofit.create(ReportsApi::class.java)

    @Provides
    @Singleton fun provideDb(@ApplicationContext ctx: Context): NetijeDatabase = NetijeDatabase.getInstance(ctx)
    @Provides
    @Singleton fun provideUserDao(db: NetijeDatabase): UserDao = db.userDao()
    @Provides
    @Singleton fun providePersonDao(db: NetijeDatabase): PersonDao = db.personDao()
    @Provides
    @Singleton fun provideTransactionDao(db: NetijeDatabase): TransactionDao = db.transactionDao()

    @Provides
    @Singleton fun provideSettingsDataStore(@ApplicationContext ctx: Context): SettingsDataStore = SettingsDataStore(ctx)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindingModule {
    @Binds @Singleton abstract fun auth(i: AuthRepositoryImpl): AuthRepository
    @Binds @Singleton abstract fun person(i: PersonRepositoryImpl): PersonRepository
    @Binds @Singleton abstract fun transaction(i: TransactionRepositoryImpl): TransactionRepository
    @Binds @Singleton abstract fun finance(i: FinanceRepositoryImpl): FinanceRepository
    @Binds @Singleton abstract fun settings(i: SettingsRepositoryImpl): SettingsRepository
    @Binds @Singleton abstract fun backup(i: BackupRepositoryImpl): BackupRepository
    @Binds @Singleton abstract fun contacts(i: ContactsRepositoryImpl): ContactsRepository
}
