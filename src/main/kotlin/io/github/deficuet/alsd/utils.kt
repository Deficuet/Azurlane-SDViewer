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
import io.github.deficuet.jimage.fancyBufferedImage
import io.github.deficuet.unitykt.UnityAssetManager
import io.github.deficuet.unitykt.classes.AssetBundle
import io.github.deficuet.unitykt.classes.MonoBehaviour
import io.github.deficuet.unitykt.firstObjectOf
import io.github.deficuet.unitykt.pptr.getAs
import javafx.application.Platform
import kotlinx.serialization.Serializable
import net.mamoe.yamlkt.Yaml
import tornadofx.*
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.concurrent.FutureTask
import javax.imageio.stream.FileImageOutputStream
import kotlin.io.path.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.math.ceil
import javafx.scene.paint.Color as ColorFX

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
    return fancyBufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED) {
        drawImage(this@toIndexed, 0, 0, Color.WHITE,  null)
    }
}

fun deleteDirectory(pathString: String) {
    val path = Path(pathString)
    Files.newDirectoryStream(path).use { stream ->
        stream.forEach {
            when {
                it.isDirectory() -> deleteDirectory(it.toString())
                it.isRegularFile() -> it.deleteExisting()
            }
        }
    }
    path.deleteExisting()
}


val TrackEntry.isCompleteTwice: Boolean get() {
    return trackTime >= 2 * (animationEnd - animationStart)
}

fun <P: View, T> runBlockingFX(gui: P, task: P.() -> T): T? {
    return try {
        if (Platform.isFxApplicationThread()) {
            gui.task()
        } else {
            val future = FutureTask { gui.task() }
            Platform.runLater(future)
            future.get()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun <T> runBlockingLwjgl(app: LwjglApplication, block: () -> T): T? {
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
    var assetSystemRoot: String = "",
    var importFilesPath: String = ""
)

val defaultConfig by lazy { Configurations() }

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

fun File.withDefaultPath(defaultPath: String = "C:/Users"): File {
    return if (exists() && isDirectory) this else File(defaultPath)
}

val dependencies: Map<String, List<String>> by lazy {
    UnityAssetManager.new().use { manager ->
        val depContext = manager.loadFile(Path(configs.assetSystemRoot).resolve("dependencies"))
        val bundle = depContext.objectList.firstObjectOf<AssetBundle>()
        val mono = bundle.mContainer.values.first()[0].asset.getAs<MonoBehaviour>()
        mono.toTypeTreeJson()!!.let { json ->
            val keys = json.getJSONArray("m_Keys")
            val values = json.getJSONArray("m_Values")
            val table = mutableMapOf<String, List<String>>()
            for (i in 0 until keys.length()) {
                val key = keys.getString(i)
                val value = values.getJSONObject(i).getJSONArray("m_Dependencies").map { it.toString() }
                table[key] = value
            }
            table
        }
    }
}

val errorTextFill: ColorFX = ColorFX.rgb(187, 0, 17)
