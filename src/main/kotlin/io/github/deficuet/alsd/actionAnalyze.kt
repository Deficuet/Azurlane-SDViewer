package io.github.deficuet.alsd

import com.esotericsoftware.spine.Animation
import com.esotericsoftware.spine.Animation.EventTimeline
import org.json.JSONArray
import tornadofx.*

class ActionTimestamp(
    val animationName: String,
    actionDuration: String = "N/A",
    finishDuration: String = "N/A"
) {
    var actionDuration: String by property(actionDuration)
    fun actionDurationProperty() = getProperty(ActionTimestamp::actionDuration)

    var finishDuration: String by property(finishDuration)
    fun finishDurationProperty() = getProperty(ActionTimestamp::finishDuration)
}

val ATTACK_ANIMATIONS = listOf(
    "attack", "attack_left", "attack_swim", "attack_swim_left",
    "attack_L", "attack_R", "attack_left_L", "attack_left_R"
)

fun Animation.getEvents(): JSONArray {
    val timeline = timelines.filterIsInstance<EventTimeline>().firstOrNull() ?: return JSONArray()
    return timeline.events.sortedBy { it.time }.map {
        json {
            "name" (it.data.name)
            "time" (it.time)
        }
    }.let { JSONArray(it) }
}
