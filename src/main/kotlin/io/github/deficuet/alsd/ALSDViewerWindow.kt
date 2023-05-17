package io.github.deficuet.alsd

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Interpolation.linear
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.utils.FloatArray as GdxFloatArray
import com.esotericsoftware.spine.*
import io.github.deficuet.tools.file.deleteDirectory
import io.github.deficuet.unitykt.cast
import javafx.application.Platform
import net.mamoe.yamlkt.Yaml
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.sign

enum class ViewerState {
    AUTO,
    PRE_MEASURE,
    MEASURING,
    PRE_RECORD,
    RECORDING,
    POST_RECORD
}

class ALSDViewerWindow(
    private val ui: ALSDViewerUI,
    private val uiScale: Int
): ApplicationAdapter() {
    private lateinit var stage: Stage
    private lateinit var batch: PolygonSpriteBatch
    private lateinit var renderer: SkeletonRenderer
    private lateinit var debugRenderer: SkeletonRendererDebug
    lateinit var camera: OrthographicCamera

    val skeletonInfo = mutableListOf<SkeletonAtlasInfo>()
    val animGroup = mutableListOf<SkeletonAnimationGroup>()

    val recordAnimations = mutableListOf<Animation>()
    var state = ViewerState.AUTO
    lateinit var task: RecordingTask

    override fun create() {
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            e.printStackTrace()
            Runtime.getRuntime().halt(0)
        }
        stage = Stage(ScreenViewport())
        batch = PolygonSpriteBatch(3100)
        renderer = SkeletonRenderer().apply {
            premultipliedAlpha = false
        }
        debugRenderer = SkeletonRendererDebug()
        camera = OrthographicCamera()
        resetCamera()
        Gdx.input.inputProcessor = InputMultiplexer(
            stage, object : InputAdapter() {
                private var offsetX = 0
                private var offsetY = 0

                override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                    if (state == ViewerState.AUTO) {
                        offsetX = screenX
                        offsetY = Gdx.graphics.height - 1 - screenY
                    }
                    return false
                }

                override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
                    if (state == ViewerState.AUTO) {
                        val dy = Gdx.graphics.height - 1 - screenY
                        with(camera.position) {
                            x -= (screenX - offsetX) * camera.zoom
                            y -= (dy - offsetY) * camera.zoom
                        }
                        offsetX = screenX
                        offsetY = dy
                    }
                    return false
                }

                override fun scrolled(amountX: Float, amountY: Float): Boolean {
                    if (state == ViewerState.AUTO) {
                        var zoom = ui.zoomSlider.value
                        val min = ui.zoomSlider.min
                        val max = ui.zoomSlider.max
                        val speed = minOf(1.2, (zoom - min) / (max - min) * 3.5)
                        zoom -= linear.apply(0.02f, 0.2f, speed.toFloat()) * sign(amountY)
                        Platform.runLater { ui.zoomSlider.value = MathUtils.clamp(zoom, min, max) }
                    }
                    return false
                }
            }
        )
    }

    fun resetCamera() {
        camera.position.set(0f, 0f, 0f)
    }

    fun loadSkeleton() {
        animGroup.clear()
        skeletonInfo.forEach { info ->
            val tad = TextureAtlas.TextureAtlasData(
                info.atlasFile, info.atlasFile.parent(), false
            )
            val atlas = DefaultTextureAtlas(info.skeletonFile, tad)
            val skd = if (info.skeletonFile.extension()!!.contentEquals("skel")) {
                SkeletonBinary(atlas).apply {
                    scale = 0.5f
                }.readSkeletonData(info.skeletonFile)
            } else {
                SkeletonJson(atlas).apply {
                    scale = 0.5f
                }.readSkeletonData(info.skeletonFile)
            }
            val anims = skd.animations
            val defaultAnim = if (ui.lastSelection.isEmpty()) {
                anims.first().also { ui.lastSelection = it.name }
            } else {
                skd.findAnimation(ui.lastSelection)
            }
            val animState = AnimationState(AnimationStateData(skd)).apply {
                data.defaultMix = info.defaultMix
                setAnimation(
                    0, defaultAnim,
                    ui.loopCheckbox.isSelected
                )
            }
            if (anims.size > 1) {
                Platform.runLater {
                    ui.animationList.clear()
                    ui.animationList.addAll(anims.map { it.name })
                    ui.animationListView.selectionModel.select(defaultAnim.name)
                }
            }
            animGroup.add(
                SkeletonAnimationGroup(
                    Skeleton(skd), skd, animState
                )
            )
        }
    }

    override fun render() {
        Gdx.gl.glClearColor(1f, 1f, 1f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        camera.update()
        batch.projectionMatrix.set(camera.combined)
        val shapes = debugRenderer.shapeRenderer.apply {
            projectionMatrix = camera.combined
            if (state == ViewerState.AUTO) {
                color = Color.DARK_GRAY
                begin(ShapeRenderer.ShapeType.Line)
                line(0f, -2.14748365E9f, 0f, 2.14748365E9f)
                line(-2.14748365E9f, 0f, 2.14748365E9f, 0f)
                end()
            }
        }
        batch.begin()
        animGroup.forEach {
            it.skeleton.setFlip(ui.flipXCheckbox.isSelected, ui.flipYCheckbox.isSelected)
            val delta = if (state == ViewerState.AUTO) {
                Gdx.graphics.deltaTime
            } else if (state == ViewerState.MEASURING && !it.state.getCurrent(0).isComplete) {
                0.33f
            } else 0.033f
            it.update(delta, ui.speedSlider.value.toFloat())
            renderer.draw(batch, it.skeleton)
            if (state == ViewerState.MEASURING) {
                if (it.state.getCurrent(0).isCompleteTwice) {
                    state = ViewerState.PRE_RECORD
                    ui.functions.recordingTrigger(task)
                } else if (it.state.getCurrent(0).isComplete) {
                    with(task.skeletonBounds) {
                        it.skeleton.getBounds(min, max, GdxFloatArray())
                        maxX = maxOf(maxX, max.x + min.x)
                        maxY = maxOf(maxY, max.y + min.y)
                        minX = minOf(minX, min.x)
                        minY = minOf(minY, min.y)
                    }
                }
            }
        }
        batch.end()
        if (state == ViewerState.RECORDING) {
            animGroup.forEach {
                if (it.state.getCurrent(0).isComplete) {
                    state = ViewerState.POST_RECORD
                    ui.functions.postRecording(task)
                } else {
                    if (task.firstFramePassed) {
                        task.writer.writeToSequence(
                            ImageIO.read(
                                ByteArrayInputStream(
                                    ByteArrayOutputStream().use { byteOut ->
                                        task.encoder.write(
                                            byteOut, Pixmap.createFromFrameBuffer(
                                                0, 0, task.width, task.height
                                            )
                                        )
                                        byteOut.toByteArray()
                                    }
                                )
                            ).toIndexed()
                        )
                    } else {
                        task.firstFramePassed = true
                    }
                }
            }
        }
        if (state == ViewerState.AUTO && animGroup.isNotEmpty()) {
            val e = animGroup[0].state.getCurrent(0)
            shapes.apply {
                projectionMatrix.setToOrtho2D(
                    0f, 0f,
                    Gdx.graphics.width.toFloat(),
                    Gdx.graphics.height.toFloat()
                )
                updateMatrices()
                begin(ShapeRenderer.ShapeType.Line)
                val x = Gdx.graphics.width * e.animationTime / e.animationEnd
                color = Color.BLACK
                line(x, 0f, x, 12f)
                val markX = Gdx.graphics.width * (if (e.mixDuration == 0f) 1f
                else minOf(1f, e.mixTime / e.mixDuration))
                color = Color(0.73f, 0f, 0.066f, 1f)
                line(markX, 0f, markX, 12f)
                end()
            }
        }
    }

    override fun resize(width: Int, height: Int) {
        val x = camera.position.x
        val y = camera.position.y
        camera.setToOrtho(false)
        camera.position.set(x, y, 0f)
        stage.viewport.cast<ScreenViewport>().apply {
            unitsPerPixel = 1f / uiScale
            update(width, height, true)
        }
    }

    override fun dispose() {
        batch.dispose()
        stage.dispose()
        configFile.writeText(
            Yaml.encodeToString(Configurations.serializer(), configs)
        )
        deleteDirectory(cachePath)
    }
}
