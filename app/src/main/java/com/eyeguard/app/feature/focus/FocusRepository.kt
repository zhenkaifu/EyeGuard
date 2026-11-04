package com.eyeguard.app.feature.focus

import android.content.Context
import android.os.SystemClock
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eyeguard.app.feature.focus.data.FocusSessionDao
import com.eyeguard.app.feature.focus.data.FocusSessionEntity
import com.eyeguard.app.feature.focus.data.FocusTaskDao
import com.eyeguard.app.feature.focus.data.FocusTaskEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.focusDataStore: DataStore<Preferences> by preferencesDataStore(name = "focus_cultivation")

object FocusPhase {
    const val IDLE = "idle"
    const val ACTIVE = "active"
    const val RESTING = "resting"
    const val REST_READY = "rest_ready"
    const val PLAN_PAUSED = "plan_paused"
    const val PRESENCE_PAUSED = "presence_paused"
    const val COMPLETE = "complete"
}

data class FocusCompletion(
    val minutes: Int,
    val xpEarned: Int,
    val previousRealm: Int,
    val newRealm: Int,
    val previousLayer: Int,
    val newLayer: Int,
    val dailyGoalCompleted: Boolean
)

data class FocusSnapshot(
    val xp: Int = 0,
    val clarityPoints: Int = 0,
    val totalMinutes: Int = 0,
    val completedSessions: Int = 0,
    val streakDays: Int = 0,
    val todayMinutes: Int = 0,
    val strictModePreferred: Boolean = false,
    val presencePreferred: Boolean = true,
    val reduceMotion: Boolean = false,
    val musicEnabled: Boolean = true,
    val allowedPackages: Set<String> = emptySet(),
    val phase: String = FocusPhase.IDLE,
    val planDay: Long = 0L,
    val planTargetMinutes: Int = 0,
    val planSegmentMinutes: Int = 25,
    val planRestMinutes: Int = 5,
    val planGoal: String = "",
    val planSegments: List<Int> = emptyList(),
    val currentSegmentIndex: Int = 0,
    val planCompletedMs: Long = 0L,
    val activeStartedAt: Long = 0L,
    val activeEndAt: Long = 0L,
    val activeElapsedStartAt: Long = 0L,
    val activeElapsedEndAt: Long = 0L,
    val activeDurationMinutes: Int = 0,
    val activeRemainingMs: Long = 0L,
    val activeStrictMode: Boolean = false,
    val activePresenceEnabled: Boolean = false,
    val activeRestCount: Int = 0,
    val activePausedMs: Long = 0L,
    val restEndAt: Long = 0L,
    val restElapsedEndAt: Long = 0L,
    val restRemainingMs: Long = 0L,
    val pausedFromPhase: String = "",
    val presencePausedAt: Long = 0L,
    val pendingCompletion: FocusCompletion? = null
) {
    val hasActivePlan: Boolean get() = planSegments.isNotEmpty() && phase != FocusPhase.IDLE
    val isActive: Boolean get() = phase == FocusPhase.ACTIVE
    val currentSegmentNumber: Int get() = currentSegmentIndex + 1
    val currentSegmentCount: Int get() = planSegments.size
}

