// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.auth

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class OrcaAuthViewModel(application: Application) : AndroidViewModel(application) {
    private val client = OrcaCloudAuthClient()
    private val tokenStore = EncryptedRefreshTokenStore(application)
    private val profileCache = OrcaProfileCache(application)
    private val mutableState = MutableStateFlow<OrcaAuthState>(OrcaAuthState.Loading)
    val state: StateFlow<OrcaAuthState> = mutableState.asStateFlow()
    private val mutableProfileState = MutableStateFlow(OrcaProfileSyncState())
    val profileState: StateFlow<OrcaProfileSyncState> = mutableProfileState.asStateFlow()

    private var session: OrcaSession? = null
    private var loginJob: Job? = null
    private var restoreJob: Job? = null
    @Volatile private var callbackServer: OrcaLoopbackServer? = null

    init {
        restoreSession()
    }

    fun signIn(provider: OrcaAuthProvider) {
        restoreJob?.cancel()
        cancelSignIn(updateState = false)
        loginJob = viewModelScope.launch {
            mutableState.value = OrcaAuthState.WaitingForBrowser(provider)
            runCatching {
                withContext(Dispatchers.IO) {
                    OrcaLoopbackServer.open().use { server ->
                        callbackServer = server
                        val pkce = client.createPkce()
                        val uri = client.authorizationUri(provider, server.redirectUri, pkce)
                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                            addCategory(Intent.CATEGORY_BROWSABLE)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            getApplication<Application>().startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            error("No browser is available for OrcaCloud sign-in")
                        }

                        val callback = server.awaitCallback()
                        require(constantTimeEquals(callback.state, pkce.state)) {
                            "OrcaCloud security check failed"
                        }
                        client.exchangeCode(callback.code, pkce.verifier)
                    }
                }
            }.onSuccess(::acceptSession)
                .onFailure { error ->
                    if (loginJob?.isCancelled != true) {
                        mutableState.value = OrcaAuthState.Error(
                            error.message ?: "Cannot sign in to OrcaCloud",
                        )
                    }
                }
            callbackServer = null
        }
    }

    fun cancelSignIn() {
        cancelSignIn(updateState = true)
    }

    fun retryRestore() {
        restoreSession()
    }

    fun syncProfiles() {
        if (isReviewerDemoActive()) {
            mutableProfileState.value = ReviewerDemoAccess.syncState()
            return
        }
        viewModelScope.launch { syncProfilesInternal() }
    }

    fun enterReviewerDemo(username: String, password: String) {
        restoreJob?.cancel()
        cancelSignIn(updateState = false)
        if (!ReviewerDemoAccess.credentialsMatch(username, password)) {
            mutableState.value = OrcaAuthState.Error(
                "Неверные данные локального demo Google Play.",
            )
            return
        }

        // A demo login never owns an OrcaCloud session and must not inherit a live access token.
        session = null
        mutableProfileState.value = ReviewerDemoAccess.syncState()
        mutableState.value = OrcaAuthState.SignedIn(
            account = ReviewerDemoAccess.account,
            mode = OrcaAuthMode.REVIEW_DEMO,
        )
    }

    fun signOut() {
        restoreJob?.cancel()
        cancelSignIn(updateState = false)
        if (isReviewerDemoActive()) {
            session = null
            mutableProfileState.value = OrcaProfileSyncState()
            mutableState.value = OrcaAuthState.SignedOut
            return
        }
        val current = session
        session = null
        tokenStore.clear()
        profileCache.clear()
        mutableProfileState.value = OrcaProfileSyncState()
        mutableState.value = OrcaAuthState.SignedOut
        if (current != null) {
            viewModelScope.launch(Dispatchers.IO) { runCatching { client.logout(current) } }
        }
    }

    private fun restoreSession() {
        restoreJob?.cancel()
        restoreJob = viewModelScope.launch {
            mutableState.value = OrcaAuthState.Loading
            val cached = withContext(Dispatchers.IO) { profileCache.read() }
            if (cached != null) {
                mutableProfileState.value = OrcaProfileSyncState(
                    profiles = cached.profiles,
                    isCached = true,
                    lastSyncedAt = cached.syncedAt,
                    origin = OrcaProfileOrigin.CACHE,
                )
            }
            val refreshToken = withContext(Dispatchers.IO) { tokenStore.read() }
            if (refreshToken.isNullOrBlank()) {
                mutableState.value = OrcaAuthState.SignedOut
                return@launch
            }

            runCatching { withContext(Dispatchers.IO) { client.refresh(refreshToken) } }
                .onSuccess(::acceptSession)
                .onFailure { error ->
                    if (error is OrcaAuthHttpException && error.status in 400..499) {
                        tokenStore.clear()
                        profileCache.clear()
                        mutableProfileState.value = OrcaProfileSyncState()
                        mutableState.value = OrcaAuthState.SignedOut
                    } else {
                        mutableState.value = OrcaAuthState.Error(
                            "Saved OrcaCloud session could not be refreshed. Check the connection.",
                        )
                    }
                }
        }
    }

    private fun acceptSession(newSession: OrcaSession, autoSync: Boolean = true): Boolean {
        var accepted = false
        runCatching { tokenStore.write(newSession.refreshToken) }
            .onSuccess {
                session = newSession
                mutableState.value = OrcaAuthState.SignedIn(
                    account = newSession.account,
                    mode = OrcaAuthMode.CLOUD,
                )
                accepted = true
                val cached = profileCache.read()?.takeIf { it.userId == newSession.account.id }
                if (cached != null) {
                    mutableProfileState.value = OrcaProfileSyncState(
                        profiles = cached.profiles,
                        isCached = true,
                        lastSyncedAt = cached.syncedAt,
                        origin = OrcaProfileOrigin.CACHE,
                    )
                } else {
                    mutableProfileState.value = OrcaProfileSyncState()
                }
                if (autoSync) syncProfiles()
            }
            .onFailure {
                session = null
                tokenStore.clear()
                mutableState.value = OrcaAuthState.Error(
                    "The OrcaCloud session could not be stored securely on this device.",
                )
            }
        return accepted
    }

    private suspend fun syncProfilesInternal() {
        var current = session
        if (current == null) {
            mutableProfileState.value = mutableProfileState.value.copy(
                isLoading = false,
                error = "Sign in to OrcaCloud before downloading profiles.",
            )
            return
        }

        mutableProfileState.value = mutableProfileState.value.copy(isLoading = true, error = null)
        var result = runCatching {
            withContext(Dispatchers.IO) { client.pullProfiles(current.accessToken) }
        }

        val firstError = result.exceptionOrNull()
        if (firstError is OrcaAuthHttpException && firstError.status == 401) {
            val refreshed = runCatching {
                withContext(Dispatchers.IO) { client.refresh(current.refreshToken) }
            }.getOrElse { error ->
                mutableProfileState.value = mutableProfileState.value.copy(
                    isLoading = false,
                    error = error.message ?: "Cannot refresh the OrcaCloud session.",
                )
                return
            }
            if (!acceptSession(refreshed, autoSync = false)) return
            current = refreshed
            result = runCatching {
                withContext(Dispatchers.IO) { client.pullProfiles(refreshed.accessToken) }
            }
        }

        result.onSuccess { profiles ->
            val syncedAt = System.currentTimeMillis()
            runCatching {
                withContext(Dispatchers.IO) {
                    profileCache.write(current.account.id, profiles, syncedAt)
                }
            }
            mutableProfileState.value = OrcaProfileSyncState(
                profiles = profiles,
                isLoading = false,
                isCached = false,
                lastSyncedAt = syncedAt,
                origin = OrcaProfileOrigin.CLOUD,
            )
        }.onFailure { error ->
            mutableProfileState.value = mutableProfileState.value.copy(
                isLoading = false,
                isCached = mutableProfileState.value.profiles.isNotEmpty(),
                error = error.message ?: "Cannot download OrcaCloud profiles.",
            )
        }
    }

    private fun cancelSignIn(updateState: Boolean) {
        callbackServer?.close()
        callbackServer = null
        loginJob?.cancel()
        loginJob = null
        if (updateState) mutableState.value = OrcaAuthState.SignedOut
    }

    override fun onCleared() {
        callbackServer?.close()
        restoreJob?.cancel()
        super.onCleared()
    }

    private fun isReviewerDemoActive(): Boolean =
        (mutableState.value as? OrcaAuthState.SignedIn)?.mode == OrcaAuthMode.REVIEW_DEMO

    private fun constantTimeEquals(left: String, right: String): Boolean = MessageDigest.isEqual(
        left.toByteArray(Charsets.UTF_8),
        right.toByteArray(Charsets.UTF_8),
    )
}
