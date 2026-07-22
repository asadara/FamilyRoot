package com.example.familytreeplatform.network

import com.example.familytreeplatform.models.PersonRequest
import com.example.familytreeplatform.models.PersonResponse
import com.example.familytreeplatform.models.PersonListItem
import com.example.familytreeplatform.models.ClaimRequest
import com.example.familytreeplatform.models.ClaimResponse
import com.example.familytreeplatform.models.ClaimReviewItem
import com.example.familytreeplatform.models.VerifyClaimRequest
import com.example.familytreeplatform.models.ChangeLog
import com.example.familytreeplatform.models.ParentChildRequest
import com.example.familytreeplatform.models.RelationshipResponse
import com.example.familytreeplatform.models.RelationsResponse
import com.example.familytreeplatform.models.RelationItem
import com.example.familytreeplatform.models.CreateSpouseRequest
import com.example.familytreeplatform.models.SpouseResponse
import com.example.familytreeplatform.models.UpdateLifeStatusRequest
import com.example.familytreeplatform.models.UpdateProfileRequest
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
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.PATCH
import retrofit2.http.Path
import com.example.familytreeplatform.models.LoginRequest
import com.example.familytreeplatform.models.RegisterRequest
import com.example.familytreeplatform.models.AuthResponse
import com.example.familytreeplatform.models.GoogleLoginRequest
import com.example.familytreeplatform.models.FamilySpace
import com.example.familytreeplatform.models.CreateSpaceRequest
import com.example.familytreeplatform.models.CreateInvitationRequest
import com.example.familytreeplatform.models.CreatedInvitation
import com.example.familytreeplatform.models.AcceptInvitationRequest
import com.example.familytreeplatform.models.InvitationPreview
import com.example.familytreeplatform.models.RefreshTokenRequest
import com.example.familytreeplatform.models.GedcomDocument
import com.example.familytreeplatform.models.ImportGedcomRequest
import com.example.familytreeplatform.models.ImportSummary
import com.example.familytreeplatform.models.RestoreBackupRequest

interface ApiService {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("auth/refresh")
    suspend fun refresh(@Body request: RefreshTokenRequest): Response<AuthResponse>

    @POST("auth/logout")
    suspend fun logout(@Body request: RefreshTokenRequest): Response<Unit>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("auth/google")
    suspend fun googleLogin(@Body request: GoogleLoginRequest): Response<AuthResponse>

    @GET("spaces")
    suspend fun listSpaces(): Response<List<FamilySpace>>

    @POST("spaces")
    suspend fun createSpace(@Body request: CreateSpaceRequest): Response<FamilySpace>

    @POST("spaces/invitations")
    suspend fun createInvitation(@Body request: CreateInvitationRequest): Response<CreatedInvitation>

    @GET("spaces/invitations/{token}")
    suspend fun previewInvitation(@Path("token") token: String): Response<InvitationPreview>

    @POST("spaces/invitations/accept")
    suspend fun acceptInvitation(@Body request: AcceptInvitationRequest): Response<FamilySpace>

    @POST("persons")
    suspend fun createPerson(@Body request: PersonRequest): Response<PersonResponse>

    @GET("persons")
    suspend fun listPersons(@Query("spaceId") spaceId: String): Response<List<PersonListItem>>

    @GET("persons/duplicates")
    suspend fun listDuplicates(@Query("spaceId") spaceId: String): Response<List<DuplicateGroup>>

    @POST("persons/merge")
    suspend fun mergePersons(@Body request: MergePersonsRequest): Response<Map<String, Any>>

    @GET("persons/{personId}/sources")
    suspend fun listSources(
        @Path("personId") personId: String,
        @Query("spaceId") spaceId: String
    ): Response<List<SourceItem>>

    @POST("persons/{personId}/sources")
    suspend fun createSource(
        @Path("personId") personId: String,
        @Body request: SourceRequest
    ): Response<SourceItem>

    @GET("persons/{personId}/media")
    suspend fun listMedia(
        @Path("personId") personId: String,
        @Query("spaceId") spaceId: String
    ): Response<List<MediaItem>>

    @POST("persons/{personId}/media")
    suspend fun createMedia(
        @Path("personId") personId: String,
        @Body request: MediaRequest
    ): Response<MediaItem>

    @GET("proposals")
    suspend fun listProposals(@Query("spaceId") spaceId: String): Response<List<ProposalItem>>

    @POST("proposals")
    suspend fun createProposal(@Body request: ProposalRequest): Response<ProposalItem>

    @POST("proposals/approve")
    suspend fun approveProposal(@Body request: ReviewProposalRequest): Response<ProposalItem>

    @POST("proposals/reject")
    suspend fun rejectProposal(@Body request: ReviewProposalRequest): Response<ProposalItem>

    @POST("claims")
    suspend fun createClaim(@Body request: ClaimRequest): Response<ClaimResponse>

    @GET("claims")
    suspend fun listClaims(@Query("spaceId") spaceId: String): Response<List<ClaimReviewItem>>

    @POST("claims/verify")
    suspend fun verifyClaim(@Body request: VerifyClaimRequest): Response<ClaimResponse>

    @GET("changes")
    suspend fun listChanges(
        @Query("spaceId") spaceId: String,
        @Query("limit") limit: Int = 50
    ): Response<List<ChangeLog>>

    @POST("persons/parent-child")
    suspend fun addParentChild(@Body request: ParentChildRequest): Response<RelationshipResponse>

    @GET("relationships")
    suspend fun getRelations(
        @Query("spaceId") spaceId: String,
        @Query("personId") personId: String
    ): Response<RelationsResponse>

    @GET("relationships")
    suspend fun listRelationships(
        @Query("spaceId") spaceId: String
    ): Response<List<RelationItem>>

    @GET("relationships/path")
    suspend fun relationshipPath(
        @Query("spaceId") spaceId: String,
        @Query("fromPersonId") fromPersonId: String,
        @Query("toPersonId") toPersonId: String
    ): Response<RelationshipPathResponse>

    @POST("relationships/spouse")
    suspend fun createSpouse(@Body request: CreateSpouseRequest): Response<SpouseResponse>

    @DELETE("relationships/{relationshipId}")
    suspend fun deleteRelationship(
        @Path("relationshipId") relationshipId: String,
        @Query("spaceId") spaceId: String
    ): Response<com.example.familytreeplatform.models.DeleteRelationshipResponse>

    @PATCH("persons/{personId}/life")
    suspend fun updateLifeStatus(
        @Path("personId") personId: String,
        @Body request: UpdateLifeStatusRequest
    ): Response<PersonResponse>

    @PATCH("persons/{personId}/profile")
    suspend fun updateProfile(
        @Path("personId") personId: String,
        @Body request: UpdateProfileRequest
    ): Response<PersonResponse>

    @GET("export/space")
    suspend fun exportSpace(@Query("spaceId") spaceId: String): Response<ExportSpaceResponse>

    @GET("export/space/gedcom")
    suspend fun exportGedcom(@Query("spaceId") spaceId: String): Response<GedcomDocument>

    @POST("export/space/gedcom/import")
    suspend fun importGedcom(@Body request: ImportGedcomRequest): Response<ImportSummary>

    @GET("export/space/backup")
    suspend fun createBackup(@Query("spaceId") spaceId: String): Response<Map<String, Any?>>

    @POST("export/space/backup/restore")
    suspend fun restoreBackup(@Body request: RestoreBackupRequest): Response<ImportSummary>
}