/** 今日计划、有效计时、境界和历史的唯一持久化入口。 */
@Singleton
class FocusRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val focusSessionDao: FocusSessionDao,
    private val focusTaskDao: FocusTaskDao
) {
    private object Keys {
        val XP = intPreferencesKey("xp")
        val CLARITY_POINTS = intPreferencesKey("clarity_points")
        val TOTAL_MINUTES = intPreferencesKey("total_minutes")
        val COMPLETED_SESSIONS = intPreferencesKey("completed_sessions")
        val STREAK_DAYS = intPreferencesKey("streak_days")
        val LAST_FOCUS_DAY = longPreferencesKey("last_focus_day")
        val STATS_DAY = longPreferencesKey("stats_day")
        val TODAY_MINUTES = intPreferencesKey("today_minutes")
        val STRICT_PREFERRED = booleanPreferencesKey("strict_mode_preferred")
        val PRESENCE_PREFERRED = booleanPreferencesKey("presence_preferred")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val MUSIC_ENABLED = booleanPreferencesKey("music_enabled")
        val ALLOWED_PACKAGES = stringPreferencesKey("allowed_packages")
        val PROGRESSION_VERSION = intPreferencesKey("progression_version")
        val PHASE = stringPreferencesKey("phase")
        val PLAN_DAY = longPreferencesKey("plan_day")
        val PLAN_TARGET = intPreferencesKey("plan_target_minutes")
        val PLAN_SEGMENT = intPreferencesKey("plan_segment_minutes")
        val PLAN_REST = intPreferencesKey("plan_rest_minutes")
        val PLAN_GOAL = stringPreferencesKey("plan_goal")
        val PLAN_SEGMENTS = stringPreferencesKey("plan_segments")
        val SEGMENT_INDEX = intPreferencesKey("segment_index")
        val PLAN_COMPLETED_MS = longPreferencesKey("plan_completed_ms")
        val ACTIVE_STARTED_AT = longPreferencesKey("active_started_at")
        val ACTIVE_END_AT = longPreferencesKey("active_end_at")
        val ACTIVE_ELAPSED_STARTED_AT = longPreferencesKey("active_elapsed_started_at")
        val ACTIVE_ELAPSED_END_AT = longPreferencesKey("active_elapsed_end_at")
        val ACTIVE_DURATION = intPreferencesKey("active_duration_minutes")
        val ACTIVE_REMAINING = longPreferencesKey("active_remaining_ms")
        val ACTIVE_STRICT = booleanPreferencesKey("active_strict_mode")
        val ACTIVE_PRESENCE = booleanPreferencesKey("active_presence_enabled")
        val ACTIVE_REST_COUNT = intPreferencesKey("active_rest_count")
        val ACTIVE_PAUSED_MS = longPreferencesKey("active_paused_ms")
        val REST_END_AT = longPreferencesKey("rest_end_at")
        val REST_ELAPSED_END_AT = longPreferencesKey("rest_elapsed_end_at")
        val REST_REMAINING = longPreferencesKey("rest_remaining_ms")
        val PAUSED_FROM = stringPreferencesKey("paused_from_phase")
        val PRESENCE_PAUSED_AT = longPreferencesKey("presence_paused_at")
        val PENDING_MINUTES = intPreferencesKey("pending_minutes")
        val PENDING_XP = intPreferencesKey("pending_xp")
        val PENDING_PREVIOUS_REALM = intPreferencesKey("pending_previous_realm")
        val PENDING_NEW_REALM = intPreferencesKey("pending_new_realm")
        val PENDING_PREVIOUS_LAYER = intPreferencesKey("pending_previous_layer")
        val PENDING_NEW_LAYER = intPreferencesKey("pending_new_layer")
        val PENDING_DAILY_COMPLETE = booleanPreferencesKey("pending_daily_complete")
    }

    val focusState: Flow<FocusSnapshot> = context.focusDataStore.data.map { it.toSnapshot() }
    val recentSessions: Flow<List<FocusSessionEntity>> = focusSessionDao.recentSessions()
    val todayTasks: Flow<List<FocusTaskEntity>> = focusTaskDao.tasksForDay(LocalDate.now().toEpochDay())

    suspend fun migrateIfNeeded() {
        context.focusDataStore.edit { prefs ->
            if ((prefs[Keys.PROGRESSION_VERSION] ?: 0) < 2) {
                prefs[Keys.XP] = (prefs[Keys.XP] ?: 0) * 2
                prefs[Keys.PROGRESSION_VERSION] = 2
            }
            if (prefs[Keys.PHASE] == null) {
                val legacyActive = (prefs[Keys.ACTIVE_END_AT] ?: 0L) > 0L
                prefs[Keys.PHASE] = if (legacyActive) FocusPhase.ACTIVE else FocusPhase.IDLE
                if (legacyActive) {
                    val minutes = (prefs[Keys.ACTIVE_DURATION] ?: 25).coerceAtLeast(25)
                    prefs[Keys.PLAN_DAY] = LocalDate.now().toEpochDay()
                    prefs[Keys.PLAN_TARGET] = minutes
                    prefs[Keys.PLAN_SEGMENTS] = minutes.toString()
                    prefs[Keys.ACTIVE_PRESENCE] = false
                }
            }
        }
    }

    suspend fun setStrictModePreferred(enabled: Boolean) {
        context.focusDataStore.edit { it[Keys.STRICT_PREFERRED] = enabled }
    }

    suspend fun setPresencePreferred(enabled: Boolean) {
        context.focusDataStore.edit { it[Keys.PRESENCE_PREFERRED] = enabled }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        context.focusDataStore.edit { it[Keys.REDUCE_MOTION] = enabled }
    }

    suspend fun setMusicEnabled(enabled: Boolean) {
        context.focusDataStore.edit { it[Keys.MUSIC_ENABLED] = enabled }
    }

    suspend fun setAllowedPackages(packages: Set<String>) {
        context.focusDataStore.edit {
            it[Keys.ALLOWED_PACKAGES] = packages.sorted().joinToString("|")
        }
    }

    suspend fun addTask(title: String, rewardXp: Int, now: Long = System.currentTimeMillis()): Boolean {
        val cleanTitle = title.trim().take(60)
        if (cleanTitle.isEmpty()) return false
        focusTaskDao.insert(FocusTaskEntity(
            planDay = LocalDate.now().toEpochDay(),
            title = cleanTitle,
            rewardXp = rewardXp.coerceIn(1, 5),
            createdAt = now
        ))
        return true
    }

    suspend fun changeTaskStatus(id: Long, newStatus: String, now: Long = System.currentTimeMillis()): FocusTaskEntity? {
        val task = focusTaskDao.findById(id) ?: return null
        val delta = FocusTaskLogic.rewardDelta(task.status, newStatus, task.rewardXp)
        if (!FocusTaskLogic.canTransition(task.status, newStatus)) return null
        val changed = focusTaskDao.updateStatus(
            id, task.status, newStatus,
            if (newStatus == FocusTaskEntity.COMPLETED) now else 0L
        )
        if (changed != 1) return null
        context.focusDataStore.edit { prefs ->
            prefs[Keys.XP] = ((prefs[Keys.XP] ?: 0) + delta).coerceAtLeast(0)
        }
        return task.copy(status = newStatus, completedAt = if (newStatus == FocusTaskEntity.COMPLETED) now else 0L)
    }

    suspend fun startPlan(
        targetMinutes: Int,
        segmentMinutes: Int,
        restMinutes: Int,
        goal: String,
        strictMode: Boolean,
        presenceEnabled: Boolean,
        now: Long = System.currentTimeMillis(),
        elapsedNow: Long = SystemClock.elapsedRealtime()
    ): Boolean {
        val segments = FocusPlanLogic.segmentsFor(targetMinutes, segmentMinutes)
        if (segments.isEmpty() || (strictMode && !presenceEnabled)) return false
        var started = false
        context.focusDataStore.edit { prefs ->
            val old = prefs.toSnapshot()
            if (!old.hasActivePlan || old.phase == FocusPhase.COMPLETE) {
                prefs[Keys.PLAN_DAY] = LocalDate.now().toEpochDay()
                prefs[Keys.PLAN_TARGET] = targetMinutes
                prefs[Keys.PLAN_SEGMENT] = segmentMinutes.coerceAtLeast(25)
                prefs[Keys.PLAN_REST] = restMinutes.coerceIn(1, 60)
                prefs[Keys.PLAN_GOAL] = goal.trim()
                prefs[Keys.PLAN_SEGMENTS] = segments.encode()
                prefs[Keys.SEGMENT_INDEX] = 0
                prefs[Keys.PLAN_COMPLETED_MS] = 0L
                prefs[Keys.STRICT_PREFERRED] = strictMode
                prefs[Keys.PRESENCE_PREFERRED] = presenceEnabled
                prefs.clearCompletion()
                prefs.clearRest()
                started = prefs.startCurrentSegment(strictMode, presenceEnabled, now, elapsedNow)
            }
        }
        return started
    }

    suspend fun startNextSegment(
        now: Long = System.currentTimeMillis(),
        elapsedNow: Long = SystemClock.elapsedRealtime()
    ): Boolean {
        var started = false
        context.focusDataStore.edit { prefs ->
            val snapshot = prefs.toSnapshot()
            if (snapshot.phase == FocusPhase.REST_READY && snapshot.currentSegmentIndex in snapshot.planSegments.indices) {
                started = prefs.startCurrentSegment(
                    snapshot.activeStrictMode,
                    snapshot.activePresenceEnabled,
                    now,
                    elapsedNow
                )
            }
        }
        return started
    }

    suspend fun pauseForPresence(elapsedNow: Long = SystemClock.elapsedRealtime()): Boolean {
        var paused = false
        context.focusDataStore.edit { prefs ->
            val snapshot = prefs.toSnapshot()
            if (snapshot.phase == FocusPhase.ACTIVE && snapshot.activePresenceEnabled) {
                val remaining = FocusTiming.resolve(snapshot, elapsedNow = elapsedNow).remainingMs
                if (remaining > 0L) {
                    prefs[Keys.ACTIVE_REMAINING] = remaining
                    prefs[Keys.PHASE] = FocusPhase.PRESENCE_PAUSED
                    prefs[Keys.PRESENCE_PAUSED_AT] = elapsedNow
                    prefs[Keys.ACTIVE_END_AT] = 0L
                    prefs[Keys.ACTIVE_ELAPSED_END_AT] = 0L
                    paused = true
                }
            }
        }
        return paused
    }

    suspend fun resumeAfterPresence(
        now: Long = System.currentTimeMillis(),
        elapsedNow: Long = SystemClock.elapsedRealtime()
    ): Boolean {
        var resumed = false
        context.focusDataStore.edit { prefs ->
            val snapshot = prefs.toSnapshot()
            if (snapshot.phase == FocusPhase.PRESENCE_PAUSED && snapshot.activeRemainingMs > 0L) {
                val pausedMs = (elapsedNow - snapshot.presencePausedAt).coerceAtLeast(0L)
                prefs[Keys.ACTIVE_PAUSED_MS] = snapshot.activePausedMs + pausedMs
                resumed = prefs.resumeActive(now, elapsedNow)
            }
        }
        return resumed
    }

    suspend fun pausePlan(
        now: Long = System.currentTimeMillis(),
        elapsedNow: Long = SystemClock.elapsedRealtime()
    ): Boolean {
        var paused = false
        context.focusDataStore.edit { prefs ->
            val snapshot = prefs.toSnapshot()
            when (snapshot.phase) {
                FocusPhase.ACTIVE -> {
                    val remaining = FocusTiming.resolve(snapshot, now, elapsedNow).remainingMs
                    if (remaining <= 0L) return@edit
                    prefs[Keys.ACTIVE_REMAINING] = remaining
                    prefs[Keys.PAUSED_FROM] = FocusPhase.ACTIVE
                    prefs[Keys.PRESENCE_PAUSED_AT] = elapsedNow
                    prefs[Keys.PHASE] = FocusPhase.PLAN_PAUSED
                    prefs[Keys.ACTIVE_END_AT] = 0L
                    prefs[Keys.ACTIVE_ELAPSED_END_AT] = 0L
                    paused = true
                }
                FocusPhase.RESTING -> {
                    prefs[Keys.REST_REMAINING] = FocusTiming.restRemainingMs(snapshot, now, elapsedNow)
                    prefs[Keys.PAUSED_FROM] = FocusPhase.RESTING
                    prefs[Keys.PHASE] = FocusPhase.PLAN_PAUSED
                    prefs.clearRest()
                    paused = true
                }
            }
        }
        return paused
    }

    suspend fun resumePlan(
        now: Long = System.currentTimeMillis(),
        elapsedNow: Long = SystemClock.elapsedRealtime()
    ): Boolean {
        var resumed = false
        context.focusDataStore.edit { prefs ->
            val snapshot = prefs.toSnapshot()
            if (snapshot.phase == FocusPhase.PLAN_PAUSED) {
                when (snapshot.pausedFromPhase) {
                    FocusPhase.RESTING -> {
                        val remaining = snapshot.restRemainingMs.coerceAtLeast(1_000L)
                        prefs[Keys.REST_END_AT] = now + remaining
                        prefs[Keys.REST_ELAPSED_END_AT] = elapsedNow + remaining
                        prefs[Keys.PHASE] = FocusPhase.RESTING
                        prefs[Keys.PAUSED_FROM] = ""
                        resumed = true
                    }
                    else -> {
                        val pausedMs = (elapsedNow - snapshot.presencePausedAt).coerceAtLeast(0L)
                        prefs[Keys.ACTIVE_PAUSED_MS] = snapshot.activePausedMs + pausedMs
                        resumed = prefs.resumeActive(now, elapsedNow)
                    }
                }
            }
        }
        return resumed
    }

    suspend fun adjustCurrentSegment(
        newMinutes: Int,
        now: Long = System.currentTimeMillis(),
        elapsedNow: Long = SystemClock.elapsedRealtime()
    ): Boolean {
        var adjusted = false
        context.focusDataStore.edit { prefs ->
            val snapshot = prefs.toSnapshot()
            if (snapshot.phase != FocusPhase.ACTIVE) return@edit
            val timing = FocusTiming.resolve(snapshot, now, elapsedNow)
            val minimum = maxOf(25, ((timing.elapsedMs + 59_999L) / 60_000L).toInt())
            if (newMinutes < minimum) return@edit
            val segments = FocusPlanLogic.rescheduleCurrent(
                snapshot.planTargetMinutes,
                (snapshot.planCompletedMs / 60_000L).toInt(),
                snapshot.currentSegmentIndex,
                snapshot.planSegments,
                snapshot.planSegmentMinutes,
                newMinutes
            ) ?: return@edit
            val newRemaining = (newMinutes * 60_000L - timing.elapsedMs).coerceAtLeast(0L)
            prefs[Keys.PLAN_SEGMENTS] = segments.encode()
            prefs[Keys.ACTIVE_DURATION] = newMinutes
            prefs[Keys.ACTIVE_REMAINING] = newRemaining
            prefs[Keys.ACTIVE_END_AT] = now + newRemaining
            prefs[Keys.ACTIVE_ELAPSED_END_AT] = elapsedNow + newRemaining
            adjusted = true
        }
        return adjusted
    }

    suspend fun completeRest(
        now: Long = System.currentTimeMillis(),
        elapsedNow: Long = SystemClock.elapsedRealtime()
    ): Boolean {
        var completed = false
        context.focusDataStore.edit { prefs ->
            val snapshot = prefs.toSnapshot()
            if (snapshot.phase == FocusPhase.RESTING && FocusTiming.restRemainingMs(snapshot, now, elapsedNow) <= 0L) {
                prefs[Keys.CLARITY_POINTS] = snapshot.clarityPoints + 5
                prefs[Keys.PHASE] = FocusPhase.REST_READY
                prefs.clearRest()
                completed = true
            }
        }
        return completed
    }

    suspend fun skipRest() {
        context.focusDataStore.edit { prefs ->
            if (prefs.toSnapshot().phase == FocusPhase.RESTING) {
                prefs[Keys.PHASE] = FocusPhase.REST_READY
                prefs.clearRest()
            }
        }
    }

    suspend fun completeSession(
        now: Long = System.currentTimeMillis(),
        elapsedNow: Long = SystemClock.elapsedRealtime()
    ): FocusCompletion? {
        var result: FocusCompletion? = null
        var history: FocusSessionEntity? = null
        context.focusDataStore.edit { prefs ->
            val snapshot = prefs.toSnapshot()
            val timing = FocusTiming.resolve(snapshot, now, elapsedNow)
            if (snapshot.phase != FocusPhase.ACTIVE || snapshot.activeDurationMinutes <= 0 || timing.remainingMs > 0L) return@edit
            val minutes = snapshot.activeDurationMinutes
            val earned = FocusProgression.xpForMinutes(minutes, snapshot.activeStrictMode)
            val oldLevel = FocusProgression.levelFor(snapshot.xp)
            val newXp = snapshot.xp + earned
            val newLevel = FocusProgression.levelFor(newXp)
            val today = LocalDate.now().toEpochDay()
            val lastFocusDay = prefs[Keys.LAST_FOCUS_DAY] ?: 0L
            val newStreak = when (lastFocusDay) {
                today -> snapshot.streakDays.coerceAtLeast(1)
                today - 1 -> snapshot.streakDays + 1
                else -> 1
            }
            val oldToday = if (prefs[Keys.STATS_DAY] == today) snapshot.todayMinutes else 0
            val newCompletedMs = snapshot.planCompletedMs + minutes * 60_000L
            val dailyComplete = snapshot.currentSegmentIndex == snapshot.planSegments.lastIndex

            prefs[Keys.XP] = newXp
            prefs[Keys.TOTAL_MINUTES] = snapshot.totalMinutes + minutes
            prefs[Keys.COMPLETED_SESSIONS] = snapshot.completedSessions + 1
            prefs[Keys.STREAK_DAYS] = newStreak
            prefs[Keys.LAST_FOCUS_DAY] = today
            prefs[Keys.STATS_DAY] = today
            prefs[Keys.TODAY_MINUTES] = oldToday + minutes
            prefs[Keys.PLAN_COMPLETED_MS] = newCompletedMs
            result = FocusCompletion(minutes, earned, oldLevel.realmIndex, newLevel.realmIndex,
                oldLevel.layer, newLevel.layer, dailyComplete)
            prefs.writeCompletion(result!!)
            history = FocusSessionEntity(
                startedAt = snapshot.activeStartedAt,
                endedAt = now,
                plannedMinutes = minutes,
                actualMinutes = minutes,
                xpEarned = earned,
                status = FocusSessionEntity.COMPLETED,
                strictMode = snapshot.activeStrictMode,
                restCount = snapshot.activeRestCount,
                goal = snapshot.planGoal,
                pausedMs = snapshot.activePausedMs
            )
            prefs.clearActiveSession()
            if (dailyComplete) {
                prefs[Keys.PHASE] = FocusPhase.COMPLETE
                prefs.clearRest()
            } else {
                val restMs = snapshot.planRestMinutes.coerceIn(1, 60) * 60_000L
                prefs[Keys.SEGMENT_INDEX] = snapshot.currentSegmentIndex + 1
                prefs[Keys.PHASE] = FocusPhase.RESTING
                prefs[Keys.REST_END_AT] = now + restMs
                prefs[Keys.REST_ELAPSED_END_AT] = elapsedNow + restMs
            }
        }
        history?.let { focusSessionDao.insert(it) }
        return result
    }

    suspend fun abandonSession(
        status: String = FocusSessionEntity.ABANDONED,
        now: Long = System.currentTimeMillis(),
        elapsedNow: Long = SystemClock.elapsedRealtime()
    ): Boolean {
        var history: FocusSessionEntity? = null
        context.focusDataStore.edit { prefs ->
            val snapshot = prefs.toSnapshot()
            if (snapshot.activeDurationMinutes <= 0 || snapshot.phase !in listOf(
                    FocusPhase.ACTIVE, FocusPhase.PRESENCE_PAUSED, FocusPhase.PLAN_PAUSED)) return@edit
            val remaining = if (snapshot.phase == FocusPhase.ACTIVE) {
                FocusTiming.resolve(snapshot, now, elapsedNow).remainingMs
            } else snapshot.activeRemainingMs
            val actual = ((snapshot.activeDurationMinutes * 60_000L - remaining) / 60_000L)
                .toInt().coerceIn(0, snapshot.activeDurationMinutes)
            history = FocusSessionEntity(
                startedAt = snapshot.activeStartedAt,
                endedAt = now,
                plannedMinutes = snapshot.activeDurationMinutes,
                actualMinutes = actual,
                xpEarned = 0,
                status = status,
                strictMode = snapshot.activeStrictMode,
                restCount = snapshot.activeRestCount,
                goal = snapshot.planGoal,
                pausedMs = snapshot.activePausedMs
            )
            prefs.clearActiveSession()
            prefs[Keys.PHASE] = FocusPhase.REST_READY
            prefs.clearRest()
        }
        history?.let { focusSessionDao.insert(it) }
        return history != null
    }

    suspend fun shelvePresencePause(elapsedNow: Long = SystemClock.elapsedRealtime()): Boolean {
        val snapshot = focusState.first()
        return snapshot.phase == FocusPhase.PRESENCE_PAUSED &&
            elapsedNow - snapshot.presencePausedAt >= 30 * 60_000L &&
            abandonSession(FocusSessionEntity.SHELVED, elapsedNow = elapsedNow)
    }

    suspend fun endPlan() {
        context.focusDataStore.edit { prefs ->
            prefs.clearActiveSession()
            prefs.clearRest()
            prefs[Keys.PHASE] = FocusPhase.IDLE
            prefs[Keys.PLAN_SEGMENTS] = ""
            prefs[Keys.PLAN_COMPLETED_MS] = 0L
            prefs[Keys.SEGMENT_INDEX] = 0
            prefs[Keys.PLAN_GOAL] = ""
            prefs.clearCompletion()
        }
    }

    suspend fun archiveExpiredPlan() {
        val snapshot = focusState.first()
        if (!snapshot.hasActivePlan || snapshot.planDay >= LocalDate.now().toEpochDay()) return
        if (snapshot.activeDurationMinutes > 0 && snapshot.phase != FocusPhase.COMPLETE) {
            abandonSession(FocusSessionEntity.SHELVED)
        }
        endPlan()
    }

    suspend fun registerRestCompleted() {
        context.focusDataStore.edit { prefs ->
            prefs[Keys.CLARITY_POINTS] = (prefs[Keys.CLARITY_POINTS] ?: 0) + 5
            if (prefs[Keys.PHASE] == FocusPhase.ACTIVE) {
                prefs[Keys.ACTIVE_REST_COUNT] = (prefs[Keys.ACTIVE_REST_COUNT] ?: 0) + 1
            }
        }
    }

    suspend fun dismissCompletion() {
        context.focusDataStore.edit { it.clearCompletion() }
    }

    private fun MutablePreferences.startCurrentSegment(
        strictMode: Boolean,
        presenceEnabled: Boolean,
        now: Long,
        elapsedNow: Long
    ): Boolean {
        val snapshot = toSnapshot()
        val minutes = snapshot.planSegments.getOrNull(snapshot.currentSegmentIndex) ?: return false
        val durationMs = minutes * 60_000L
        this[Keys.PHASE] = FocusPhase.ACTIVE
        this[Keys.ACTIVE_STARTED_AT] = now
        this[Keys.ACTIVE_END_AT] = now + durationMs
        this[Keys.ACTIVE_ELAPSED_STARTED_AT] = elapsedNow
        this[Keys.ACTIVE_ELAPSED_END_AT] = elapsedNow + durationMs
        this[Keys.ACTIVE_DURATION] = minutes
        this[Keys.ACTIVE_REMAINING] = durationMs
        this[Keys.ACTIVE_STRICT] = strictMode
        this[Keys.ACTIVE_PRESENCE] = presenceEnabled
        this[Keys.ACTIVE_REST_COUNT] = 0
        this[Keys.ACTIVE_PAUSED_MS] = 0L
        this[Keys.PAUSED_FROM] = ""
        this[Keys.PRESENCE_PAUSED_AT] = 0L
        clearRest()
        return true
    }

    private fun MutablePreferences.resumeActive(now: Long, elapsedNow: Long): Boolean {
        val remaining = (this[Keys.ACTIVE_REMAINING] ?: 0L).coerceAtLeast(0L)
        if (remaining <= 0L) return false
        this[Keys.ACTIVE_END_AT] = now + remaining
        this[Keys.ACTIVE_ELAPSED_END_AT] = elapsedNow + remaining
        this[Keys.PHASE] = FocusPhase.ACTIVE
        this[Keys.PAUSED_FROM] = ""
        this[Keys.PRESENCE_PAUSED_AT] = 0L
        return true
    }

    private fun Preferences.toSnapshot(): FocusSnapshot {
        val today = LocalDate.now().toEpochDay()
        val pendingMinutes = this[Keys.PENDING_MINUTES] ?: 0
        return FocusSnapshot(
            xp = this[Keys.XP] ?: 0,
            clarityPoints = this[Keys.CLARITY_POINTS] ?: 0,
            totalMinutes = this[Keys.TOTAL_MINUTES] ?: 0,
            completedSessions = this[Keys.COMPLETED_SESSIONS] ?: 0,
            streakDays = this[Keys.STREAK_DAYS] ?: 0,
            todayMinutes = if (this[Keys.STATS_DAY] == today) this[Keys.TODAY_MINUTES] ?: 0 else 0,
            strictModePreferred = this[Keys.STRICT_PREFERRED] ?: false,
            presencePreferred = this[Keys.PRESENCE_PREFERRED] ?: true,
            reduceMotion = this[Keys.REDUCE_MOTION] ?: false,
            musicEnabled = this[Keys.MUSIC_ENABLED] ?: true,
            allowedPackages = (this[Keys.ALLOWED_PACKAGES] ?: "").split('|').filter { it.isNotBlank() }.toSet(),
            phase = this[Keys.PHASE] ?: if ((this[Keys.ACTIVE_END_AT] ?: 0L) > 0L) FocusPhase.ACTIVE else FocusPhase.IDLE,
            planDay = this[Keys.PLAN_DAY] ?: 0L,
            planTargetMinutes = this[Keys.PLAN_TARGET] ?: 0,
            planSegmentMinutes = this[Keys.PLAN_SEGMENT] ?: 25,
            planRestMinutes = this[Keys.PLAN_REST] ?: 5,
            planGoal = this[Keys.PLAN_GOAL] ?: "",
            planSegments = (this[Keys.PLAN_SEGMENTS] ?: "").decodeSegments(),
            currentSegmentIndex = this[Keys.SEGMENT_INDEX] ?: 0,
            planCompletedMs = this[Keys.PLAN_COMPLETED_MS] ?: 0L,
            activeStartedAt = this[Keys.ACTIVE_STARTED_AT] ?: 0L,
            activeEndAt = this[Keys.ACTIVE_END_AT] ?: 0L,
            activeElapsedStartAt = this[Keys.ACTIVE_ELAPSED_STARTED_AT] ?: 0L,
            activeElapsedEndAt = this[Keys.ACTIVE_ELAPSED_END_AT] ?: 0L,
            activeDurationMinutes = this[Keys.ACTIVE_DURATION] ?: 0,
            activeRemainingMs = this[Keys.ACTIVE_REMAINING] ?: 0L,
            activeStrictMode = this[Keys.ACTIVE_STRICT] ?: false,
            activePresenceEnabled = this[Keys.ACTIVE_PRESENCE] ?: false,
            activeRestCount = this[Keys.ACTIVE_REST_COUNT] ?: 0,
            activePausedMs = this[Keys.ACTIVE_PAUSED_MS] ?: 0L,
            restEndAt = this[Keys.REST_END_AT] ?: 0L,
            restElapsedEndAt = this[Keys.REST_ELAPSED_END_AT] ?: 0L,
            restRemainingMs = this[Keys.REST_REMAINING] ?: 0L,
            pausedFromPhase = this[Keys.PAUSED_FROM] ?: "",
            presencePausedAt = this[Keys.PRESENCE_PAUSED_AT] ?: 0L,
            pendingCompletion = if (pendingMinutes > 0) FocusCompletion(
                pendingMinutes,
                this[Keys.PENDING_XP] ?: 0,
                this[Keys.PENDING_PREVIOUS_REALM] ?: 0,
                this[Keys.PENDING_NEW_REALM] ?: 0,
                this[Keys.PENDING_PREVIOUS_LAYER] ?: 1,
                this[Keys.PENDING_NEW_LAYER] ?: 1,
                this[Keys.PENDING_DAILY_COMPLETE] ?: false
            ) else null
        )
    }

    private fun MutablePreferences.writeCompletion(result: FocusCompletion) {
        this[Keys.PENDING_MINUTES] = result.minutes
        this[Keys.PENDING_XP] = result.xpEarned
        this[Keys.PENDING_PREVIOUS_REALM] = result.previousRealm
        this[Keys.PENDING_NEW_REALM] = result.newRealm
        this[Keys.PENDING_PREVIOUS_LAYER] = result.previousLayer
        this[Keys.PENDING_NEW_LAYER] = result.newLayer
        this[Keys.PENDING_DAILY_COMPLETE] = result.dailyGoalCompleted
    }

    private fun MutablePreferences.clearCompletion() {
        remove(Keys.PENDING_MINUTES)
        remove(Keys.PENDING_XP)
        remove(Keys.PENDING_PREVIOUS_REALM)
        remove(Keys.PENDING_NEW_REALM)
        remove(Keys.PENDING_PREVIOUS_LAYER)
        remove(Keys.PENDING_NEW_LAYER)
        remove(Keys.PENDING_DAILY_COMPLETE)
    }

    private fun MutablePreferences.clearActiveSession() {
        remove(Keys.ACTIVE_STARTED_AT)
        remove(Keys.ACTIVE_END_AT)
        remove(Keys.ACTIVE_ELAPSED_STARTED_AT)
        remove(Keys.ACTIVE_ELAPSED_END_AT)
        remove(Keys.ACTIVE_DURATION)
        remove(Keys.ACTIVE_REMAINING)
        remove(Keys.ACTIVE_REST_COUNT)
        remove(Keys.ACTIVE_PAUSED_MS)
        remove(Keys.PAUSED_FROM)
        remove(Keys.PRESENCE_PAUSED_AT)
    }

    private fun MutablePreferences.clearRest() {
        remove(Keys.REST_END_AT)
        remove(Keys.REST_ELAPSED_END_AT)
        remove(Keys.REST_REMAINING)
    }

    private fun List<Int>.encode(): String = joinToString(",")
    private fun String.decodeSegments(): List<Int> = split(',').mapNotNull { it.toIntOrNull() }.filter { it >= 25 }
}
