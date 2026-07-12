package com.example.familytreeplatform.repository

import com.example.familytreeplatform.Config
import com.example.familytreeplatform.BuildConfig
import com.example.familytreeplatform.SessionStore
import com.example.familytreeplatform.models.ClaimRequest
import com.example.familytreeplatform.models.ClaimResponse
import com.example.familytreeplatform.models.ChangeLog
import com.example.familytreeplatform.models.VerifyClaimRequest
import com.example.familytreeplatform.models.ParentChildRequest
import com.example.familytreeplatform.models.RelationshipResponse
import com.example.familytreeplatform.models.RelationsResponse
import com.example.familytreeplatform.models.RelationItem
import com.example.familytreeplatform.models.CreateSpouseRequest
import com.example.familytreeplatform.models.SpouseResponse
import com.example.familytreeplatform.models.UpdateLifeStatusRequest
import com.example.familytreeplatform.models.PersonListItem
import com.example.familytreeplatform.models.PersonRequest
import com.example.familytreeplatform.models.PersonResponse
import com.example.familytreeplatform.models.ExportSpaceResponse
import com.example.familytreeplatform.network.ApiService
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.example.familytreeplatform.models.LoginRequest
import com.example.familytreeplatform.models.RegisterRequest
import com.example.familytreeplatform.models.AuthResponse
import com.example.familytreeplatform.models.FamilySpace
import com.example.familytreeplatform.models.CreateSpaceRequest
import com.example.familytreeplatform.models.CreateInvitationRequest
import com.example.familytreeplatform.models.CreatedInvitation
import com.example.familytreeplatform.models.AcceptInvitationRequest
import com.example.familytreeplatform.models.InvitationPreview
import com.example.familytreeplatform.data.local.PersonDao
import com.example.familytreeplatform.data.local.toEntity
import com.example.familytreeplatform.data.local.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

class PersonRepository(private val personDao: PersonDao? = null) {
    private val apiService: ApiService

