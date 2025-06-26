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
import io.github.deficuet.jimage.flipY
import io.github.deficuet.unitykt.UnityAssetManager
import io.github.deficuet.unitykt.classes.*
import io.github.deficuet.unitykt.firstObjectOf
import io.github.deficuet.unitykt.pptr.getAs
import io.github.deficuet.unitykt.pptr.getObj
import io.github.deficuet.unitykt.pptr.safeGetAs
import javafx.stage.FileChooser
import org.json.JSONArray
import org.json.JSONObject
import tornadofx.*
import java.io.File
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.*
import javafx.scene.paint.Color as ColorFX

class Functions(private val ui: ALSDViewerUI) {
    fun importFile(): File? {
        ui.lastSelection = ""
        val files = chooseFile(
            "选择文件", arrayOf(
                FileChooser.ExtensionFilter("All types", "*.*")
            ), File(configs.importFilesPath)
                .withDefaultPath(Path(configs.assetSystemRoot).resolve("char").pathString)
                .withDefaultPath(),
            owner = ui.primaryStage
        )
        if (files.isEmpty()) return null
        val file = files[0]
        configs.importFilesPath = file.parent
        return file
    }

    fun importFolder(): File? {
        ui.lastSelection = ""
        val folder = chooseDirectory(
            "选择文件夹", File(configs.importFilesPath)
                .withDefaultPath(),
            owner = ui.primaryStage
        ) ?: return null
        configs.importFilesPath = folder.parent
        return folder
    }

    private fun extractFromFile(file: File, dependencies: Array<Path>): SkeletonAtlasInfo {
        return UnityAssetManager.new(Path(configs.assetSystemRoot)).use { manager ->
            val bundleContext = manager.loadFile(file.absolutePath)
            manager.loadFiles(*dependencies)
            val folderPath = "$cachePath/${file.nameWithoutExtension}".also {
                File(it).apply { mkdir() }
            }
            val bundle = bundleContext.objectList.firstObjectOf<AssetBundle>()
            val skData = bundle.mContainer.values.first()[0].asset.getAs<MonoBehaviour>().toTypeTreeJson()!!
            val skObj = with(skData.getJSONObject("skeletonJSON")) {
                bundle.createPPtr<TextAsset>(
                    getInt("m_FileID"),
                    getLong("m_PathID")
                ).getObj()
            }
//                bundleContext.objectMap.getAs<TextAsset>(
//                skData.getJSONObject("skeletonJSON").getLong("m_PathID")
//            )
            val skFile = File("$folderPath/${skObj.mName}").apply {
                writeBytes(skObj.mScript)
            }
            val atlasMono = with(skData.getJSONArray("atlasAssets")[0] as JSONObject) {
                bundle.createPPtr<MonoBehaviour>(
                    getInt("m_FileID"),
                    getLong("m_PathID")
                ).getObj()
            }
            val atlasData = atlasMono.toTypeTreeJson()!!
//                bundleContext.objectMap.getAs<MonoBehaviour>(
//                skData.getJSONArray("atlasAssets")[0].cast<JSONObject>().getLong("m_PathID")
//            ).toTypeTreeJson()!!
            val atlasObj = with(atlasData.getJSONObject("atlasFile") as JSONObject) {
                atlasMono.createPPtr<TextAsset>(
                    getInt("m_FileID"),
                    getLong("m_PathID")
                ).getObj()
            }
//                bundleContext.objectMap.getAs<TextAsset>(
//                atlasData.getJSONObject("atlasFile").getLong("m_PathID")
//            )
            val atlasFile = File("$folderPath/${atlasObj.mName}").apply {
                writeBytes(atlasObj.mScript)
            }
            atlasData.getJSONArray("materials").forEach { materialInfo ->
                val materialObj = with(materialInfo as JSONObject) {
                    atlasMono.createPPtr<Material>(
                        getInt("m_FileID"),
                        getLong("m_PathID")
                    ).getObj()
                }
                val tex = materialObj.mSavedProperties.mTexEnvs.values.first()[0].mTexture.safeGetAs<Texture2D>()
                if (tex != null) {
                    ImageIO.write(
                        tex.getImage()!!.flipY().apply(true), "png",
                        File("$folderPath/${tex.mName}.png")
                    )
                }
            }
//            val material = bundleContext.objectMap.getAs<Material>(
//                atlasData.getJSONArray("materials")[0].cast<JSONObject>().getLong("m_PathID")
//            )
//            val tex = material.mSavedProperties.mTexEnvs.values.first()[0].mTexture.safeGetAs<Texture2D>()
//            if (tex != null) {
//                ImageIO.write(
//                    tex.getImage()!!.flipY().apply(true), "png",
//                    File("$folderPath/${tex.mName}.png")
//                )
//            }
            SkeletonAtlasInfo(
                FileHandle(skFile), FileHandle(atlasFile),
                skData.getFloat("defaultMix")
            )
        }
    }

