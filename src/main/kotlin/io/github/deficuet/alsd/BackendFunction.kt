package io.github.deficuet.alsd

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO.PNG
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.esotericsoftware.spine.Animation
import com.esotericsoftware.spine.SkeletonBinary
import com.esotericsoftware.spine.SkeletonJson
import io.github.deficuet.tools.image.flipY
import io.github.deficuet.unitykt.*
import io.github.deficuet.unitykt.data.*
import javafx.application.Platform
import javafx.scene.paint.Color
import javafx.stage.FileChooser
import org.json.JSONObject
import tornadofx.*
import java.io.File
import java.io.FileWriter
import java.nio.file.Files
import javax.imageio.ImageIO

class BackendFunctions(private val ui: ALSDViewerUI) {
    fun importFile(): File? {
        ui.lastSelection = ""
        val files = chooseFile(
            "选择文件", arrayOf(
                FileChooser.ExtensionFilter("All types", "*.*")
            ), File(configs.importFilesPath)
        )
        if (files.isEmpty()) return null
        val file = files[0]
        configs.importFilesPath = file.parent
        return file
    }

    private fun extractFromFile(file: File): SkeletonAtlasInfo {
        return UnityAssetManager().use { manager ->
            val bundleContext = manager.loadFile(file.absolutePath)
            val folderPath = "$cachePath/${file.nameWithoutExtension}".also {
                File(it).apply { mkdir() }
            }
            val skData = bundleContext.objectList.firstObjectOf<AssetBundle>()
                .mContainer[0].second.asset.getObjAs<MonoBehaviour>().typeTreeJson!!
            val skObj = bundleContext.objects.getAs<TextAsset>(
                skData.getJSONObject("skeletonJSON").getLong("m_PathID")
            )
            val skFile = File("$folderPath/${skObj.mName}").apply {
                writeBytes(skObj.mScript)
            }
            val atlasData = bundleContext.objects.getAs<MonoBehaviour>(
                skData.getJSONArray("atlasAssets")[0].cast<JSONObject>().getLong("m_PathID")
            ).typeTreeJson!!
            val atlasObj = bundleContext.objects.getAs<TextAsset>(
                atlasData.getJSONObject("atlasFile").getLong("m_PathID")
            )
            val atlasFile = File("$folderPath/${atlasObj.mName}").apply {
                writeBytes(atlasObj.mScript)
            }
            val material = bundleContext.objects.getAs<Material>(
                atlasData.getJSONArray("materials")[0].cast<JSONObject>().getLong("m_PathID")
            )
            val tex = material.mSavedProperties.mTexEnvs[0].second.mTexture.getObj()?.cast<Texture2D>()
            if (tex != null) {
                ImageIO.write(
                    tex.image.flipY(), "png",
                    File("$folderPath/${tex.mName}.png")
                )
            }
            SkeletonAtlasInfo(
                FileHandle(skFile), FileHandle(atlasFile),
                skData.getFloat("defaultMix")
            )
        }
    }

    fun loadFile(file: File) {
        Platform.runLater {
            ui.taskNameLabel.textFill = Color.BLACK
            ui.taskNameStr.value = "加载中：${file.nameWithoutExtension}"
        }
        val newInfo = extractFromFile(file)
        ui.fileName = file.nameWithoutExtension
        Platform.runLater {
            ui.animationList.clear()
            ui.taskNameStr.value = "当前任务：${file.nameWithoutExtension}"
            ui.controls.forEach { it.isDisable = false }
        }
        with(ui.window) {
            skeletonInfo.clear()
            skeletonInfo.add(newInfo)
            resetCamera()
            ui.windowApp.postRunnable {
                loadSkeleton()
                Platform.runLater {
                    ui.actionTimestampList.forEach {
                        it.actionDuration = "N/A"
                        it.finishDuration = "N/A"
                    }
                    analyzeAttack()
                }
            }
        }
    }

    private fun preRecording(): ViewerOptions {
        ui.controls.forEach { it.isDisable = true }
        ui.window.state = ViewerState.PRE_MEASURE
        val op = ViewerOptions(
            ui.zoomSlider.value,
            ui.speedSlider.value,
            ui.window.camera.position.x,
            ui.window.camera.position.y,
            Gdx.graphics.width,
            Gdx.graphics.height
        )
        runAndWaitFX {
            ui.zoomSlider.value = 1.0
            ui.speedSlider.value = 1.0
        }
        ui.window.resetCamera()
        ui.window.loadSkeleton()
        return op
    }

    private fun revertOptions(op: ViewerOptions) = with(op) {
        Gdx.graphics.setWindowedMode(
            windowWidth, windowHeight
        )
        ui.window.camera.position.set(cameraX, cameraY, 0f)
        runAndWaitFX {
            ui.zoomSlider.value = zoom
            ui.speedSlider.value = speed
        }
        ui.window.loadSkeleton()
    }

