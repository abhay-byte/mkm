package com.ivarna.mkm.utils

import android.content.Context
import com.ivarna.mkm.data.model.BatterySessionRecord
import com.ivarna.mkm.data.model.SessionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Persists a rolling list of completed battery sessions to disk as JSON.
 *
 * Storage: `<filesDir>/battery_history.json`
 * Format: `{ "version": 1, "records": [ BatterySessionRecord, ... ] }`
 *
 * Concurrency: all reads/writes are guarded by `synchronized(lock)` to keep
 * the in-memory snapshot consistent with the file. The in-memory snapshot is
 * also exposed as a [StateFlow] so the UI updates immediately on save/clear.
 */
class BatteryHistoryManager(context: Context) {

    companion object {
        const val FILE_NAME = "battery_history.json"
        const val MAX_RECORDS = 100
        private const val VERSION = 1
    }

    private val appContext = context.applicationContext
    private val lock = Any()
    private val idGenerator = AtomicLong(System.currentTimeMillis())
    private val _records = MutableStateFlow<List<BatterySessionRecord>>(emptyList())
    val records: StateFlow<List<BatterySessionRecord>> = _records.asStateFlow()

    init {
        synchronized(lock) {
            _records.value = readFromDisk()
        }
    }

    /**
     * Append a record to history. The list is sorted newest-first and capped
     * at [MAX_RECORDS] entries. Persists to disk before returning.
     */
    fun add(record: BatterySessionRecord) {
        synchronized(lock) {
            val withId = if (record.id == 0L) record.copy(id = idGenerator.incrementAndGet()) else record
            val updated = (listOf(withId) + _records.value).take(MAX_RECORDS)
            _records.value = updated
            writeToDisk(updated)
        }
    }

    /**
     * Erase all history from memory and disk.
     */
    fun clear() {
        synchronized(lock) {
            _records.value = emptyList()
            writeToDisk(emptyList())
        }
    }

    /**
     * Build a [BatterySessionRecord] from raw session metrics.
     * Centralises the "what does a session snapshot look like" logic so the
     * tracker stays free of persistence concerns.
     */
    fun buildRecord(
        sessionType: SessionType,
        startTimeMs: Long,
        endTimeMs: Long,
        startPercent: Int,
        endPercent: Int,
        screenOnTimeMs: Long,
        screenOffTimeMs: Long,
        deepSleepTimeMs: Long,
        awakeTimeMs: Long,
        screenOnDrainPercent: Float,
        screenOffDrainPercent: Float,
        deepSleepDrainPercent: Float,
        awakeDrainPercent: Float,
        activeDrainPerHr: Float,
        idleDrainPerHr: Float,
        avgCurrentMa: Int,
        avgWattageW: Float,
        avgTemperatureC: Float
    ): BatterySessionRecord {
        return BatterySessionRecord(
            id = idGenerator.incrementAndGet(),
            sessionType = sessionType,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            startPercent = startPercent,
            endPercent = endPercent,
            screenOnTimeMs = screenOnTimeMs,
            screenOffTimeMs = screenOffTimeMs,
            deepSleepTimeMs = deepSleepTimeMs,
            awakeTimeMs = awakeTimeMs,
            screenOnDrainPercent = screenOnDrainPercent,
            screenOffDrainPercent = screenOffDrainPercent,
            deepSleepDrainPercent = deepSleepDrainPercent,
            awakeDrainPercent = awakeDrainPercent,
            activeDrainPerHr = activeDrainPerHr,
            idleDrainPerHr = idleDrainPerHr,
            avgCurrentMa = avgCurrentMa,
            avgWattageW = avgWattageW,
            avgTemperatureC = avgTemperatureC
        )
    }

    // ------------------------------------------------------------------
    // Disk I/O
    // ------------------------------------------------------------------

    private fun historyFile(): File = File(appContext.filesDir, FILE_NAME)