    fun loadFile(file: File) {
        runBlockingFX(ui) {
            controls.forEach { it.isDisable = true }
            dependenciesList.clear()
        }
        val rootPath = Path(configs.assetSystemRoot)
        val dependenciesPathList = dependencies[
            "char/${file.name}"
        ]
        if (dependenciesPathList == null) {
            runBlockingFX(ui) {
                taskNameLabel.textFill = errorTextFill
                taskNameStr.value = "dependencies文件已过时"
            }
            return
        }
        val dependenciesTable = dependenciesPathList.associateWith { rootPath.resolve(it) }
        val dependenciesCheckTable = dependenciesPathList.associateWith {
            dependenciesTable.getValue(it).exists()
        }
        runBlockingFX(ui) {
            dependenciesList.addAll(dependenciesPathList)
            dependenciesColumn.cellFormat {
                text = it
                textFill = when (dependenciesCheckTable.getValue(it)) {
                    true -> ColorFX.BLUE
                    else -> errorTextFill
                }
                tooltip(it)
            }
        }
        if (dependenciesCheckTable.values.any { !it }) {
            runBlockingFX(ui) {
                taskNameLabel.textFill = errorTextFill
                taskNameStr.value = "依赖项缺失"
            }
            return
        }
        runBlockingFX(ui) {
            taskNameLabel.textFill = ColorFX.BLACK
            taskNameStr.value = "加载中：${file.nameWithoutExtension}"
        }
        val newInfo = extractFromFile(file, dependenciesTable.values.toTypedArray())
        runBlockingFX(ui) {
            fileName = file.nameWithoutExtension
            animationList.clear()
            taskNameStr.value = "当前任务：${file.nameWithoutExtension}"
            controls.forEach { it.isDisable = false }
        }
        with(ui.window) {
            skeletonInfo.clear()
            skeletonInfo.add(newInfo)
            resetCamera()
            runBlockingLwjgl(ui.windowApp) {
                loadSkeleton()
                runBlockingFX(ui) {
                    actionTimestampList.forEach {
                        it.actionDuration = "N/A"
                        it.finishDuration = "N/A"
                    }
                    analyzeAttack()
                }
            }
        }
    }

    private fun extractFromFolder(folder: File): SkeletonAtlasInfo? {
        val fileList = folder.listFiles()!!
        val atlasFile = fileList.firstOrNull { it.extension == "atlas" }
        if (atlasFile == null) {
            runBlockingFX(ui) {
                taskNameLabel.textFill = errorTextFill
                taskNameStr.value = "找不到.atlas文件"
            }
            return null
        }
        val skelFile = fileList.firstOrNull { it.extension == "skel" }
        if (skelFile == null) {
            runBlockingFX(ui) {
                taskNameLabel.textFill = errorTextFill
                taskNameStr.value = "找不到.skel文件"
            }
            return null
        }
        if (fileList.firstOrNull { it.extension == "png" } == null) {
            runBlockingFX(ui) {
                taskNameLabel.textFill = errorTextFill
                taskNameStr.value = "找不到.png文件"
            }
            return null
        }
        return SkeletonAtlasInfo(FileHandle(skelFile), FileHandle(atlasFile), 0.2f)
    }