    private fun measuringTrigger(op: ViewerOptions) {
        val animation = ui.window.recordAnimations[0]
        Platform.runLater {
            ui.recordTaskNameStr.value = "当前任务：${animation.name}"
        }
        ui.window.animGroup[0].resetWith(animation, true)
        ui.window.task = RecordingTask(
            ui.fileName, op, animation, SkeletonBounds()
        )
        ui.window.state = ViewerState.MEASURING
    }

    fun recordingTrigger(task: RecordingTask) {
        with(task.skeletonBounds) {
            Gdx.graphics.setWindowedMode(
                width, height
            )
            ui.window.camera.position.set(
                minX + width / 2f,
                minY + height / 2f,
                0f
            )
        }
        ui.window.animGroup[0].resetWith(task.animation, false)
        task.encoder = PNG()
        task.frameBuffer = FrameBuffer(
            Pixmap.Format.RGBA8888, task.width, task.height, false
        )
        ui.window.state = ViewerState.RECORDING
    }

    fun postRecording(task: RecordingTask) {
        task.frameBuffer.apply {
            end(); dispose()
        }
        task.encoder.dispose()
        task.writer.close()
        ui.window.recordAnimations.removeAt(0)
        if (ui.window.recordAnimations.isEmpty()) {
            revertOptions(task.viewerOptions)
            ui.window.state = ViewerState.AUTO
            ui.controls.forEach { it.isDisable = false }
        } else {
            with(task.viewerOptions) {
                Gdx.graphics.setWindowedMode(
                    windowWidth, windowHeight
                )
            }
            measuringTrigger(task.viewerOptions)
        }
    }

    fun recordCurrentAnimation() {
        ui.window.recordAnimations.add(
            ui.window.animGroup[0].state.getCurrent(0).animation
        )
        measuringTrigger(preRecording())
    }

    fun recordAllAnimations() {
        ui.window.recordAnimations.addAll(
            ui.window.animGroup[0].skeletonData.animations
        )
        measuringTrigger(preRecording())
    }

    private fun analyzeAttack() {
        val skd = ui.window.animGroup[0].skeletonData
        for (animationName in ATTACK_ANIMATIONS) {
            val animation: Animation? = skd.findAnimation(animationName)
            if (animation != null) {
                val result = animation.analyzeTimeline()
                val record = ui.actionTimestampTable.getValue(animationName)
                val actionDuration = result["action"]
                if (actionDuration != null)
                    record.actionDuration = "%.4f".format(actionDuration)
                val finishDuration = result["finish"]
                if (finishDuration != null)
                    record.finishDuration = "%.4f".format(finishDuration)
            }
        }
    }

    fun analyzeAll(folder: File): Unit = Files.newDirectoryStream(folder.toPath()).use { stream ->
        val result = JSONObject()
        for (file in stream) {
            if (!Files.isRegularFile(file)) continue
            Platform.runLater {
                ui.analyzeTaskNameStr.value = "当前任务：${file.fileName}"
            }
            val skeletonFileInfo: SkeletonAtlasInfo
            try {
                skeletonFileInfo = extractFromFile(file.toFile())
            } catch (e: Exception) {
                e.printStackTrace()
                continue
            }
            val skd = with(skeletonFileInfo) {
                runAndWaitLwjgl(ui.windowApp) {
                    val atlas = DefaultTextureAtlas(
                        skeletonFile,
                        TextureAtlas.TextureAtlasData(
                            atlasFile, atlasFile.parent(), false
                        )
                    )
                    if (skeletonFileInfo.skeletonFile.extension()!!.contentEquals("skel")) {
                        SkeletonBinary(atlas).readSkeletonData(skeletonFile)
                    } else {
                        SkeletonJson(atlas).readSkeletonData(skeletonFile)
                    }
                }
            } ?: continue
            val animMap = mutableMapOf<String, Map<String, Float>>()
            for (animName in ATTACK_ANIMATIONS) {
                val anim = runAndWaitLwjgl(ui.windowApp) {
                    skd.findAnimation(animName)
                }
                if (anim != null) {
                    val resultMap = anim.analyzeTimeline()
                    if (resultMap.isNotEmpty()) {
                        animMap[anim.name] = resultMap
                    }
                }
            }
            if (animMap.isNotEmpty()) {
                result.put(file.fileName.toString(), animMap.toJSONObject())
            } else {
                println("\u001b[93m${file.fileName} 无可分析动画\u001b[0m")
            }
        }
        FileWriter("$outputPath/${folder.name}.json").use {
            result.write(it, 4, 0)
        }
        Platform.runLater {
            ui.analyzeTaskNameStr.value = "当前任务：已完成"
        }
    }
}
