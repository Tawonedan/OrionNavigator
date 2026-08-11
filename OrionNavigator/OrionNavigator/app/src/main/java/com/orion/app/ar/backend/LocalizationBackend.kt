package com.orion.app.ar.backend

/**
 * Replaceable localization backend.
 */
interface LocalizationBackend {
    val capabilities: BackendCapabilities

    /** Apply backend-specific AR session flags via [hooks]. */
    fun configureArSession(hooks: ArSessionHooks)

    suspend fun hostAnchor(request: HostAnchorRequest): HostAnchorResult

    suspend fun resolveAnchor(request: ResolveAnchorRequest): ResolveAnchorResult

    suspend fun deleteAnchor(backendAnchorId: String): DeleteAnchorResult

    suspend fun saveMap(mapId: String): SaveMapResult

    suspend fun loadMap(mapId: String): LoadMapResult
}
