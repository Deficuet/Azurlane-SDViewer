package io.github.deficuet.alsd

import com.esotericsoftware.spine.Animation
import com.esotericsoftware.spine.Animation.EventTimeline
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
    "attack", "attack_left", "attack_swim", "attack_swim_left"
)

fun Animation.analyzeTimeline(): Map<String, Float> {
    val timeline = timelines.filterIsInstance<EventTimeline>().firstOrNull() ?: return emptyMap()
    val result = mutableMapOf<String, Float>()
    for (event in timeline.events) {
        result[event.data.name] = event.time
    }
    return result
}
