package com.example.familytreeplatform.models

data class SpaceMember(
    val memberId: String,
    val userId: String,
    val displayName: String,
    val role: String,
    val joinedAt: String,
    val isCurrentUser: Boolean = false
)

data class UpdateMemberRoleRequest(val role: String)

data class TransferOwnershipRequest(val targetMemberId: String)

data class MembershipResult(
    val memberId: String,
    val userId: String,
    val role: String,
    val joinedAt: String
)

data class OwnershipTransferResponse(
    val spaceId: String,
    val previousOwner: MembershipResult,
    val owner: MembershipResult
)

data class RemoveMemberResponse(
    val spaceId: String,
    val memberId: String,
    val removed: Boolean
)

data class LeaveSpaceResponse(
    val spaceId: String,
    val left: Boolean
)
