package com.example.familytreeplatform.feature.spacesettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.familytreeplatform.models.ClaimReviewItem
import com.example.familytreeplatform.models.CreatedInvitation
import com.example.familytreeplatform.models.DuplicateGroup
import com.example.familytreeplatform.models.MergePersonsRequest
import com.example.familytreeplatform.models.ProposalItem
import com.example.familytreeplatform.models.ProposalCommentItem
import com.example.familytreeplatform.models.ReviewProposalRequest
import com.example.familytreeplatform.models.VerifyClaimRequest
import com.example.familytreeplatform.models.PortableDocument
import com.example.familytreeplatform.models.SpaceMember
import com.example.familytreeplatform.models.SpaceInvitation
import com.example.familytreeplatform.models.SpaceLifecycleImpact
import com.example.familytreeplatform.repository.PersonRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SpaceSettingsUiState(
    val role: String = "VIEWER",
    val memberRole: String? = null,
    val loadingInvitePermission: Boolean = true,
    val expiresInDays: String = "7",
    val invitationTargetEmail: String = "",
    val creating: Boolean = false,
    val loadingClaims: Boolean = false,
    val loadingProposals: Boolean = false,
    val loadingDuplicates: Boolean = false,
    val verifyingClaimId: String? = null,
    val reviewingProposalId: String? = null,
    val loadingProposalComments: Set<String> = emptySet(),
    val postingProposalCommentId: String? = null,
    val merging: Boolean = false,
    val invitation: CreatedInvitation? = null,
    val invitationError: String? = null,
    val invitationMessage: String? = null,
    val loadingInvitations: Boolean = false,
    val invitations: List<SpaceInvitation> = emptyList(),
    val invitationStatusFilter: String = "ALL",
    val revokingInvitationId: String? = null,
    val pendingInvitationRevoke: SpaceInvitation? = null,
    val loadingMembers: Boolean = true,
    val members: List<SpaceMember> = emptyList(),
    val spaceName: String = "Silsilah",
    val spaceStatus: String = "ACTIVE",
    val loadingLifecycle: Boolean = false,
    val lifecycleImpact: SpaceLifecycleImpact? = null,
    val lifecycleAction: String? = null,
    val lifecycleMessage: String? = null,
    val showArchiveConfirmation: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val deleteConfirmation: String = "",
    val deleteExportAcknowledged: Boolean = false,
    val spaceDeleted: Boolean = false,
    val membershipActionMemberId: String? = null,
    val membershipMessage: String? = null,
    val pendingRoleChange: PendingRoleChange? = null,
    val pendingRemoval: SpaceMember? = null,
    val pendingTransfer: SpaceMember? = null,
    val showLeaveConfirmation: Boolean = false,
    val leavingSpace: Boolean = false,
    val claims: List<ClaimReviewItem> = emptyList(),
    val proposals: List<ProposalItem> = emptyList(),
    val proposalComments: Map<String, List<ProposalCommentItem>> = emptyMap(),
    val proposalCommentDrafts: Map<String, String> = emptyMap(),
    val duplicates: List<DuplicateGroup> = emptyList(),
    val transferringData: Boolean = false,
    val pendingDocument: PortableDocument? = null,
    val transferMessage: String? = null,
    val clearingOfflineData: Boolean = false,
    val showClearOfflineConfirmation: Boolean = false,
    val privacyMessage: String? = null,
    val error: String? = null
)

data class PendingRoleChange(val member: SpaceMember, val role: String)

