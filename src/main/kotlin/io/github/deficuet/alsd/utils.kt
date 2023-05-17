package io.github.deficuet.alsd

import com.badlogic.gdx.backends.lwjgl.LwjglApplication
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.PixmapIO.PNG
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.Vector2
import com.esotericsoftware.spine.Animation
import com.esotericsoftware.spine.AnimationState
import com.esotericsoftware.spine.AnimationState.TrackEntry
import com.esotericsoftware.spine.Skeleton
import com.esotericsoftware.spine.SkeletonData
import io.github.deficuet.tools.image.BufferedImage
import javafx.application.Platform
import java.io.File
import kotlinx.serialization.Serializable
import net.mamoe.yamlkt.Yaml
import org.json.JSONArray
import org.json.JSONObject
import java.awt.Color
import java.awt.image.BufferedImage
import java.util.concurrent.FutureTask
import javax.imageio.stream.FileImageOutputStream
import kotlin.math.ceil

data class SkeletonAtlasInfo(
    val skeletonFile: FileHandle,
    val atlasFile: FileHandle,
    val defaultMix: Float
)

data class SkeletonAnimationGroup(
    val skeleton: Skeleton,
    val skeletonData: SkeletonData,
    val state: AnimationState
) {
    fun update(delta: Float, speed: Float) {
        val inc = delta * speed
        skeleton.update(inc)
        state.apply {
            update(inc)
            apply(skeleton)
        }
        skeleton.updateWorldTransform()
    }

    fun resetWith(animation: Animation, loop: Boolean) {
        skeleton.setToSetupPose()
        state.setAnimation(0, animation, loop)
    }
}

class SkeletonBounds {
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    val min = Vector2(0f, 0f)
    val max = Vector2(0f, 0f)
    val width get() = ceil(maxX - minX).toInt()
    val height get() = ceil(maxY - minY).toInt()
}

data class ViewerOptions(
    val zoom: Double,
    val speed: Double,
    val cameraX: Float,
    val cameraY: Float,
    val windowWidth: Int,
    val windowHeight: Int
)

class RecordingTask(
    fileName: String,
    val viewerOptions: ViewerOptions,
    val animation: Animation,
    val skeletonBounds: SkeletonBounds
) {
    init {
        File("$outputPath/$fileName").mkdir()
    }

    val writer = GIFWriter(
        FileImageOutputStream(
            File("$outputPath/$fileName/${animation.name}.gif")
        ), BufferedImage.TYPE_BYTE_INDEXED, 30, true
    )

    val width get() = skeletonBounds.width
    val height get() = skeletonBounds.height

    lateinit var frameBuffer: FrameBuffer
    lateinit var encoder: PNG
    var firstFramePassed = false
}

fun BufferedImage.toIndexed(): BufferedImage {
    return BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED) {
        drawImage(this@toIndexed, 0, 0, Color.WHITE,  null)
    }
}

val TrackEntry.isCompleteTwice: Boolean get() {
    return trackTime >= 2 * (animationEnd - animationStart)
}

fun Map<*, *>.toJSONObject(): JSONObject {
    return JSONObject().apply {
        for (entry in this@toJSONObject) {
            val key = java.lang.String.valueOf(entry.key)
            val value = entry.value!!
            if (value::class.java.isArray) {
                put(key, JSONArray(value))
                continue
            }
            when (value) {
                is Collection<*> -> put(key, JSONArray(value))
                is Map<*, *> -> put(key, value.toJSONObject())
                else -> put(key, value)
            }
        }
    }
}

fun runAndWaitFX(block: Runnable) {
    try {
        if (Platform.isFxApplicationThread()) {
            block.run()
        } else {
            val task = FutureTask<Unit>(block, null)
            Platform.runLater(task)
            task.get()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun <T> runAndWaitLwjgl(app: LwjglApplication, block: () -> T): T? {
    return try {
        val task = FutureTask(block)
        app.postRunnable(task)
        task.get()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Serializable
data class Configurations(
    var importFilesPath: String
)

val defaultConfig by lazy {
    Configurations(
        importFilesPath = "C:/Users"
    )
}

val cacheFolder = File("./cache")
val cachePath: String by lazy { cacheFolder.absoluteFile.canonicalPath }

val outputFolder = File("./output")
val outputPath: String by lazy { outputFolder.absoluteFile.canonicalPath }

val configFile = File("alsd.yml")

val configs = if (configFile.exists()) {
    Yaml.decodeFromString(Configurations.serializer(), configFile.readText())
} else {
    configFile.writeText(
        Yaml.encodeToString(Configurations.serializer(), defaultConfig)
    )
    defaultConfig
}