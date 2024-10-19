package nevariver

import com.intellij.concurrency.JobScheduler
import com.intellij.util.io.HttpRequests
import java.time.ZoneId
import java.util.*
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.prefs.Preferences
import java.util.regex.Pattern

private const val MIN_Y_RANGE = 5
const val MINUTE = 60000L
const val HOUR = MINUTE * 60
private const val MAX_CHART_INTERVAL = 48 * HOUR
private const val UPDATE_PERIOD = 10 * MINUTE

object NevaRiverWidgetModel : Runnable {
    var levelMap = TreeMap<Date, Int>()

    var minLevel = Integer.MAX_VALUE
    var maxLevel = Integer.MIN_VALUE
    var lastLevel = Integer.MIN_VALUE
    var minLevelTime = System.currentTimeMillis()
    var maxLevelTime = System.currentTimeMillis()
    var lastLevelTime = System.currentTimeMillis()

    private val dataPattern = Pattern.compile("<strong>([^<\\s]+)\\s?</strong>")

    private var future: ScheduledFuture<*>? = null
    private var myLastUpdateTime = System.currentTimeMillis() - UPDATE_PERIOD

    private val myBusy = AtomicBoolean()

    init {
        future = JobScheduler.getScheduler().schedule(this, 1, TimeUnit.SECONDS)
    }

    override fun run() {
        try {
            updateNow()
        } finally {
            future = JobScheduler.getScheduler().schedule(this, UPDATE_PERIOD, TimeUnit.MILLISECONDS)
        }
    }

    private fun updateNow() {
        if (myBusy.compareAndSet(false, true)) {
            try {
                updateData()
            } finally {
                myBusy.set(false)
            }
        }
    }


    private fun updateData() {
        //todo ветер http://old.meteoinfo.ru/pogoda/russia/leningrad-region/sankt-peterburg
        if (levelMap.isEmpty() || (System.currentTimeMillis() - myLastUpdateTime > UPDATE_PERIOD)) {
            val matcher = dataPattern.matcher(HttpRequests.request("https://asup.volgo-balt.ru/").readString(null))
            val data = mutableListOf<String>()
            while (matcher.find()) {
                data.add(matcher.group(1))
            }
            if (data.size == 2) {
                Calendar.getInstance(TimeZone.getTimeZone(ZoneId.of("Europe/Moscow"))).apply {
                    data[0].split(":").let {
                        set(Calendar.HOUR_OF_DAY, Integer.parseInt(it[0]))
                        set(Calendar.MINUTE, Integer.parseInt(it[1]))
                    }
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (timeInMillis > System.currentTimeMillis() + HOUR) {
                        timeInMillis = timeInMillis - 86400000L//day back for yesterday's data
                    }
                    levelMap[time] = Integer.parseInt(data[1])
                }
            }
            myLastUpdateTime = System.currentTimeMillis()
        }
        syncWithStorage()
        updateMinMax()
    }

    private fun updateMinMax() {
        minLevel = Integer.MAX_VALUE
        maxLevel = Integer.MIN_VALUE
        val minLevelTimes = ArrayList<Long>()
        var isMinLevelCollecting = false
        val maxLevelTimes = ArrayList<Long>()
        var isMaxLevelCollecting = false
        for (entry in levelMap.entries) {
            if (entry.value <= minLevel) {
                if (entry.value < minLevel) {
                    minLevelTimes.clear()
                    isMinLevelCollecting = true
                }
                if (isMinLevelCollecting) {
                    minLevel = entry.value
                    minLevelTimes.add(entry.key.time)
                }
            } else {
                isMinLevelCollecting = false
            }
            if (entry.value >= maxLevel) {
                if (entry.value > maxLevel) {
                    maxLevelTimes.clear()
                    isMaxLevelCollecting = true
                }
                if (isMaxLevelCollecting) {
                    maxLevel = entry.value
                    maxLevelTimes.add(entry.key.time)
                }
            } else {
                isMaxLevelCollecting = false
            }
        }
        minLevelTime = minLevelTimes[minLevelTimes.size / 2] //middle point of a peak
        maxLevelTime = maxLevelTimes[maxLevelTimes.size / 2]
        lastLevel = levelMap.lastEntry().value
        lastLevelTime = levelMap.lastKey().time
    }

    fun scaleTime(time: Long, iconWidth: Int): Double {
        return iconWidth.toDouble() * (1.0 - (lastLevelTime
            .toDouble() - time - MINUTE) / (lastLevelTime - levelMap.keys.first().time))
    }

    fun scaleLevel(value: Int, iconHeight: Int): Double {
        return (iconHeight.toDouble() - 1) * (1 - (value.toDouble() - minLevel + MIN_Y_RANGE) / (maxLevel - minLevel + 2 * MIN_Y_RANGE))
    }

    private fun syncWithStorage() {
        val node = Preferences.userRoot().node(ID.replace(' ', '_'))
        node.sync()

        //Save current state to FS
        levelMap.entries.forEach { (time, level) ->
            node.putInt(time.time.toString(), level)
        }
        //Filter out expired ones
        for (key in node.keys()) {
            val time = key.toLongOrNull()
            if (!time.isValid) {
                node.remove(key)
                time?.let { levelMap.remove(Date(it)) }
            } else {
                levelMap.putIfAbsent(Date(time!!), node.getInt(key, 0))
            }
        }
        node.flush()
    }

    private val Long?.isValid: Boolean
        get() = this != null
                && this < System.currentTimeMillis()
                && this >= System.currentTimeMillis() - MAX_CHART_INTERVAL
}