    init {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        val actorHeader = Interceptor { chain ->
            val original = chain.request()
            val token = SessionStore.accessToken.value
            val request = if (!token.isNullOrBlank()) {
                original.newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                original
            }
            chain.proceed(request)
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(actorHeader)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(Config.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(ApiService::class.java)
    }

    suspend fun login(email: String, password: String): Result<AuthResponse> = apiResult {
        apiService.login(LoginRequest(email, password))
    }

    suspend fun register(email: String, displayName: String, password: String): Result<AuthResponse> = apiResult {
        apiService.register(RegisterRequest(email, displayName, password))
    }

    suspend fun listSpaces(): Result<List<FamilySpace>> = apiResult { apiService.listSpaces() }

    suspend fun createSpace(name: String): Result<FamilySpace> = apiResult {
        apiService.createSpace(CreateSpaceRequest(name))
    }

    suspend fun createInvitation(spaceId: String, role: String, expiresInDays: Int): Result<CreatedInvitation> = apiResult {
        apiService.createInvitation(CreateInvitationRequest(spaceId, role, expiresInDays))
    }

    suspend fun previewInvitation(token: String): Result<InvitationPreview> = apiResult {
        apiService.previewInvitation(token)
    }

    suspend fun acceptInvitation(token: String): Result<FamilySpace> = apiResult {
        apiService.acceptInvitation(AcceptInvitationRequest(token))
    }

    private suspend fun <T> apiResult(call: suspend () -> Response<T>): Result<T> {
        return try {
            val response = call()
            if (response.isSuccessful) {
                response.body()?.let { Result.success(it) } ?: Result.failure(Exception("Empty response"))
            } else Result.failure(Exception(parseError(response)))
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun parseError(response: Response<*>): String {
        val raw = response.errorBody()?.string()
        if (raw.isNullOrBlank()) {
            return "${response.code()} ${response.message()}"
        }

        return try {
            val json = Gson().fromJson(raw, Map::class.java)
            val message = json["message"]
            when (message) {
                is String -> message
                is List<*> -> message.filterIsInstance<String>().joinToString(", ")
                else -> raw
            }
        } catch (_: JsonSyntaxException) {
            raw
        }
    }

    suspend fun createPerson(request: PersonRequest): Result<PersonResponse> {
        return try {
            val response = apiService.createPerson(request)
            if (response.isSuccessful) {
                response.body()?.let { Result.success(it) } ?: Result.failure(Exception("Empty response"))
            } else {
                Result.failure(Exception(parseError(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listPersons(spaceId: String): Result<List<PersonListItem>> {
        return try {
            val response = apiService.listPersons(spaceId)
            if (response.isSuccessful) {
                response.body()?.let { list ->
                    personDao?.replaceSpace(spaceId, list.map { it.toEntity(spaceId) })
                    Result.success(list)
                } ?: Result.failure(Exception("Empty response"))
            } else {
                Result.failure(Exception(parseError(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observePersons(spaceId: String): Flow<List<PersonListItem>> =
        personDao?.observeBySpace(spaceId)?.map { list -> list.map { it.toModel() } } ?: emptyFlow()

    fun observePerson(personId: String): Flow<PersonListItem?> =
        personDao?.observeById(personId)?.map { it?.toModel() } ?: emptyFlow()

    suspend fun createClaim(request: ClaimRequest): Result<ClaimResponse> {
        return try {
            val response = apiService.createClaim(request)
            if (response.isSuccessful) {
                response.body()?.let { Result.success(it) } ?: Result.failure(Exception("Empty response"))
            } else {
                Result.failure(Exception(parseError(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyClaim(request: VerifyClaimRequest): Result<ClaimResponse> {
        return try {
            val response = apiService.verifyClaim(request)
            if (response.isSuccessful) {
                response.body()?.let { Result.success(it) } ?: Result.failure(Exception("Empty response"))
            } else {
                Result.failure(Exception(parseError(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listChanges(spaceId: String, limit: Int = 50): Result<List<ChangeLog>> {
        return try {
            val response = apiService.listChanges(spaceId, limit)
            if (response.isSuccessful) {
                response.body()?.let { Result.success(it) } ?: Result.failure(Exception("Empty response"))
            } else {
                Result.failure(Exception(parseError(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addParentChild(request: ParentChildRequest): Result<RelationshipResponse> {
        return try {
            val response = apiService.addParentChild(request)
            if (response.isSuccessful) {
                response.body()?.let { Result.success(it) } ?: Result.failure(Exception("Empty response"))
            } else {
                Result.failure(Exception(parseError(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRelations(spaceId: String, personId: String): Result<RelationsResponse> {
        return try {
            val response = apiService.getRelations(spaceId, personId)
            if (response.isSuccessful) {
                response.body()?.let { Result.success(it) } ?: Result.failure(Exception("Empty response"))
            } else {
                Result.failure(Exception(parseError(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listRelationships(spaceId: String): Result<List<RelationItem>> {
        return try {
            val response = apiService.listRelationships(spaceId)
            if (response.isSuccessful) {
                response.body()?.let { Result.success(it) } ?: Result.failure(Exception("Empty response"))
            } else {
                Result.failure(Exception(parseError(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createSpouse(request: CreateSpouseRequest): Result<SpouseResponse> {
        return try {
            val response = apiService.createSpouse(request)
            if (response.isSuccessful) {
                response.body()?.let { Result.success(it) } ?: Result.failure(Exception("Empty response"))
            } else {
                Result.failure(Exception(parseError(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateLifeStatus(personId: String, request: UpdateLifeStatusRequest): Result<PersonResponse> {
        return try {
            val response = apiService.updateLifeStatus(personId, request)
            if (response.isSuccessful) {
                response.body()?.let { Result.success(it) } ?: Result.failure(Exception("Empty response"))
            } else {
                Result.failure(Exception(parseError(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportSpace(spaceId: String): Result<ExportSpaceResponse> {
        return try {
            val response = apiService.exportSpace(spaceId)
            if (response.isSuccessful) {
                response.body()?.let { Result.success(it) } ?: Result.failure(Exception("Empty response"))
            } else {
                Result.failure(Exception(parseError(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