    private fun writeToDisk(list: List<BatterySessionRecord>) {
        runCatching {
            val arr = JSONArray()
            list.forEach { rec -> arr.put(serialize(rec)) }
            val root = JSONObject().apply {
                put("version", VERSION)
                put("records", arr)
            }
            val tmp = File(appContext.filesDir, "$FILE_NAME.tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(historyFile())) {
                // renameTo can fail across some filesystems; fall back to copy.
                historyFile().writeText(root.toString())
                tmp.delete()
            }
        }
    }

    private fun readFromDisk(): List<BatterySessionRecord> {
        val file = historyFile()
        if (!file.exists()) return emptyList()
        return runCatching {
            val root = JSONObject(file.readText())
            val arr = root.optJSONArray("records") ?: return@runCatching emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    deserialize(obj)?.let { add(it) }
                }
            }.sortedByDescending { it.endTimeMs }
        }.getOrDefault(emptyList())
    }

    private fun serialize(rec: BatterySessionRecord): JSONObject {
        return JSONObject().apply {
            put("id", rec.id)
            put("sessionType", rec.sessionType.name)
            put("startTimeMs", rec.startTimeMs)
            put("endTimeMs", rec.endTimeMs)
            put("startPercent", rec.startPercent)
            put("endPercent", rec.endPercent)
            put("screenOnTimeMs", rec.screenOnTimeMs)
            put("screenOffTimeMs", rec.screenOffTimeMs)
            put("deepSleepTimeMs", rec.deepSleepTimeMs)
            put("awakeTimeMs", rec.awakeTimeMs)
            put("screenOnDrainPercent", rec.screenOnDrainPercent.toDouble())
            put("screenOffDrainPercent", rec.screenOffDrainPercent.toDouble())
            put("deepSleepDrainPercent", rec.deepSleepDrainPercent.toDouble())
            put("awakeDrainPercent", rec.awakeDrainPercent.toDouble())
            put("activeDrainPerHr", rec.activeDrainPerHr.toDouble())
            put("idleDrainPerHr", rec.idleDrainPerHr.toDouble())
            put("avgCurrentMa", rec.avgCurrentMa)
            put("avgWattageW", rec.avgWattageW.toDouble())
            put("avgTemperatureC", rec.avgTemperatureC.toDouble())
        }
    }

    private fun deserialize(obj: JSONObject): BatterySessionRecord? {
        return runCatching {
            val typeName = obj.optString("sessionType", SessionType.DISCHARGING.name)
            val type = runCatching { SessionType.valueOf(typeName) }.getOrDefault(SessionType.DISCHARGING)
            BatterySessionRecord(
                id = obj.optLong("id", 0L),
                sessionType = type,
                startTimeMs = obj.optLong("startTimeMs"),
                endTimeMs = obj.optLong("endTimeMs"),
                startPercent = obj.optInt("startPercent"),
                endPercent = obj.optInt("endPercent"),
                screenOnTimeMs = obj.optLong("screenOnTimeMs"),
                screenOffTimeMs = obj.optLong("screenOffTimeMs"),
                deepSleepTimeMs = obj.optLong("deepSleepTimeMs"),
                awakeTimeMs = obj.optLong("awakeTimeMs"),
                screenOnDrainPercent = obj.optDouble("screenOnDrainPercent", 0.0).toFloat(),
                screenOffDrainPercent = obj.optDouble("screenOffDrainPercent", 0.0).toFloat(),
                deepSleepDrainPercent = obj.optDouble("deepSleepDrainPercent", 0.0).toFloat(),
                awakeDrainPercent = obj.optDouble("awakeDrainPercent", 0.0).toFloat(),
                activeDrainPerHr = obj.optDouble("activeDrainPerHr", 0.0).toFloat(),
                idleDrainPerHr = obj.optDouble("idleDrainPerHr", 0.0).toFloat(),
                avgCurrentMa = obj.optInt("avgCurrentMa"),
                avgWattageW = obj.optDouble("avgWattageW", 0.0).toFloat(),
                avgTemperatureC = obj.optDouble("avgTemperatureC", 0.0).toFloat()
            )
        }.getOrNull()
    }
}
