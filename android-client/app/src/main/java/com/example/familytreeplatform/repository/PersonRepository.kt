package com.example.familytreeplatform.repository

import android.content.Context
import com.example.familytreeplatform.Config
import com.example.familytreeplatform.BuildConfig
import com.example.familytreeplatform.SessionStore
import com.example.familytreeplatform.models.ClaimRequest
import com.example.familytreeplatform.models.ClaimResponse
import com.example.familytreeplatform.models.ClaimReviewItem
import com.example.familytreeplatform.models.ChangeLog
import com.example.familytreeplatform.models.VerifyClaimRequest
import com.example.familytreeplatform.models.ParentChildRequest
import com.example.familytreeplatform.models.RelationshipResponse
import com.example.familytreeplatform.models.RelationsResponse
import com.example.familytreeplatform.models.RelationItem
import com.example.familytreeplatform.models.CreateSpouseRequest
import com.example.familytreeplatform.models.SpouseResponse
import com.example.familytreeplatform.models.UpdateLifeStatusRequest
import com.example.familytreeplatform.models.UpdateProfileRequest
import com.example.familytreeplatform.models.PersonListItem
import com.example.familytreeplatform.models.PersonRequest
import com.example.familytreeplatform.models.PersonResponse
import com.example.familytreeplatform.models.ExportSpaceResponse
import com.example.familytreeplatform.models.DuplicateGroup
import com.example.familytreeplatform.models.MediaItem
import com.example.familytreeplatform.models.MediaRequest
import com.example.familytreeplatform.models.MergePersonsRequest
import com.example.familytreeplatform.models.ProposalItem
import com.example.familytreeplatform.models.ProposalRequest
import com.example.familytreeplatform.models.RelationshipPathResponse
import com.example.familytreeplatform.models.ReviewProposalRequest
import com.example.familytreeplatform.models.SourceItem
import com.example.familytreeplatform.models.SourceRequest
import com.example.familytreeplatform.network.ApiService
import com.example.familytreeplatform.network.ApiException
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import okhttp3.Interceptor
import okhttp3.Authenticator
import okhttp3.Request
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
import com.example.familytreeplatform.models.RefreshTokenRequest
import com.example.familytreeplatform.data.local.PersonDao
import com.example.familytreeplatform.data.local.OfflineMutationDao
import com.example.familytreeplatform.data.local.OfflineMutationEntity
import com.example.familytreeplatform.data.local.OfflineMutationStatus
import com.example.familytreeplatform.data.local.OfflineMutationType
import com.example.familytreeplatform.data.local.RelationshipDao
import com.example.familytreeplatform.data.local.CachedRelationshipEntity
import com.example.familytreeplatform.data.local.toEntity
import com.example.familytreeplatform.data.local.toModel
import com.example.familytreeplatform.models.ApiConflictEnvelope
import com.example.familytreeplatform.models.LifeStatusMutationPayload
import com.example.familytreeplatform.models.PersonConflictSnapshot
import com.example.familytreeplatform.models.ProfileMutationPayload
import com.example.familytreeplatform.models.ParentChildMutationPayload
import com.example.familytreeplatform.models.SpouseMutationPayload
import com.example.familytreeplatform.sync.OfflineSyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class SyncBatchResult { COMPLETE, RETRY }

