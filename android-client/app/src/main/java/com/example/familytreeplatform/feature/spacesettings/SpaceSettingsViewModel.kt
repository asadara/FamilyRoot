package com.example.familytreeplatform.feature.spacesettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.familytreeplatform.models.ClaimReviewItem
import com.example.familytreeplatform.models.CreatedInvitation
import com.example.familytreeplatform.models.DuplicateGroup
import com.example.familytreeplatform.models.MergePersonsRequest
import com.example.familytreeplatform.models.ProposalItem
import com.example.familytreeplatform.models.ReviewProposalRequest
import com.example.familytreeplatform.models.VerifyClaimRequest
import com.example.familytreeplatform.models.PortableDocument
import com.example.familytreeplatform.repository.PersonRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SpaceSettingsUiState(
    val role: String = "VIEWER",
    val expiresInDays: String = "7",
    val creating: Boolean = false,
    val loadingClaims: Boolean = false,
    val loadingProposals: Boolean = false,
    val loadingDuplicates: Boolean = false,
    val verifyingClaimId: String? = null,
    val reviewingProposalId: String? = null,
    val merging: Boolean = false,
    val invitation: CreatedInvitation? = null,
    val claims: List<ClaimReviewItem> = emptyList(),
    val proposals: List<ProposalItem> = emptyList(),
    val duplicates: List<DuplicateGroup> = emptyList(),
    val transferringData: Boolean = false,
    val pendingDocument: PortableDocument? = null,
    val transferMessage: String? = null,
    val clearingOfflineData: Boolean = false,
    val showClearOfflineConfirmation: Boolean = false,
    val privacyMessage: String? = null,
    val error: String? = null
)

class SpaceSettingsViewModel(
    private val spaceId: String,
    private val repository: PersonRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SpaceSettingsUiState())
    val uiState: StateFlow<SpaceSettingsUiState> = _uiState.asStateFlow()

    init {
        refreshClaims()
        refreshProposals()
        refreshDuplicates()
    }

    fun setRole(value: String) {
        _uiState.update { it.copy(role = value, error = null) }
    }

    fun setExpiresInDays(value: String) {
        if (value.all { it.isDigit() }) {
            _uiState.update { it.copy(expiresInDays = value, error = null) }
        }
    }

    fun createInvitation() {
        val state = _uiState.value
        val days = state.expiresInDays.toIntOrNull()
        if (days == null || days !in 1..30) {
            _uiState.update { it.copy(error = "Expiry must be between 1 and 30 days") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(creating = true, error = null, invitation = null) }
            repository.createInvitation(spaceId, state.role, days)
                .onSuccess { invitation ->
                    _uiState.update { it.copy(creating = false, invitation = invitation) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(creating = false, error = error.message) }
                }
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

    fun rejectProposal(proposalId: String) {
        reviewProposal(proposalId, approve = false)
    }

    private fun reviewProposal(proposalId: String, approve: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(reviewingProposalId = proposalId, error = null) }
            val request = ReviewProposalRequest(spaceId, proposalId)
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