    fun loadFolder(folder: File) {
        runBlockingFX(ui) {
            controls.forEach { it.isDisable = true }
            dependenciesList.clear()
            taskNameLabel.textFill = ColorFX.BLACK
            taskNameStr.value = "加载中：${folder.name}"
        }
        val newInfo = extractFromFolder(folder) ?: return
        runBlockingFX(ui) {
            fileName = folder.name
            animationList.clear()
            taskNameStr.value = "当前任务：${folder.name}"
            controls.forEach { it.isDisable = false }
        }
        with(ui.window) {
            skeletonInfo.clear()
            skeletonInfo.add(newInfo)
            resetCamera()
            runBlockingLwjgl(ui.windowApp) {
                loadSkeleton()
                runBlockingFX(ui) {
                    actionTimestampList.forEach {
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
        runBlockingFX(ui) {
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
        runBlockingFX(ui) {
            ui.zoomSlider.value = zoom
            ui.speedSlider.value = speed
        }
        ui.window.loadSkeleton()
    }

    private fun measuringTrigger(op: ViewerOptions) {
        val animation = ui.window.recordAnimations[0]
        runBlockingFX(ui) {
            recordTaskNameStr.value = "当前任务：${animation.name}"
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
                val result = animation.getEvents()
                val record = ui.actionTimestampTable.getValue(animationName)
                val actionDuration = result.filterIsInstance<JSONObject>()
                    .firstOrNull { it.getString("name") == "action" }
                    ?.get("time")
                if (actionDuration != null)
                    record.actionDuration = "%.4f".format(actionDuration)
                val finishDuration = result.filterIsInstance<JSONObject>()
                    .firstOrNull { it.getString("name") == "finish" }
                    ?.get("time")
                if (finishDuration != null)
                    record.finishDuration = "%.4f".format(finishDuration)
            }
        }
    }

    fun analyzeAll(folder: File) = Files.newDirectoryStream(folder.toPath()).use { stream ->
        val result = JSONObject()
        for (file in stream) {
            if (!Files.isRegularFile(file)) continue
            runBlockingFX(ui) {
                analyzeTaskNameStr.value = "当前任务：${file.fileName}"
            }
            val rootPath = Path(configs.assetSystemRoot)
            val dependenciesPathList = dependencies[
                "char/${file.name}"
            ] ?: continue
            val dependenciesTable = dependenciesPathList.associateWith { rootPath.resolve(it) }
            val dependenciesCheckTable = dependenciesPathList.associateWith {
                dependenciesTable.getValue(it).exists()
            }
            if (dependenciesCheckTable.values.any { !it }) continue
            val skeletonFileInfo: SkeletonAtlasInfo
            try {
                skeletonFileInfo = extractFromFile(file.toFile(), dependenciesTable.values.toTypedArray())
            } catch (e: Exception) {
                e.printStackTrace()
                continue
            }
            val skd = with(skeletonFileInfo) {
                runBlockingLwjgl(ui.windowApp) {
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
            val animMap = mutableMapOf<String, JSONArray>()
            for (animName in ATTACK_ANIMATIONS) {
                val anim = runBlockingLwjgl(ui.windowApp) {
                    skd.findAnimation(animName)
                }
                if (anim != null) {
                    val resultMap = anim.getEvents()
                    if (!resultMap.isEmpty) {
                        animMap[anim.name] = resultMap
                    }
                }
            }
            if (animMap.isNotEmpty()) {
                result.put(file.fileName.toString(), JSONObject(animMap))
            } else {
                println("\u001b[93m${file.fileName} 无可分析动画\u001b[0m")
            }
        }
        FileWriter("$outputPath/${folder.name}.json").use {
            result.write(it, 4, 0)
        }
        runBlockingFX(ui) {
            analyzeTaskNameStr.value = "当前任务：已完成"
        }
    }

    companion object {
        fun importAssetSystemRoot(): File? {
            val folder = chooseDirectory(
                "选择文件夹",
                File(configs.assetSystemRoot).withDefaultPath()
            ) ?: return null
            configs.assetSystemRoot = folder.absolutePath
            return folder
        }
    }
}