class PersonRepository(
    private val personDao: PersonDao? = null,
    private val mutationDao: OfflineMutationDao? = null,
    private val relationshipDao: RelationshipDao? = null,
    private val appContext: Context? = null
) {
    private val apiService: ApiService
    private val sessionApiService: ApiService
    private val refreshLock = Any()

    init {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            redactHeader("Authorization")
            redactHeader("Cookie")
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
        val sessionClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
        sessionApiService = Retrofit.Builder()
            .baseUrl(Config.BASE_URL)
            .client(sessionClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(actorHeader)
            .authenticator(Authenticator { _, response -> refreshRequest(response) })
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

    fun acceptSession(auth: AuthResponse) {
        SessionStore.saveSession(
            auth.accessToken,
            auth.refreshToken,
            auth.user.userId,
            auth.user.displayName,
            auth.user.email
        )
    }

    suspend fun restoreSession() {
        if (SessionStore.refreshToken().isNullOrBlank()) {
            SessionStore.finishRestore()
            return
        }
        val restored = ensureAccessToken()
        SessionStore.finishRestore()
        if (restored) resumeOfflineSync()
    }

    suspend fun logout() {
        val refreshToken = SessionStore.refreshToken()
        try {
            if (!refreshToken.isNullOrBlank()) {
                sessionApiService.logout(RefreshTokenRequest(refreshToken))
            }
        } catch (_: Exception) {
            // Local logout must still succeed when the server is unavailable.
        } finally {
            SessionStore.clear()
        }
    }

    /**
     * Removes the locally cached copy of one Family Space without deleting server data.
     * Pending/conflicted edits are deliberately protected because clearing them would lose
     * contributions which have not reached the server yet.
     */
    suspend fun clearOfflineSpaceData(spaceId: String): Result<Unit> = runCatching {
        val queue = requireNotNull(mutationDao) { "Offline mutation queue is unavailable" }
        require(queue.countForSpace(spaceId) == 0) {
            "Resolve or sync pending offline changes before clearing this device"
        }
        relationshipDao?.deleteBySpace(spaceId)
        personDao?.deleteBySpace(spaceId)
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
            return ApiException(
                statusCode = response.code(),
                errorCode = null,
                serverMessage = response.message().ifBlank { "Request failed" }
            ).message.orEmpty()
        }

        return try {
            val json = Gson().fromJson(raw, Map::class.java)
            val message = json["message"]
            val serverMessage = when (message) {
                is String -> message
                is List<*> -> message.filterIsInstance<String>().joinToString(", ")
                else -> raw
            }
            val code = json["code"] as? String
            val status = (json["statusCode"] as? Number)?.toInt() ?: response.code()
            ApiException(status, code, serverMessage).message.orEmpty()
        } catch (_: Exception) {
            ApiException(response.code(), null, raw).message.orEmpty()
        }
    }

    suspend fun createPerson(request: PersonRequest): Result<PersonResponse> {
        return try {
            val response = apiService.createPerson(request)
            if (response.isSuccessful) {
                response.body()?.let { person ->
                    personDao?.upsertAll(listOf(person.toEntity()))
                    Result.success(person)
                } ?: Result.failure(Exception("Empty response"))
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
                    reapplyQueuedMutations(spaceId)
                    val mergedLocal = personDao
                        ?.listBySpace(spaceId)
                        ?.map { entity -> entity.toModel() }
                    Result.success(mergedLocal ?: list)
                } ?: Result.failure(Exception("Empty response"))
            } else {
                Result.failure(Exception(parseError(response)))
            }
        } catch (e: Exception) {
            personDao?.listBySpace(spaceId)?.takeIf { it.isNotEmpty() }
                ?.let { Result.success(it.map { entity -> entity.toModel() }) }
                ?: Result.failure(e)
        }
    }

    fun observePersons(spaceId: String): Flow<List<PersonListItem>> =
        personDao?.observeBySpace(spaceId)?.map { list -> list.map { it.toModel() } } ?: emptyFlow()

    fun observeRelationships(spaceId: String): Flow<List<RelationItem>> =
        relationshipDao?.observeBySpace(spaceId)?.map { list -> list.map { it.toModel() } } ?: emptyFlow()

    fun observePerson(personId: String): Flow<PersonListItem?> =
        personDao?.observeById(personId)?.map { it?.toModel() } ?: emptyFlow()

    fun observeOfflineMutations(personId: String): Flow<List<OfflineMutationEntity>> =
        mutationDao?.observeForPerson(personId) ?: flowOf(emptyList())

    fun observeOfflineMutationCount(spaceId: String): Flow<Int> =
        mutationDao?.observeCountForSpace(spaceId) ?: flowOf(0)

    suspend fun queueLifeStatusUpdate(
        spaceId: String,
        personId: String,
        lifeStatus: String,
        deceasedAt: String? = null
    ): Result<OfflineMutationEntity> = runCatching {
        val localPersonDao = requireNotNull(personDao) { "Offline person cache is unavailable" }
        val localPerson = requireNotNull(localPersonDao.getById(personId)) {
            "Person must be available offline before it can be edited"
        }
        val dao = requireNotNull(mutationDao) { "Offline mutation queue is unavailable" }
        val now = System.currentTimeMillis()
        val payload = LifeStatusMutationPayload(lifeStatus, deceasedAt)
        val mutation = OfflineMutationEntity(
            mutationId = UUID.randomUUID().toString(),
            spaceId = spaceId,
            personId = personId,
            mutationType = OfflineMutationType.UPDATE_LIFE_STATUS,
            payloadJson = Gson().toJson(payload),
            baseVersion = localPerson.version,
            status = OfflineMutationStatus.PENDING,
            attemptCount = 0,
            lastError = null,
            conflictVersion = null,
            conflictPayloadJson = null,
            createdAt = now,
            updatedAt = now
        )
        dao.deleteForPersonAndType(personId, OfflineMutationType.UPDATE_LIFE_STATUS)
        dao.upsert(mutation)
        localPersonDao.updateLifeStatusLocally(personId, lifeStatus, deceasedAt)
        appContext?.let(OfflineSyncScheduler::enqueue)
        mutation
    }

    suspend fun queueProfileUpdate(
        spaceId: String,
        personId: String,
        birthPlace: String,
        notes: String
    ): Result<OfflineMutationEntity> = runCatching {
        val localPersonDao = requireNotNull(personDao) { "Offline person cache is unavailable" }
        val localPerson = requireNotNull(localPersonDao.getById(personId)) {
            "Person must be available offline before it can be edited"
        }
        val dao = requireNotNull(mutationDao) { "Offline mutation queue is unavailable" }
        val now = System.currentTimeMillis()
        val payload = ProfileMutationPayload(birthPlace.trim(), notes.trim())
        val mutation = OfflineMutationEntity(
            mutationId = UUID.randomUUID().toString(),
            spaceId = spaceId,
            personId = personId,
            mutationType = OfflineMutationType.UPDATE_PROFILE,
            payloadJson = Gson().toJson(payload),
            baseVersion = localPerson.version,
            status = OfflineMutationStatus.PENDING,
            attemptCount = 0,
            lastError = null,
            conflictVersion = null,
            conflictPayloadJson = null,
            createdAt = now,
            updatedAt = now
        )
        dao.deleteForPersonAndType(personId, OfflineMutationType.UPDATE_PROFILE)
        dao.upsert(mutation)
        localPersonDao.updateProfileLocally(
            personId,
            payload.birthPlace.ifBlank { null },
            payload.notes.ifBlank { null }
        )
        appContext?.let(OfflineSyncScheduler::enqueue)
        mutation
    }

    suspend fun queueParentChild(
        request: ParentChildRequest,
        focusPersonId: String
    ): Result<OfflineMutationEntity> = runCatching {
        require(request.parentId != request.childId) { "Parent and child cannot be the same person" }
        val peopleDao = requireNotNull(personDao) { "Offline person cache is unavailable" }
        requireNotNull(peopleDao.getById(request.parentId)) { "Parent is not available offline" }
        requireNotNull(peopleDao.getById(request.childId)) { "Child is not available offline" }
        val relationsDao = requireNotNull(relationshipDao) { "Offline relationship cache is unavailable" }
        val relationships = relationsDao.listBySpace(request.spaceId)
        require(relationships.none {
            it.type == "PARENT_CHILD" &&
                it.fromPersonId == request.parentId && it.toPersonId == request.childId
        }) { "Relationship already exists" }
        require(!hasParentChildPath(relationships, request.childId, request.parentId)) {
            "Cycle detected in family tree"
        }
        if (request.meta == "BIOLOGICAL") {
            require(relationships.count {
                it.type == "PARENT_CHILD" && it.toPersonId == request.childId && it.meta == "BIOLOGICAL"
            } < 2) { "Child already has 2 biological parents" }
        }

        val queueDao = requireNotNull(mutationDao) { "Offline mutation queue is unavailable" }
        val now = System.currentTimeMillis()
        val mutation = OfflineMutationEntity(
            mutationId = request.clientMutationId,
            spaceId = request.spaceId,
            personId = focusPersonId,
            mutationType = OfflineMutationType.ADD_PARENT_CHILD,
            payloadJson = Gson().toJson(
                ParentChildMutationPayload(request.parentId, request.childId, request.meta)
            ),
            baseVersion = 0,
            status = OfflineMutationStatus.PENDING,
            attemptCount = 0,
            lastError = null,
            conflictVersion = null,
            conflictPayloadJson = null,
            createdAt = now,
            updatedAt = now
        )
        queueDao.upsert(mutation)
        relationsDao.upsert(
            CachedRelationshipEntity(
                relationshipId = "local-${mutation.mutationId}",
                spaceId = request.spaceId,
                type = "PARENT_CHILD",
                fromPersonId = request.parentId,
                toPersonId = request.childId,
                meta = request.meta,
                startDate = null,
                endDate = null,
                createdAt = isoTime(now),
                pendingMutationId = mutation.mutationId
            )
        )
        appContext?.let(OfflineSyncScheduler::enqueue)
        mutation
    }

    suspend fun queueSpouse(
        request: CreateSpouseRequest,
        focusPersonId: String
    ): Result<OfflineMutationEntity> = runCatching {
        require(request.personAId != request.personBId) { "Spouse cannot be the same person" }
        val peopleDao = requireNotNull(personDao) { "Offline person cache is unavailable" }
        val personA = requireNotNull(peopleDao.getById(request.personAId)) { "First spouse is unavailable offline" }
        val personB = requireNotNull(peopleDao.getById(request.personBId)) { "Second spouse is unavailable offline" }
        if (personA.gender == "MALE") require(personB.gender == "FEMALE") { "Spouse gender must be FEMALE for male" }
        if (personA.gender == "FEMALE") require(personB.gender == "MALE") { "Spouse gender must be MALE for female" }
        require(request.endDate == null || request.endDate >= request.startDate) { "endDate must be >= startDate" }
        val fromId = minOf(request.personAId, request.personBId)
        val toId = maxOf(request.personAId, request.personBId)
        val relationsDao = requireNotNull(relationshipDao) { "Offline relationship cache is unavailable" }
        require(relationsDao.listBySpace(request.spaceId).none {
            it.type == "SPOUSE" && it.fromPersonId == fromId && it.toPersonId == toId
        }) { "Spouse relationship already exists" }

        val queueDao = requireNotNull(mutationDao) { "Offline mutation queue is unavailable" }
        val now = System.currentTimeMillis()
        val mutation = OfflineMutationEntity(
            mutationId = request.clientMutationId,
            spaceId = request.spaceId,
            personId = focusPersonId,
            mutationType = OfflineMutationType.ADD_SPOUSE,
            payloadJson = Gson().toJson(
                SpouseMutationPayload(
                    request.personAId,
                    request.personBId,
                    request.meta,
                    request.startDate,
                    request.endDate
                )
            ),
            baseVersion = 0,
            status = OfflineMutationStatus.PENDING,
            attemptCount = 0,
            lastError = null,
            conflictVersion = null,
            conflictPayloadJson = null,
            createdAt = now,
            updatedAt = now
        )
        queueDao.upsert(mutation)
        relationsDao.upsert(
            CachedRelationshipEntity(
                relationshipId = "local-${mutation.mutationId}",
                spaceId = request.spaceId,
                type = "SPOUSE",
                fromPersonId = fromId,
                toPersonId = toId,
                meta = request.meta,
                startDate = request.startDate,
                endDate = request.endDate,
                createdAt = isoTime(now),
                pendingMutationId = mutation.mutationId
            )
        )
        appContext?.let(OfflineSyncScheduler::enqueue)
        mutation
    }

    suspend fun syncPendingMutations(): SyncBatchResult {
        val dao = mutationDao ?: return SyncBatchResult.COMPLETE
        if (SessionStore.accessToken.value.isNullOrBlank() && !ensureAccessToken()) {
            return SyncBatchResult.RETRY
        }
        dao.resetInterrupted(System.currentTimeMillis())
        for (mutation in dao.listReady()) {
            val result = syncMutation(mutation)
            if (result == SyncBatchResult.RETRY) return result
        }
        return SyncBatchResult.COMPLETE
    }

    suspend fun keepLocalConflict(mutationId: String, serverVersion: Int) {
        mutationDao?.retryWithVersion(mutationId, serverVersion, System.currentTimeMillis())
        appContext?.let(OfflineSyncScheduler::enqueue)
    }

    suspend fun useServerConflict(mutation: OfflineMutationEntity) {
        val snapshot = mutation.conflictPayloadJson
            ?.let { runCatching { Gson().fromJson(it, PersonConflictSnapshot::class.java) }.getOrNull() }
            ?: return
        when (mutation.mutationType) {
            OfflineMutationType.UPDATE_LIFE_STATUS -> snapshot.lifeStatus?.let { lifeStatus ->
                personDao?.applySyncedLifeStatus(
                    snapshot.personId,
                    lifeStatus,
                    snapshot.deceasedAt,
                    snapshot.version
                )
            }
            OfflineMutationType.UPDATE_PROFILE -> personDao?.applySyncedProfile(
                snapshot.personId,
                snapshot.birthPlace,
                snapshot.notes,
                snapshot.version
            )
        }
        mutationDao?.rebasePendingForPerson(
            mutation.personId,
            mutation.mutationId,
            snapshot.version,
            System.currentTimeMillis()
        )
        mutationDao?.delete(mutation.mutationId)
    }

    suspend fun retryFailedMutation(mutationId: String, baseVersion: Int) {
        mutationDao?.retryWithVersion(mutationId, baseVersion, System.currentTimeMillis())
        mutationDao?.getById(mutationId)?.let { reapplyMutation(it) }
        appContext?.let(OfflineSyncScheduler::enqueue)
    }

    suspend fun resumeOfflineSync() {
        appContext?.let(OfflineSyncScheduler::enqueue)
    }

    suspend fun listDuplicates(spaceId: String): Result<List<DuplicateGroup>> =
        apiResult { apiService.listDuplicates(spaceId) }

    suspend fun mergePersons(request: MergePersonsRequest): Result<Map<String, Any>> =
        apiResult { apiService.mergePersons(request) }

    suspend fun listSources(spaceId: String, personId: String): Result<List<SourceItem>> =
        apiResult { apiService.listSources(personId, spaceId) }

    suspend fun createSource(personId: String, request: SourceRequest): Result<SourceItem> =
        apiResult { apiService.createSource(personId, request) }

    suspend fun listMedia(spaceId: String, personId: String): Result<List<MediaItem>> =
        apiResult { apiService.listMedia(personId, spaceId) }

    suspend fun createMedia(personId: String, request: MediaRequest): Result<MediaItem> =
        apiResult { apiService.createMedia(personId, request) }

    suspend fun listProposals(spaceId: String): Result<List<ProposalItem>> =
        apiResult { apiService.listProposals(spaceId) }

    suspend fun createProposal(request: ProposalRequest): Result<ProposalItem> =
        apiResult { apiService.createProposal(request) }

    suspend fun approveProposal(request: ReviewProposalRequest): Result<ProposalItem> =
        apiResult { apiService.approveProposal(request) }

    suspend fun rejectProposal(request: ReviewProposalRequest): Result<ProposalItem> =
        apiResult { apiService.rejectProposal(request) }

    suspend fun relationshipPath(
        spaceId: String,
        fromPersonId: String,
        toPersonId: String
    ): Result<RelationshipPathResponse> =
        apiResult { apiService.relationshipPath(spaceId, fromPersonId, toPersonId) }

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

    suspend fun listClaims(spaceId: String): Result<List<ClaimReviewItem>> {
        return try {
            val response = apiService.listClaims(spaceId)
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
                response.body()?.let { relations ->
                    relationshipDao?.upsertAll(
                        (relations.parents + relations.children + relations.spouses)
                            .distinctBy { it.relationshipId }
                            .map { it.toEntity(spaceId) }
                    )
                    Result.success(relations)
                } ?: Result.failure(Exception("Empty response"))
            } else {
                Result.failure(Exception(parseError(response)))
            }
        } catch (e: Exception) {
            val cached = relationshipDao?.listBySpace(spaceId).orEmpty()
            if (personDao?.getById(personId) != null) Result.success(cachedRelations(personId, cached))
            else Result.failure(e)
        }
    }

    suspend fun listRelationships(spaceId: String): Result<List<RelationItem>> {
        return try {
            val response = apiService.listRelationships(spaceId)
            if (response.isSuccessful) {
                response.body()?.let { relationships ->
                    relationshipDao?.replaceSynced(spaceId, relationships.map { it.toEntity(spaceId) })
                    reapplyQueuedMutations(spaceId)
                    Result.success(relationshipDao?.listBySpace(spaceId)?.map { it.toModel() } ?: relationships)
                } ?: Result.failure(Exception("Empty response"))
            } else {
                Result.failure(Exception(parseError(response)))
            }
        } catch (e: Exception) {
            relationshipDao?.listBySpace(spaceId)?.let { Result.success(it.map { entity -> entity.toModel() }) }
                ?: Result.failure(e)
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

    suspend fun exportGedcom(spaceId: String) = apiResult {
        apiService.exportGedcom(spaceId)
    }

    suspend fun importGedcom(spaceId: String, content: String) = apiResult {
        apiService.importGedcom(com.example.familytreeplatform.models.ImportGedcomRequest(spaceId, content))
    }

    suspend fun createBackup(spaceId: String): Result<String> = apiResult {
        apiService.createBackup(spaceId)
    }.map { backup -> GsonBuilder().setPrettyPrinting().create().toJson(backup) }

    suspend fun restoreBackup(spaceId: String, content: String): Result<com.example.familytreeplatform.models.ImportSummary> {
        val backup = try {
            val type = object : TypeToken<Map<String, Any?>>() {}.type
            Gson().fromJson<Map<String, Any?>>(content, type)
        } catch (error: Exception) {
            return Result.failure(IllegalArgumentException("Invalid FamilyRoot backup file", error))
        }
        return apiResult {
            apiService.restoreBackup(com.example.familytreeplatform.models.RestoreBackupRequest(spaceId, backup))
        }
    }

    private suspend fun syncMutation(mutation: OfflineMutationEntity): SyncBatchResult {
        val dao = mutationDao ?: return SyncBatchResult.COMPLETE
        val now = System.currentTimeMillis()
        dao.markAttempt(mutation.mutationId, OfflineMutationStatus.SYNCING, null, now)
        if (
            mutation.mutationType == OfflineMutationType.ADD_PARENT_CHILD ||
            mutation.mutationType == OfflineMutationType.ADD_SPOUSE
        ) {
            return syncRelationshipMutation(mutation)
        }
        return try {
            val response: Response<PersonResponse> = when (mutation.mutationType) {
                OfflineMutationType.UPDATE_LIFE_STATUS -> {
                    val payload = runCatching {
                        Gson().fromJson(mutation.payloadJson, LifeStatusMutationPayload::class.java)
                    }.getOrNull() ?: return invalidMutationPayload(dao, mutation)
                    apiService.updateLifeStatus(
                        mutation.personId,
                        UpdateLifeStatusRequest(
                            spaceId = mutation.spaceId,
                            lifeStatus = payload.lifeStatus,
                            deceasedAt = payload.deceasedAt,
                            expectedVersion = mutation.baseVersion,
                            clientMutationId = mutation.mutationId
                        )
                    )
                }
                OfflineMutationType.UPDATE_PROFILE -> {
                    val payload = runCatching {
                        Gson().fromJson(mutation.payloadJson, ProfileMutationPayload::class.java)
                    }.getOrNull() ?: return invalidMutationPayload(dao, mutation)
                    apiService.updateProfile(
                        mutation.personId,
                        UpdateProfileRequest(
                            spaceId = mutation.spaceId,
                            birthPlace = payload.birthPlace,
                            notes = payload.notes,
                            expectedVersion = mutation.baseVersion,
                            clientMutationId = mutation.mutationId
                        )
                    )
                }
                else -> {
                    dao.markStatus(
                        mutation.mutationId,
                        OfflineMutationStatus.FAILED,
                        "Unsupported offline mutation type",
                        System.currentTimeMillis()
                    )
                    return SyncBatchResult.COMPLETE
                }
            }
            if (response.isSuccessful) {
                val person = response.body()
                if (person == null) {
                    dao.markStatus(
                        mutation.mutationId,
                        OfflineMutationStatus.PENDING,
                        "Empty sync response",
                        System.currentTimeMillis()
                    )
                    SyncBatchResult.RETRY
                } else {
                    when (mutation.mutationType) {
                        OfflineMutationType.UPDATE_LIFE_STATUS -> personDao?.applySyncedLifeStatus(
                            mutation.personId,
                            requireNotNull(person.lifeStatus),
                            person.deceasedAt,
                            person.version
                        )
                        OfflineMutationType.UPDATE_PROFILE -> personDao?.applySyncedProfile(
                            mutation.personId,
                            person.birthPlace,
                            person.notes,
                            person.version
                        )
                    }
                    dao.rebasePendingForPerson(
                        mutation.personId,
                        mutation.mutationId,
                        person.version,
                        System.currentTimeMillis()
                    )
                    dao.delete(mutation.mutationId)
                    SyncBatchResult.COMPLETE
                }
            } else {
                val rawError = response.errorBody()?.string()
                if (response.code() == 409) {
                    val envelope = rawError?.let {
                        runCatching { Gson().fromJson(it, ApiConflictEnvelope::class.java) }.getOrNull()
                    }
                    dao.markConflict(
                        mutationId = mutation.mutationId,
                        error = envelope?.message ?: "Server data changed",
                        serverVersion = envelope?.details?.version,
                        conflictPayloadJson = envelope?.details?.let(Gson()::toJson),
                        updatedAt = System.currentTimeMillis()
                    )
                    SyncBatchResult.COMPLETE
                } else if (response.code() >= 500 || response.code() == 401) {
                    dao.markStatus(
                        mutation.mutationId,
                        OfflineMutationStatus.PENDING,
                        rawError ?: "Server unavailable",
                        System.currentTimeMillis()
                    )
                    SyncBatchResult.RETRY
                } else {
                    dao.markStatus(
                        mutation.mutationId,
                        OfflineMutationStatus.FAILED,
                        rawError ?: "Sync rejected (${response.code()})",
                        System.currentTimeMillis()
                    )
                    SyncBatchResult.COMPLETE
                }
            }
        } catch (error: Exception) {
            dao.markStatus(
                mutation.mutationId,
                OfflineMutationStatus.PENDING,
                error.message ?: "Network unavailable",
                System.currentTimeMillis()
            )
            SyncBatchResult.RETRY
        }
    }

    private suspend fun syncRelationshipMutation(mutation: OfflineMutationEntity): SyncBatchResult {
        val queueDao = mutationDao ?: return SyncBatchResult.COMPLETE
        val relationsDao = relationshipDao ?: return SyncBatchResult.COMPLETE
        return try {
            val synced: CachedRelationshipEntity? = when (mutation.mutationType) {
                OfflineMutationType.ADD_PARENT_CHILD -> {
                    val payload = runCatching {
                        Gson().fromJson(mutation.payloadJson, ParentChildMutationPayload::class.java)
                    }.getOrNull() ?: return invalidMutationPayload(queueDao, mutation)
                    val response = apiService.addParentChild(
                        ParentChildRequest(
                            spaceId = mutation.spaceId,
                            parentId = payload.parentId,
                            childId = payload.childId,
                            meta = payload.meta,
                            clientMutationId = mutation.mutationId
                        )
                    )
                    if (!response.isSuccessful) return handleRelationshipSyncError(mutation, response)
                    response.body()?.let {
                        CachedRelationshipEntity(
                            relationshipId = it.relationshipId,
                            spaceId = mutation.spaceId,
                            type = it.type,
                            fromPersonId = it.fromPersonId,
                            toPersonId = it.toPersonId,
                            meta = it.meta,
                            startDate = null,
                            endDate = null,
                            createdAt = it.createdAt ?: isoTime(System.currentTimeMillis()),
                            pendingMutationId = null
                        )
                    }
                }
                OfflineMutationType.ADD_SPOUSE -> {
                    val payload = runCatching {
                        Gson().fromJson(mutation.payloadJson, SpouseMutationPayload::class.java)
                    }.getOrNull() ?: return invalidMutationPayload(queueDao, mutation)
                    val response = apiService.createSpouse(
                        CreateSpouseRequest(
                            spaceId = mutation.spaceId,
                            personAId = payload.personAId,
                            personBId = payload.personBId,
                            meta = payload.meta,
                            startDate = payload.startDate,
                            endDate = payload.endDate,
                            clientMutationId = mutation.mutationId
                        )
                    )
                    if (!response.isSuccessful) return handleRelationshipSyncError(mutation, response)
                    response.body()?.let {
                        CachedRelationshipEntity(
                            relationshipId = it.relationshipId,
                            spaceId = mutation.spaceId,
                            type = it.type,
                            fromPersonId = it.fromPersonId,
                            toPersonId = it.toPersonId,
                            meta = it.meta,
                            startDate = it.startDate,
                            endDate = it.endDate,
                            createdAt = it.createdAt ?: isoTime(System.currentTimeMillis()),
                            pendingMutationId = null
                        )
                    }
                }
                else -> null
            }
            if (synced == null) {
                queueDao.markStatus(
                    mutation.mutationId,
                    OfflineMutationStatus.PENDING,
                    "Empty sync response",
                    System.currentTimeMillis()
                )
                SyncBatchResult.RETRY
            } else {
                relationsDao.deleteByMutation(mutation.mutationId)
                relationsDao.upsert(synced)
                queueDao.delete(mutation.mutationId)
                SyncBatchResult.COMPLETE
            }
        } catch (error: Exception) {
            queueDao.markStatus(
                mutation.mutationId,
                OfflineMutationStatus.PENDING,
                error.message ?: "Network unavailable",
                System.currentTimeMillis()
            )
            SyncBatchResult.RETRY
        }
    }

    private suspend fun handleRelationshipSyncError(
        mutation: OfflineMutationEntity,
        response: Response<*>
    ): SyncBatchResult {
        val queueDao = mutationDao ?: return SyncBatchResult.COMPLETE
        val rawError = response.errorBody()?.string()
        return if (response.code() >= 500 || response.code() == 401) {
            queueDao.markStatus(
                mutation.mutationId,
                OfflineMutationStatus.PENDING,
                rawError ?: "Server unavailable",
                System.currentTimeMillis()
            )
            SyncBatchResult.RETRY
        } else {
            relationshipDao?.deleteByMutation(mutation.mutationId)
            queueDao.markStatus(
                mutation.mutationId,
                OfflineMutationStatus.FAILED,
                rawError ?: "Relationship rejected (${response.code()})",
                System.currentTimeMillis()
            )
            SyncBatchResult.COMPLETE
        }
    }

    private suspend fun invalidMutationPayload(
        dao: OfflineMutationDao,
        mutation: OfflineMutationEntity
    ): SyncBatchResult {
        dao.markStatus(
            mutation.mutationId,
            OfflineMutationStatus.FAILED,
            "Invalid offline mutation payload",
            System.currentTimeMillis()
        )
        return SyncBatchResult.COMPLETE
    }

    private suspend fun reapplyQueuedMutations(spaceId: String) {
        val queueDao = mutationDao ?: return
        queueDao.listForSpace(spaceId).forEach { mutation -> reapplyMutation(mutation) }
    }

    private suspend fun reapplyMutation(mutation: OfflineMutationEntity) {
        val localDao = personDao
        when (mutation.mutationType) {
                OfflineMutationType.UPDATE_LIFE_STATUS -> runCatching {
                    Gson().fromJson(mutation.payloadJson, LifeStatusMutationPayload::class.java)
                }.getOrNull()?.let { payload ->
                    localDao?.updateLifeStatusLocally(mutation.personId, payload.lifeStatus, payload.deceasedAt)
                }
                OfflineMutationType.UPDATE_PROFILE -> runCatching {
                    Gson().fromJson(mutation.payloadJson, ProfileMutationPayload::class.java)
                }.getOrNull()?.let { payload ->
                    localDao?.updateProfileLocally(
                        mutation.personId,
                        payload.birthPlace.ifBlank { null },
                        payload.notes.ifBlank { null }
                    )
                }
                OfflineMutationType.ADD_PARENT_CHILD -> runCatching {
                    Gson().fromJson(mutation.payloadJson, ParentChildMutationPayload::class.java)
                }.getOrNull()?.let { payload ->
                    relationshipDao?.upsert(
                        CachedRelationshipEntity(
                            "local-${mutation.mutationId}", mutation.spaceId, "PARENT_CHILD",
                            payload.parentId, payload.childId, payload.meta, null, null,
                            isoTime(mutation.createdAt), mutation.mutationId
                        )
                    )
                }
                OfflineMutationType.ADD_SPOUSE -> runCatching {
                    Gson().fromJson(mutation.payloadJson, SpouseMutationPayload::class.java)
                }.getOrNull()?.let { payload ->
                    relationshipDao?.upsert(
                        CachedRelationshipEntity(
                            "local-${mutation.mutationId}", mutation.spaceId, "SPOUSE",
                            minOf(payload.personAId, payload.personBId),
                            maxOf(payload.personAId, payload.personBId), payload.meta,
                            payload.startDate, payload.endDate, isoTime(mutation.createdAt), mutation.mutationId
                        )
                    )
                }
        }
    }

    private fun cachedRelations(
        personId: String,
        relationships: List<CachedRelationshipEntity>
    ) = RelationsResponse(
        personId = personId,
        parents = relationships.filter { it.type == "PARENT_CHILD" && it.toPersonId == personId }.map { it.toModel() },
        children = relationships.filter { it.type == "PARENT_CHILD" && it.fromPersonId == personId }.map { it.toModel() },
        spouses = relationships.filter {
            it.type == "SPOUSE" && (it.fromPersonId == personId || it.toPersonId == personId)
        }.map { it.toModel() }
    )

    private fun hasParentChildPath(
        relationships: List<CachedRelationshipEntity>,
        startId: String,
        targetId: String
    ): Boolean {
        val childrenByParent = relationships.filter { it.type == "PARENT_CHILD" }
            .groupBy { it.fromPersonId }
        val pending = ArrayDeque<String>().apply { add(startId) }
        val visited = mutableSetOf<String>()
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (!visited.add(current)) continue
            if (current == targetId) return true
            childrenByParent[current].orEmpty().forEach { pending.add(it.toPersonId) }
        }
        return false
    }

    private fun isoTime(timeMillis: Long): String = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        Locale.US
    ).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(timeMillis))

    private fun refreshRequest(response: okhttp3.Response): Request? {
        if (responseCount(response) >= 2) return null
        val requestToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")
        return synchronized(refreshLock) {
            val latestToken = SessionStore.accessToken.value
            if (!latestToken.isNullOrBlank() && latestToken != requestToken) {
                return@synchronized response.request.newBuilder()
                    .header("Authorization", "Bearer $latestToken")
                    .build()
            }
            if (!refreshSessionLocked()) return@synchronized null
            SessionStore.accessToken.value?.let { token ->
                response.request.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            }
        }
    }

    private suspend fun ensureAccessToken(): Boolean = withContext(Dispatchers.IO) {
        if (!SessionStore.accessToken.value.isNullOrBlank()) return@withContext true
        synchronized(refreshLock) { refreshSessionLocked() }
    }

    private fun refreshSessionLocked(): Boolean {
        val refreshToken = SessionStore.refreshToken() ?: return false
        return try {
            val response = runBlocking {
                sessionApiService.refresh(RefreshTokenRequest(refreshToken))
            }
            if (response.isSuccessful) {
                val auth = response.body() ?: return false
                SessionStore.updateSession(
                    auth.accessToken,
                    auth.refreshToken,
                    auth.user.userId,
                    auth.user.displayName,
                    auth.user.email
                )
                true
            } else {
                if (response.code() == 400 || response.code() == 401) SessionStore.clear()
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun responseCount(response: okhttp3.Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count += 1
            prior = prior.priorResponse
        }
        return count
    }
}
