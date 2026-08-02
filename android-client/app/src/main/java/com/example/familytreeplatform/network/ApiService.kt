package com.example.familytreeplatform.network

import com.example.familytreeplatform.models.PersonRequest
import com.example.familytreeplatform.models.PersonResponse
import com.example.familytreeplatform.models.PersonListItem
import com.example.familytreeplatform.models.ClaimRequest
import com.example.familytreeplatform.models.ClaimResponse
import com.example.familytreeplatform.models.ClaimReviewItem
import com.example.familytreeplatform.models.MyClaimResponse
import com.example.familytreeplatform.models.HistoryAccessRequestItem
import com.example.familytreeplatform.models.MyHistoryAccessResponse
import com.example.familytreeplatform.models.PagedChangeLog
import com.example.familytreeplatform.models.RequestHistoryAccessBody
import com.example.familytreeplatform.models.ReviewHistoryAccessBody
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
import com.example.familytreeplatform.models.ProfilePhotoItem
import com.example.familytreeplatform.models.MyProfilePhotoResponse
import com.example.familytreeplatform.models.MergePersonsRequest
import com.example.familytreeplatform.models.ProposalItem
import com.example.familytreeplatform.models.ProposalRequest
import com.example.familytreeplatform.models.RelationshipPathResponse
import com.example.familytreeplatform.models.ReviewProposalRequest
import com.example.familytreeplatform.models.ProposalCommentItem
import com.example.familytreeplatform.models.CreateProposalCommentRequest
import com.example.familytreeplatform.models.UserNotificationItem
import com.example.familytreeplatform.models.NotificationHistoryResponse
import com.example.familytreeplatform.models.MarkAllNotificationsReadResponse
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
import retrofit2.http.Multipart
import retrofit2.http.Part
import okhttp3.MultipartBody
import okhttp3.RequestBody
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
import com.example.familytreeplatform.models.DeletePersonRequest
import com.example.familytreeplatform.models.DeletePersonResponse
import com.example.familytreeplatform.models.PersonDeletionImpact
import com.example.familytreeplatform.models.RequestPersonDeletionRequest
import com.example.familytreeplatform.models.AppCompatibilityResponse
import com.example.familytreeplatform.models.LeaveSpaceResponse
import com.example.familytreeplatform.models.OwnershipTransferResponse
import com.example.familytreeplatform.models.RemoveMemberResponse
import com.example.familytreeplatform.models.SpaceMember
import com.example.familytreeplatform.models.TransferOwnershipRequest
import com.example.familytreeplatform.models.UpdateMemberRoleRequest
import com.example.familytreeplatform.models.UpdatePersonVisibilityRequest
import com.example.familytreeplatform.models.MembershipResult
import com.example.familytreeplatform.models.RevokeInvitationResponse
import com.example.familytreeplatform.models.SpaceInvitation
import com.example.familytreeplatform.models.AccountDeletionImpact
import com.example.familytreeplatform.models.DeleteAccountRequest
import com.example.familytreeplatform.models.DeleteAccountResponse
import com.example.familytreeplatform.models.DeleteSpaceRequest
import com.example.familytreeplatform.models.DeleteSpaceResponse
import com.example.familytreeplatform.models.SpaceLifecycleImpact
import retrofit2.http.HTTP

interface ApiService {
    @GET("app-compatibility/android")
    suspend fun checkAppCompatibility(
        @Query("versionCode") versionCode: Int,
        @Query("versionName") versionName: String,
        @Query("apiContractVersion") apiContractVersion: Int,
        @Query("channel") channel: String
    ): Response<AppCompatibilityResponse>

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

    @GET("users/me/deletion-impact")
    suspend fun accountDeletionImpact(): Response<AccountDeletionImpact>

    @HTTP(method = "DELETE", path = "users/me", hasBody = true)
    suspend fun deleteAccount(
        @Body request: DeleteAccountRequest
    ): Response<DeleteAccountResponse>

    @GET("spaces")
    suspend fun listSpaces(): Response<List<FamilySpace>>

    @POST("spaces")
    suspend fun createSpace(@Body request: CreateSpaceRequest): Response<FamilySpace>

