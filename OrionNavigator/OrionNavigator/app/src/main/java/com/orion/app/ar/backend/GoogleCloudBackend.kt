package com.orion.app.ar.backend

import android.util.Log
import com.google.ar.core.Anchor
import com.google.ar.core.Session
import com.orion.app.ar.core.ArCoreTrackingToken
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * ARCore Persistent Cloud Anchors + Google Cloud API.
 * This class handles Google Cloud Anchor host & resolve operations.
 */
class GoogleCloudBackend : LocalizationBackend {
    companion object {
        private const val TAG = "GoogleCloudBackend"
        const val TTL_DAYS = 1
    }

    @Volatile
    private var session: Session? = null

    override val capabilities =
        BackendCapabilities(
            displayName = "Google Cloud Anchors",
            usesArCoreCloudAnchors = true,
            supportsMultiUserShare = true,
        )

    fun bindSession(session: Session?) {
        this.session = session
    }

    override fun configureArSession(hooks: ArSessionHooks) {
        hooks.setCloudAnchorModeEnabled(true)
    }

    override suspend fun hostAnchor(request: HostAnchorRequest): HostAnchorResult {
        val token =
            request.localTrackingToken as? ArCoreTrackingToken
                ?: return HostAnchorResult(
                    success = false,
                    errorMessage = "GoogleCloudBackend requires an ArCoreTrackingToken.",
                )
        return hostWithArCore(token.session, token.anchor)
    }

    override suspend fun resolveAnchor(request: ResolveAnchorRequest): ResolveAnchorResult {
        val session =
            session
                ?: return ResolveAnchorResult(
                    success = false,
                    backendAnchorId = request.backendAnchorId,
                    waypointId = request.waypointId,
                    errorMessage = "AR session not bound to GoogleCloudBackend.",
                )
        return resolveWithSession(session, request)
    }

    override suspend fun deleteAnchor(backendAnchorId: String): DeleteAnchorResult {
        Log.i(TAG, "deleteAnchor($backendAnchorId): local-only.")
        return DeleteAnchorResult(success = true)
    }

    override suspend fun saveMap(mapId: String): SaveMapResult =
        SaveMapResult(success = true, mapId = mapId)

    override suspend fun loadMap(mapId: String): LoadMapResult =
        LoadMapResult(success = true, mapId = mapId)

    private suspend fun resolveWithSession(
        session: Session,
        request: ResolveAnchorRequest,
    ): ResolveAnchorResult =
        suspendCancellableCoroutine { cont ->
            try {
                val future =
                    session.resolveCloudAnchorAsync(request.backendAnchorId) { anchor, state ->
                        if (!cont.isActive) return@resolveCloudAnchorAsync
                        when (state) {
                            Anchor.CloudAnchorState.SUCCESS -> {
                                val pose = anchor.pose
                                cont.resume(
                                    ResolveAnchorResult(
                                        success = true,
                                        backendAnchorId = request.backendAnchorId,
                                        waypointId = request.waypointId,
                                        localTrackingToken = ArCoreTrackingToken(session, anchor),
                                        pose =
                                            Pose6Dof(
                                                pose.tx(),
                                                pose.ty(),
                                                pose.tz(),
                                                pose.rotationQuaternion[0],
                                                pose.rotationQuaternion[1],
                                                pose.rotationQuaternion[2],
                                                pose.rotationQuaternion[3],
                                            ),
                                        retryable = false,
                                    ),
                                )
                            }
                            else ->
                                cont.resume(
                                    ResolveAnchorResult(
                                        success = false,
                                        backendAnchorId = request.backendAnchorId,
                                        waypointId = request.waypointId,
                                        errorMessage = humanResolveError(state),
                                        retryable = isRetryableResolveFailure(state),
                                    ),
                                )
                        }
                    }
                cont.invokeOnCancellation {
                    try {
                        future.cancel()
                    } catch (_: Exception) {
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "resolveCloudAnchorAsync threw", e)
                cont.resume(
                    ResolveAnchorResult(
                        success = false,
                        backendAnchorId = request.backendAnchorId,
                        waypointId = request.waypointId,
                        errorMessage = e.message ?: "Resolve failed.",
                        retryable = true,
                    ),
                )
            }
        }

    private suspend fun hostWithArCore(session: Session, localAnchor: Anchor): HostAnchorResult =
        suspendCancellableCoroutine { cont ->
            try {
                val future =
                    session.hostCloudAnchorAsync(localAnchor, TTL_DAYS) { cloudAnchorId, state ->
                        if (!cont.isActive) return@hostCloudAnchorAsync
                        when (state) {
                            Anchor.CloudAnchorState.SUCCESS -> {
                                if (cloudAnchorId.isNullOrBlank()) {
                                    cont.resume(
                                        HostAnchorResult(success = false, errorMessage = "Host OK but empty id."),
                                    )
                                } else {
                                    Log.i(TAG, "Hosted cloud anchor id=$cloudAnchorId")
                                    cont.resume(HostAnchorResult(success = true, backendAnchorId = cloudAnchorId))
                                }
                            }
                            else ->
                                cont.resume(
                                    HostAnchorResult(success = false, errorMessage = humanHostError(state)),
                                )
                        }
                    }
                cont.invokeOnCancellation {
                    try {
                        future.cancel()
                    } catch (_: Exception) {
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "hostCloudAnchorAsync threw", e)
                cont.resume(HostAnchorResult(success = false, errorMessage = e.message ?: "Host failed."))
            }
        }

    private fun humanHostError(state: Anchor.CloudAnchorState): String =
        when (state) {
            Anchor.CloudAnchorState.ERROR_NOT_AUTHORIZED ->
                "ARCore API not authorized. Check API key in local.properties."
            Anchor.CloudAnchorState.ERROR_HOSTING_SERVICE_UNAVAILABLE ->
                "No internet / ARCore Cloud unreachable."
            Anchor.CloudAnchorState.ERROR_HOSTING_DATASET_PROCESSING_FAILED ->
                "Not enough visual features — walk around the spot and try again."
            Anchor.CloudAnchorState.ERROR_RESOURCE_EXHAUSTED ->
                "Cloud Anchor quota exceeded."
            else -> "Cloud host failed ($state)."
        }

    private fun humanResolveError(state: Anchor.CloudAnchorState): String =
        when (state) {
            Anchor.CloudAnchorState.ERROR_CLOUD_ID_NOT_FOUND ->
                "Cloud marker expired or missing."
            Anchor.CloudAnchorState.ERROR_NOT_AUTHORIZED ->
                "ARCore API not authorized."
            Anchor.CloudAnchorState.ERROR_HOSTING_SERVICE_UNAVAILABLE ->
                "No internet while resolving."
            Anchor.CloudAnchorState.ERROR_RESOURCE_EXHAUSTED ->
                "Cloud Anchor quota exceeded — backing off."
            else -> "Could not find marker in room ($state)."
        }

    private fun isRetryableResolveFailure(state: Anchor.CloudAnchorState): Boolean =
        when (state) {
            Anchor.CloudAnchorState.ERROR_CLOUD_ID_NOT_FOUND,
            Anchor.CloudAnchorState.ERROR_NOT_AUTHORIZED,
            -> false
            else -> true
        }
}
