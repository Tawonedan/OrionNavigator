package com.orion.app.ar.backend

/**
 * Opaque token for a placement that exists only in the current AR session.
 */
interface LocalTrackingToken

/**
 * Hooks the localization backend can use while the AR session is configured.
 */
interface ArSessionHooks {
    fun setCloudAnchorModeEnabled(enabled: Boolean)
}

data class Pose6Dof(
    val tx: Float,
    val ty: Float,
    val tz: Float,
    val qx: Float,
    val qy: Float,
    val qz: Float,
    val qw: Float,
)

data class HostAnchorRequest(
    val provisionalId: String,
    val localTrackingToken: LocalTrackingToken,
    val displayName: String = "",
)

data class HostAnchorResult(
    val success: Boolean,
    val backendAnchorId: String? = null,
    val errorMessage: String? = null,
)

data class ResolveAnchorRequest(
    val backendAnchorId: String,
    val waypointId: String,
)

data class ResolveAnchorResult(
    val success: Boolean,
    val backendAnchorId: String,
    val waypointId: String,
    val localTrackingToken: LocalTrackingToken? = null,
    val pose: Pose6Dof? = null,
    val errorMessage: String? = null,
    val retryable: Boolean = true,
)

data class DeleteAnchorResult(
    val success: Boolean,
    val errorMessage: String? = null,
)

data class SaveMapResult(
    val success: Boolean,
    val mapId: String? = null,
    val errorMessage: String? = null,
)

data class LoadMapResult(
    val success: Boolean,
    val mapId: String? = null,
    val errorMessage: String? = null,
)

data class BackendCapabilities(
    val displayName: String,
    val usesArCoreCloudAnchors: Boolean = false,
    val supportsMultiUserShare: Boolean = false,
)