    @GET("spaces/{spaceId}/lifecycle-impact")
    suspend fun spaceLifecycleImpact(
        @Path("spaceId") spaceId: String
    ): Response<SpaceLifecycleImpact>

    @POST("spaces/{spaceId}/archive")
    suspend fun archiveSpace(
        @Path("spaceId") spaceId: String
    ): Response<FamilySpace>

    @POST("spaces/{spaceId}/restore")
    suspend fun restoreSpace(
        @Path("spaceId") spaceId: String
    ): Response<FamilySpace>

    @HTTP(method = "DELETE", path = "spaces/{spaceId}", hasBody = true)
    suspend fun deleteSpace(
        @Path("spaceId") spaceId: String,
        @Body request: DeleteSpaceRequest
    ): Response<DeleteSpaceResponse>

    @POST("spaces/invitations")
    suspend fun createInvitation(@Body request: CreateInvitationRequest): Response<CreatedInvitation>

    @GET("spaces/invitations/{token}")
    suspend fun previewInvitation(@Path("token") token: String): Response<InvitationPreview>

    @POST("spaces/invitations/accept")
    suspend fun acceptInvitation(@Body request: AcceptInvitationRequest): Response<FamilySpace>

    @GET("spaces/{spaceId}/invitations")
    suspend fun listSpaceInvitations(
        @Path("spaceId") spaceId: String,
        @Query("status") status: String? = null
    ): Response<List<SpaceInvitation>>

    @DELETE("spaces/{spaceId}/invitations/{inviteId}")
    suspend fun revokeSpaceInvitation(
        @Path("spaceId") spaceId: String,
        @Path("inviteId") inviteId: String
    ): Response<RevokeInvitationResponse>

    @GET("spaces/{spaceId}/members")
    suspend fun listSpaceMembers(
        @Path("spaceId") spaceId: String
    ): Response<List<SpaceMember>>

    @PATCH("spaces/{spaceId}/members/{memberId}")
    suspend fun updateSpaceMemberRole(
        @Path("spaceId") spaceId: String,
        @Path("memberId") memberId: String,
        @Body request: UpdateMemberRoleRequest
    ): Response<MembershipResult>

    @DELETE("spaces/{spaceId}/members/{memberId}")
    suspend fun removeSpaceMember(
        @Path("spaceId") spaceId: String,
        @Path("memberId") memberId: String
    ): Response<RemoveMemberResponse>

    @POST("spaces/{spaceId}/ownership-transfer")
    suspend fun transferSpaceOwnership(
        @Path("spaceId") spaceId: String,
        @Body request: TransferOwnershipRequest
    ): Response<OwnershipTransferResponse>

    @POST("spaces/{spaceId}/leave")
    suspend fun leaveSpace(
        @Path("spaceId") spaceId: String
    ): Response<LeaveSpaceResponse>

    @POST("persons")
    suspend fun createPerson(@Body request: PersonRequest): Response<PersonResponse>

    @GET("persons")
    suspend fun listPersons(@Query("spaceId") spaceId: String): Response<List<PersonListItem>>

    @GET("persons/mutation-results/{clientMutationId}")
    suspend fun resolveCreatePersonMutation(
        @Path("clientMutationId") clientMutationId: String,
        @Query("spaceId") spaceId: String
    ): Response<PersonResponse>

    @PATCH("persons/{personId}/visibility")
    suspend fun updatePersonVisibility(
        @Path("personId") personId: String,
        @Body request: UpdatePersonVisibilityRequest
    ): Response<PersonListItem>

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

    @GET("persons/{personId}/deletion-impact")
    suspend fun personDeletionImpact(
        @Path("personId") personId: String,
        @Query("spaceId") spaceId: String
    ): Response<PersonDeletionImpact>

    @HTTP(method = "DELETE", path = "persons/{personId}", hasBody = true)
    suspend fun deletePerson(
        @Path("personId") personId: String,
        @Body request: DeletePersonRequest
    ): Response<DeletePersonResponse>

