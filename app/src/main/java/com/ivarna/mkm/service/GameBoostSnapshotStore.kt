package com.ivarna.mkm.service

import android.content.Context
import android.provider.Settings
import com.ivarna.mkm.data.model.CpuBoostSnapshot
import com.ivarna.mkm.data.model.GameBoostComponent
import com.ivarna.mkm.data.model.GameBoostSnapshot
import com.ivarna.mkm.data.model.GpuBoostSnapshot
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Storage boundary so transaction durability failures can be tested deterministically. */
interface GameBoostSessionStore {
    fun save(snapshot: GameBoostSnapshot): Boolean
    fun hasSnapshot(): Boolean
    fun load(): GameBoostSnapshot?
    fun clear(): Boolean
    fun isSameBoot(snapshot: GameBoostSnapshot): Boolean
}

/** Durable session ownership. A snapshot is written before any tuning write. */
class GameBoostSnapshotStore(context: Context) : GameBoostSessionStore {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun save(snapshot: GameBoostSnapshot): Boolean =
        prefs.edit().putString(KEY_SNAPSHOT, GameBoostSnapshotJson.encode(snapshot).toString()).commit()

    override fun hasSnapshot(): Boolean = prefs.contains(KEY_SNAPSHOT)

    override fun load(): GameBoostSnapshot? = runCatching {
        prefs.getString(KEY_SNAPSHOT, null)?.let { GameBoostSnapshotJson.decode(JSONObject(it)) }
    }.getOrNull()

    override fun clear(): Boolean = prefs.edit().remove(KEY_SNAPSHOT).commit()

    override fun isSameBoot(snapshot: GameBoostSnapshot): Boolean {
        val current = currentIdentity()
        return matchesBoot(snapshot.bootCount, snapshot.bootId, current.first, current.second)
    }

    fun currentIdentity(): Pair<Int?, String?> = currentBootCount(appContext) to currentBootId()

    companion object {
        const val PREFS = "game_boost_session"
        private const val KEY_SNAPSHOT = "snapshot"

        fun currentBootCount(context: Context): Int? = runCatching {
            // BOOT_COUNT is absent on some vendor builds; null is a valid identity component.
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
        }.getOrNull()

        fun currentBootId(): String? = runCatching {
            File("/proc/sys/kernel/random/boot_id").takeIf { it.canRead() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
        }.getOrNull()

        fun matchesBoot(snapshotCount: Int?, snapshotId: String?, currentCount: Int?, currentId: String?): Boolean = when {
            snapshotCount != null && currentCount != null && snapshotId != null && currentId != null ->
                snapshotCount == currentCount && snapshotId == currentId
            snapshotCount != null && currentCount != null -> snapshotCount == currentCount
            snapshotId != null && currentId != null -> snapshotId == currentId
            else -> false
        }
    }
}

/** Kept separate so JSON round-tripping is testable without an Android Context. */
object GameBoostSnapshotJson {
    fun encode(snapshot: GameBoostSnapshot) = JSONObject().apply {
        put("version", snapshot.version)
        putOpt("bootCount", snapshot.bootCount)
        putOpt("bootId", snapshot.bootId)
        put("phase", snapshot.phase)
        put("cpu", JSONArray().apply {
            snapshot.cpu.forEach { p -> put(JSONObject().apply {
                put("policyId", p.policyId); put("path", p.path); putOpt("governor", p.governor)
                putOpt("minFreq", p.minFreq); putOpt("maxFreq", p.maxFreq); putOpt("targetFreq", p.targetFreq)
            }) }
        })
        snapshot.gpu?.let { g -> put("gpu", JSONObject().apply {
            put("path", g.path); putOpt("governor", g.governor); putOpt("minFreq", g.minFreq); putOpt("maxFreq", g.maxFreq); putOpt("targetFreq", g.targetFreq)
        }) }
        put("attempted", JSONArray(snapshot.attempted.map { it.name }))
        put("applied", JSONArray(snapshot.applied.map { it.name }))
        put("thermallyReleased", JSONArray(snapshot.thermallyReleased.map { it.name }))
    }

    fun decode(obj: JSONObject): GameBoostSnapshot {
        fun components(key: String): Set<GameBoostComponent> = buildSet {
            val values = obj.optJSONArray(key) ?: return@buildSet
            for (i in 0 until values.length()) runCatching { add(GameBoostComponent.valueOf(values.getString(i))) }
        }
        val cpu = buildList {
            val values = obj.optJSONArray("cpu") ?: JSONArray()
            for (i in 0 until values.length()) {
                val p = values.getJSONObject(i)
                add(CpuBoostSnapshot(
                    policyId = p.getInt("policyId"),
                    path = p.getString("path"),
                    governor = p.optString("governor").takeIf { it.isNotBlank() && it != "null" },
                    minFreq = p.optLong("minFreq", 0L).takeIf { it > 0L },
                    maxFreq = p.optLong("maxFreq", 0L).takeIf { it > 0L },
                    targetFreq = p.optLong("targetFreq", 0L).takeIf { it > 0L }
                ))
            }
        }
        val gpu = obj.optJSONObject("gpu")?.let { g ->
            GpuBoostSnapshot(
                path = g.getString("path"),
                governor = g.optString("governor").takeIf { it.isNotBlank() && it != "null" },
                minFreq = g.optLong("minFreq", 0L).takeIf { it > 0L },
                maxFreq = g.optLong("maxFreq", 0L).takeIf { it > 0L },
                targetFreq = g.optLong("targetFreq", 0L).takeIf { it > 0L }
            )
        }
        return GameBoostSnapshot(
            version = obj.optInt("version", 1),
            bootCount = if (obj.has("bootCount") && !obj.isNull("bootCount")) obj.getInt("bootCount") else null,
            bootId = obj.optString("bootId").takeIf { it.isNotBlank() && it != "null" }, phase = obj.optString("phase", "ENABLING"),
            cpu = cpu, gpu = gpu, attempted = components("attempted"), applied = components("applied"), thermallyReleased = components("thermallyReleased")
        )
    }
}