class SpaceSettingsViewModel(
    private val spaceId: String,
    private val repository: PersonRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SpaceSettingsUiState())
    val uiState: StateFlow<SpaceSettingsUiState> = _uiState.asStateFlow()

    init {
        refreshInvitationPermission()
        refreshMembers()
        refreshClaims()
        refreshProposals()
        refreshDuplicates()
    }

    fun setRole(value: String) {
        _uiState.update {
            it.copy(
                role = value,
                invitation = null,
                invitationError = null,
                invitationMessage = null
            )
        }
    }

    fun setExpiresInDays(value: String) {
        if (value.all { it.isDigit() }) {
            _uiState.update { it.copy(expiresInDays = value, invitationError = null, invitationMessage = null) }
        }
    }

    fun setInvitationTargetEmail(value: String) {
        _uiState.update {
            it.copy(
                invitationTargetEmail = value.trimStart(),
                invitationError = null,
                invitationMessage = null
            )
        }
    }

    private fun refreshInvitationPermission() {
        viewModelScope.launch {
            repository.listSpaces()
                .onSuccess { spaces ->
                    val currentSpace = spaces.firstOrNull { space -> space.spaceId == spaceId }
                    val role = currentSpace?.role
                    _uiState.update {
                        it.copy(
                            memberRole = role,
                            loadingInvitePermission = false,
                            spaceName = currentSpace?.name ?: it.spaceName,
                            spaceStatus = currentSpace?.status ?: it.spaceStatus
                        )
                    }
                    if (role in setOf("OWNER", "ADMIN")) refreshInvitations()
                    if (role == "OWNER") refreshLifecycle()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            loadingInvitePermission = false,
                            invitationError = error.message
                        )
                    }
                }
        }
    }

    fun createInvitation() {
        val state = _uiState.value
        if (state.memberRole !in setOf("OWNER", "ADMIN")) {
            _uiState.update {
                it.copy(invitationError = "Only OWNER or ADMIN can create invitations")
            }
            return
        }
        val days = state.expiresInDays.toIntOrNull()
        if (days == null || days !in 1..30) {
            _uiState.update { it.copy(invitationError = "Expiry must be between 1 and 30 days") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    creating = true,
                    invitationError = null,
                    invitationMessage = null,
                    invitation = null
                )
            }
            repository.createInvitation(
                spaceId,
                state.role,
                days,
                state.invitationTargetEmail.trim().ifBlank { null }
            )
                .onSuccess { invitation ->
                    _uiState.update {
                        it.copy(
                            creating = false,
                            invitation = invitation,
                            invitationMessage = "Kode undangan berhasil dibuat."
                        )
                    }
                    refreshInvitations()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(creating = false, invitationError = error.message) }
                }
        }
    }

    fun setInvitationStatusFilter(status: String) {
        if (status !in setOf("ALL", "ACTIVE", "ACCEPTED", "REVOKED", "EXPIRED")) return
        _uiState.update { it.copy(invitationStatusFilter = status) }
        refreshInvitations()
    }

    fun refreshInvitations() {
        if (_uiState.value.memberRole !in setOf("OWNER", "ADMIN")) return
        viewModelScope.launch {
            val filter = _uiState.value.invitationStatusFilter
            _uiState.update {
                it.copy(loadingInvitations = true, invitationError = null)
            }
            repository.listSpaceInvitations(
                spaceId,
                filter.takeUnless { it == "ALL" }
            )
                .onSuccess { invitations ->
                    _uiState.update {
                        it.copy(
                            loadingInvitations = false,
                            invitations = invitations
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            loadingInvitations = false,
                            invitationError = error.message
                        )
                    }
                }
        }
    }

    fun requestRevokeInvitation(invitation: SpaceInvitation) {
        if (invitation.status != "ACTIVE") return
        _uiState.update {
            it.copy(
                pendingInvitationRevoke = invitation,
                invitationMessage = null,
                invitationError = null
            )
        }
    }

    fun confirmRevokeInvitation() {
        val invitation = _uiState.value.pendingInvitationRevoke ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    pendingInvitationRevoke = null,
                    revokingInvitationId = invitation.inviteId,
                    invitationMessage = null,
                    invitationError = null
                )
            }
            repository.revokeSpaceInvitation(spaceId, invitation.inviteId)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            revokingInvitationId = null,
                            invitationMessage = "Undangan berhasil dicabut."
                        )
                    }
                    refreshInvitations()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            revokingInvitationId = null,
                            invitationError = error.message
                        )
                    }
                }
        }
    }

    fun refreshMembers() {
        viewModelScope.launch {
            _uiState.update { it.copy(loadingMembers = true, error = null) }
            repository.listSpaceMembers(spaceId)
                .onSuccess { members ->
                    _uiState.update {
                        it.copy(
                            loadingMembers = false,
                            members = members,
                            memberRole = members.firstOrNull { member -> member.isCurrentUser }?.role
                                ?: it.memberRole
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(loadingMembers = false, error = error.message)
                    }
                }
        }
    }

    fun refreshLifecycle() {
        if (_uiState.value.memberRole != "OWNER") return
        viewModelScope.launch {
            _uiState.update {
                it.copy(loadingLifecycle = true, error = null)
            }
            repository.spaceLifecycleImpact(spaceId)
                .onSuccess { impact ->
                    _uiState.update {
                        it.copy(
                            loadingLifecycle = false,
                            lifecycleImpact = impact,
                            spaceName = impact.name,
                            spaceStatus = impact.status
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(loadingLifecycle = false, error = error.message)
                    }
                }
        }
    }

    fun requestArchiveSpace() {
        if (_uiState.value.memberRole != "OWNER" || _uiState.value.spaceStatus != "ACTIVE") return
        _uiState.update {
            it.copy(
                showArchiveConfirmation = true,
                lifecycleMessage = null,
                error = null
            )
        }
    }

    fun confirmArchiveSpace() {
        if (!_uiState.value.showArchiveConfirmation) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    showArchiveConfirmation = false,
                    lifecycleAction = "ARCHIVE",
                    lifecycleMessage = null,
                    error = null
                )
            }
            repository.archiveSpace(spaceId)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            lifecycleAction = null,
                            spaceStatus = "ARCHIVED",
                            lifecycleMessage =
                                "Silsilah diarsipkan. Data tetap dapat dibaca, tetapi tidak dapat diubah."
                        )
                    }
                    refreshLifecycle()
                    refreshInvitations()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(lifecycleAction = null, error = error.message)
                    }
                }
        }
    }

    fun restoreSpace() {
        if (_uiState.value.memberRole != "OWNER" || _uiState.value.spaceStatus != "ARCHIVED") return
        viewModelScope.launch {
            _uiState.update {
                it.copy(lifecycleAction = "RESTORE", lifecycleMessage = null, error = null)
            }
            repository.restoreSpace(spaceId)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            lifecycleAction = null,
                            spaceStatus = "ACTIVE",
                            lifecycleMessage = "Silsilah aktif kembali dan dapat diedit."
                        )
                    }
                    refreshLifecycle()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(lifecycleAction = null, error = error.message)
                    }
                }
        }
    }

    fun requestDeleteSpace() {
        if (_uiState.value.memberRole != "OWNER" || _uiState.value.spaceStatus != "ARCHIVED") return
        _uiState.update {
            it.copy(
                showDeleteConfirmation = true,
                deleteConfirmation = "",
                deleteExportAcknowledged = false,
                lifecycleMessage = null,
                error = null
            )
        }
    }

    fun setDeleteConfirmation(value: String) {
        _uiState.update { it.copy(deleteConfirmation = value, error = null) }
    }

    fun setDeleteExportAcknowledged(value: Boolean) {
        _uiState.update { it.copy(deleteExportAcknowledged = value, error = null) }
    }

    fun confirmDeleteSpace() {
        val state = _uiState.value
        if (
            !state.showDeleteConfirmation ||
            state.deleteConfirmation != state.spaceName ||
            !state.deleteExportAcknowledged
        ) {
            _uiState.update {
                it.copy(error = "Ketik nama silsilah persis dan konfirmasi pilihan ekspor.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    showDeleteConfirmation = false,
                    lifecycleAction = "DELETE",
                    lifecycleMessage = null,
                    error = null
                )
            }
            repository.deleteSpace(
                spaceId,
                state.deleteConfirmation,
                state.deleteExportAcknowledged
            )
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            lifecycleAction = null,
                            spaceDeleted = true,
                            lifecycleMessage = "Silsilah telah dihapus."
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(lifecycleAction = null, error = error.message)
                    }
                }
        }
    }

    fun cancelLifecycleConfirmation() {
        _uiState.update {
            it.copy(
                showArchiveConfirmation = false,
                showDeleteConfirmation = false,
                deleteConfirmation = "",
                deleteExportAcknowledged = false
            )
        }
    }

    fun requestRoleChange(member: SpaceMember, role: String) {
        if (member.role == role || role !in manageableRoles(_uiState.value.memberRole, member)) return
        _uiState.update {
            it.copy(
                pendingRoleChange = PendingRoleChange(member, role),
                membershipMessage = null,
                error = null
            )
        }
    }

    fun confirmRoleChange() {
        val change = _uiState.value.pendingRoleChange ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    pendingRoleChange = null,
                    membershipActionMemberId = change.member.memberId,
                    membershipMessage = null,
                    error = null
                )
            }
            repository.updateSpaceMemberRole(spaceId, change.member.memberId, change.role)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            membershipActionMemberId = null,
                            membershipMessage =
                                "Peran ${change.member.displayName} berhasil diubah."
                        )
                    }
                    refreshMembers()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(membershipActionMemberId = null, error = error.message)
                    }
                }
        }
    }

    fun requestRemoveMember(member: SpaceMember) {
        if (manageableRoles(_uiState.value.memberRole, member).isEmpty()) return
        _uiState.update {
            it.copy(pendingRemoval = member, membershipMessage = null, error = null)
        }
    }

    fun confirmRemoveMember() {
        val member = _uiState.value.pendingRemoval ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    pendingRemoval = null,
                    membershipActionMemberId = member.memberId,
                    membershipMessage = null,
                    error = null
                )
            }
            repository.removeSpaceMember(spaceId, member.memberId)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            membershipActionMemberId = null,
                            membershipMessage = "${member.displayName} telah dikeluarkan."
                        )
                    }
                    refreshMembers()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(membershipActionMemberId = null, error = error.message)
                    }
                }
        }
    }

    fun requestTransferOwnership(member: SpaceMember) {
        if (_uiState.value.memberRole != "OWNER" || member.isCurrentUser || member.role == "OWNER") return
        _uiState.update {
            it.copy(pendingTransfer = member, membershipMessage = null, error = null)
        }
    }

    fun confirmTransferOwnership() {
        val member = _uiState.value.pendingTransfer ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    pendingTransfer = null,
                    membershipActionMemberId = member.memberId,
                    membershipMessage = null,
                    error = null
                )
            }
            repository.transferSpaceOwnership(spaceId, member.memberId)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            membershipActionMemberId = null,
                            memberRole = "ADMIN",
                            membershipMessage =
                                "${member.displayName} sekarang menjadi Pemilik. Peran Anda berubah menjadi Pengelola."
                        )
                    }
                    refreshMembers()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(membershipActionMemberId = null, error = error.message)
                    }
                }
        }
    }

    fun requestLeaveSpace() {
        _uiState.update {
            it.copy(showLeaveConfirmation = true, membershipMessage = null, error = null)
        }
    }

    fun confirmLeaveSpace() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    showLeaveConfirmation = false,
                    leavingSpace = true,
                    membershipMessage = null,
                    error = null
                )
            }
            repository.leaveSpace(spaceId)
                .onFailure { error ->
                    _uiState.update {
                        it.copy(leavingSpace = false, error = error.message)
                    }
                }
        }
    }

    fun cancelMembershipConfirmation() {
        _uiState.update {
            it.copy(
                pendingRoleChange = null,
                pendingRemoval = null,
                pendingTransfer = null,
                pendingInvitationRevoke = null,
                showLeaveConfirmation = false
            )
        }
    }

    fun refreshClaims() {
        viewModelScope.launch {
            _uiState.update { it.copy(loadingClaims = true, error = null) }
            repository.listClaims(spaceId)
                .onSuccess { claims ->
                    _uiState.update {
                        it.copy(loadingClaims = false, claims = claims)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(loadingClaims = false, error = error.message)
                    }
                }
        }
    }

    fun verifyClaim(claimId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(verifyingClaimId = claimId, error = null) }
            repository.verifyClaim(VerifyClaimRequest(claimId))
                .onSuccess {
                    _uiState.update { state -> state.copy(verifyingClaimId = null) }
                    refreshClaims()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(verifyingClaimId = null, error = error.message)
                    }
                }
        }
    }

    fun refreshProposals() {
        viewModelScope.launch {
            _uiState.update { it.copy(loadingProposals = true, error = null) }
            repository.listProposals(spaceId)
                .onSuccess { proposals ->
                    _uiState.update { it.copy(loadingProposals = false, proposals = proposals) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(loadingProposals = false, error = error.message) }
                }
        }
    }

    fun approveProposal(proposalId: String) {
        reviewProposal(proposalId, approve = true)
    }

    fun rejectProposal(proposalId: String, reviewReason: String) {
        reviewProposal(proposalId, approve = false, reviewReason = reviewReason)
    }

    fun setProposalCommentDraft(proposalId: String, value: String) {
        _uiState.update {
            it.copy(
                proposalCommentDrafts = it.proposalCommentDrafts +
                    (proposalId to value.take(1000))
            )
        }
    }

    fun refreshProposalComments(proposalId: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    loadingProposalComments = it.loadingProposalComments + proposalId,
                    error = null
                )
            }
            repository.listProposalComments(spaceId, proposalId)
                .onSuccess { comments ->
                    _uiState.update {
                        it.copy(
                            loadingProposalComments =
                                it.loadingProposalComments - proposalId,
                            proposalComments =
                                it.proposalComments + (proposalId to comments)
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            loadingProposalComments =
                                it.loadingProposalComments - proposalId,
                            error = error.message
                        )
                    }
                }
        }
    }

    fun addProposalComment(proposalId: String) {
        val body = _uiState.value.proposalCommentDrafts[proposalId].orEmpty().trim()
        if (!isProposalCommentValid(body)) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(postingProposalCommentId = proposalId, error = null)
            }
            repository.createProposalComment(spaceId, proposalId, body)
                .onSuccess { comment ->
                    _uiState.update {
                        val current = it.proposalComments[proposalId].orEmpty()
                        it.copy(
                            postingProposalCommentId = null,
                            proposalComments =
                                it.proposalComments + (proposalId to (current + comment)),
                            proposalCommentDrafts =
                                it.proposalCommentDrafts + (proposalId to "")
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            postingProposalCommentId = null,
                            error = error.message
                        )
                    }
                }
        }
    }

    private fun reviewProposal(
        proposalId: String,
        approve: Boolean,
        reviewReason: String? = null
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(reviewingProposalId = proposalId, error = null) }
            val request = ReviewProposalRequest(
                spaceId = spaceId,
                proposalId = proposalId,
                reviewReason = reviewReason?.trim()?.takeIf(String::isNotEmpty)
            )
            val result = if (approve) repository.approveProposal(request) else repository.rejectProposal(request)
            result.onSuccess {
                _uiState.update { it.copy(reviewingProposalId = null) }
                refreshProposals()
            }.onFailure { error ->
                _uiState.update { it.copy(reviewingProposalId = null, error = error.message) }
            }
        }
    }

    fun refreshDuplicates() {
        viewModelScope.launch {
            _uiState.update { it.copy(loadingDuplicates = true, error = null) }
            repository.listDuplicates(spaceId)
                .onSuccess { duplicates ->
                    _uiState.update { it.copy(loadingDuplicates = false, duplicates = duplicates) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(loadingDuplicates = false, error = error.message) }
                }
        }
    }

    fun mergeDuplicate(sourcePersonId: String, targetPersonId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(merging = true, error = null) }
            repository.mergePersons(MergePersonsRequest(spaceId, sourcePersonId, targetPersonId))
                .onSuccess {
                    _uiState.update { it.copy(merging = false) }
                    refreshDuplicates()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(merging = false, error = error.message) }
                }
        }
    }

    fun prepareGedcomExport() {
        viewModelScope.launch {
            _uiState.update { it.copy(transferringData = true, error = null, transferMessage = null) }
            repository.exportGedcom(spaceId)
                .onSuccess { document ->
                    _uiState.update {
                        it.copy(
                            transferringData = false,
                            pendingDocument = PortableDocument(
                                document.fileName,
                                document.mimeType,
                                document.content,
                                "GEDCOM"
                            )
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(transferringData = false, error = error.message) }
                }
        }
    }

    fun prepareBackupExport() {
        viewModelScope.launch {
            _uiState.update { it.copy(transferringData = true, error = null, transferMessage = null) }
            repository.createBackup(spaceId)
                .onSuccess { content ->
                    _uiState.update {
                        it.copy(
                            transferringData = false,
                            pendingDocument = PortableDocument(
                                "familyroot-backup.json",
                                "application/json",
                                content,
                                "BACKUP"
                            )
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(transferringData = false, error = error.message) }
                }
        }
    }

    fun documentHandled(saved: Boolean) {
        _uiState.update {
            it.copy(
                pendingDocument = null,
                transferMessage = if (saved) "File saved" else null
            )
        }
    }

    fun importGedcom(content: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(transferringData = true, error = null, transferMessage = null) }
            repository.importGedcom(spaceId, content)
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            transferringData = false,
                            transferMessage = "Imported ${result.personCount} people and ${result.relationshipCount} relationships"
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(transferringData = false, error = error.message) }
                }
        }
    }

    fun restoreBackup(content: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(transferringData = true, error = null, transferMessage = null) }
            repository.restoreBackup(spaceId, content)
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            transferringData = false,
                            transferMessage = "Restored ${result.personCount} people and ${result.relationshipCount} relationships"
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(transferringData = false, error = error.message) }
                }
        }
    }

    fun requestClearOfflineData() {
        _uiState.update {
            it.copy(showClearOfflineConfirmation = true, privacyMessage = null, error = null)
        }
    }

    fun cancelClearOfflineData() {
        _uiState.update { it.copy(showClearOfflineConfirmation = false) }
    }

    fun clearOfflineData() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    showClearOfflineConfirmation = false,
                    clearingOfflineData = true,
                    privacyMessage = null,
                    error = null
                )
            }
            repository.clearOfflineSpaceData(spaceId)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            clearingOfflineData = false,
                            privacyMessage = "Offline family data removed from this device"
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(clearingOfflineData = false, error = error.message)
                    }
                }
        }
    }

    class Factory(
        private val spaceId: String,
        private val repository: PersonRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SpaceSettingsViewModel(spaceId, repository) as T
        }
    }
}

internal fun manageableRoles(actorRole: String?, member: SpaceMember): List<String> = when {
    member.isCurrentUser || member.role == "OWNER" -> emptyList()
    actorRole == "OWNER" -> listOf("ADMIN", "EDITOR", "VIEWER")
    actorRole == "ADMIN" && member.role in setOf("EDITOR", "VIEWER") ->
        listOf("EDITOR", "VIEWER")
    else -> emptyList()
}