    @POST("persons/{personId}/deletion-requests")
    suspend fun requestPersonDeletion(
        @Path("personId") personId: String,
        @Body request: RequestPersonDeletionRequest
    ): Response<ProposalItem>

    @Multipart
    @POST("persons/{personId}/media/upload")
    suspend fun uploadProfilePhoto(
        @Path("personId") personId: String,
        @Query("spaceId") spaceId: String,
        @Part file: MultipartBody.Part,
        @Part("label") label: RequestBody
    ): Response<ProfilePhotoItem>

    @GET("spaces/{spaceId}/profile-photos")
    suspend fun listProfilePhotos(
        @Path("spaceId") spaceId: String
    ): Response<List<ProfilePhotoItem>>

    @GET("spaces/{spaceId}/profile-photos/me")
    suspend fun getMyProfilePhoto(
        @Path("spaceId") spaceId: String
    ): Response<MyProfilePhotoResponse>

    @GET("proposals")
    suspend fun listProposals(@Query("spaceId") spaceId: String): Response<List<ProposalItem>>

    @POST("proposals")
    suspend fun createProposal(@Body request: ProposalRequest): Response<ProposalItem>

    @POST("proposals/approve")
    suspend fun approveProposal(@Body request: ReviewProposalRequest): Response<ProposalItem>

    @POST("proposals/reject")
    suspend fun rejectProposal(@Body request: ReviewProposalRequest): Response<ProposalItem>

    @GET("proposals/{proposalId}/comments")
    suspend fun listProposalComments(
        @Path("proposalId") proposalId: String,
        @Query("spaceId") spaceId: String
    ): Response<List<ProposalCommentItem>>

    @POST("proposals/{proposalId}/comments")
    suspend fun createProposalComment(
        @Path("proposalId") proposalId: String,
        @Body request: CreateProposalCommentRequest
    ): Response<ProposalCommentItem>

    @GET("notifications")
    suspend fun listNotifications(
        @Query("limit") limit: Int = 10
    ): Response<NotificationHistoryResponse>

    @PATCH("notifications/{notificationId}/read")
    suspend fun markNotificationRead(
        @Path("notificationId") notificationId: String
    ): Response<UserNotificationItem>

    @POST("notifications/read-all")
    suspend fun markAllNotificationsRead(): Response<MarkAllNotificationsReadResponse>

    @POST("claims")
    suspend fun createClaim(@Body request: ClaimRequest): Response<ClaimResponse>

    @GET("claims")
    suspend fun listClaims(@Query("spaceId") spaceId: String): Response<List<ClaimReviewItem>>

    @GET("claims/me")
    suspend fun getMyClaim(@Query("spaceId") spaceId: String): Response<MyClaimResponse>

    @POST("claims/verify")
    suspend fun verifyClaim(@Body request: VerifyClaimRequest): Response<ClaimResponse>

    @GET("changes")
    suspend fun listChanges(
        @Query("spaceId") spaceId: String,
        @Query("limit") limit: Int = 10
    ): Response<List<ChangeLog>>

    @GET("changes/full")
    suspend fun listFullHistory(
        @Query("spaceId") spaceId: String,
        @Query("limit") limit: Int = 50,
        @Query("before") before: String? = null
    ): Response<PagedChangeLog>

    @POST("changes/history-access-requests")
    suspend fun requestFullHistoryAccess(
        @Body request: RequestHistoryAccessBody
    ): Response<HistoryAccessRequestItem>

    @GET("changes/history-access-requests/me")
    suspend fun myHistoryAccessRequest(
        @Query("spaceId") spaceId: String
    ): Response<MyHistoryAccessResponse>

    @GET("changes/history-access-requests")
    suspend fun listHistoryAccessRequests(
        @Query("spaceId") spaceId: String
    ): Response<List<HistoryAccessRequestItem>>

    @POST("changes/history-access-requests/{requestId}/review")
    suspend fun reviewHistoryAccessRequest(
        @Path("requestId") requestId: String,
        @Body request: ReviewHistoryAccessBody
    ): Response<HistoryAccessRequestItem>

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
        @Query("spaceId") spaceId: String,
        @Query("clientMutationId") clientMutationId: String
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
