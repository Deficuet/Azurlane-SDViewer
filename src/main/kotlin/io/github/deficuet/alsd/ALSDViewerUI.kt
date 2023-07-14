package io.github.deficuet.alsd

import com.badlogic.gdx.backends.lwjgl.LwjglApplication
import java.awt.Toolkit
import javafx.stage.Stage
import tornadofx.*
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.event.Event
import javafx.event.EventTarget
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.control.skin.TableColumnHeader
import javafx.scene.input.InputEvent
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent

class ALSDApp: App(ALSDViewerUI::class) {
    override fun start(stage: Stage) {
        if (!cacheFolder.exists()) cacheFolder.mkdir()
        if (!outputFolder.exists()) outputFolder.mkdir()
        with(stage) {
            isResizable = false
            x = 128.0
            y = 90.0
        }
        super.start(stage)
    }

    override fun stop() {
        val view = find(ALSDViewerUI::class, scope)
        view.windowApp.exit()
        super.stop()
    }
}

class ALSDViewerUI: View("碧蓝SD小人浏览器") {
    init {
        LwjglApplicationConfiguration.disableAudio = true
    }

    private val dpi = run {
        val os = System.getProperty("os.name")
        if ("Windows" in os) {
            Toolkit.getDefaultToolkit().screenResolution / 96f
        } else if ("OS X" in os) {
            val o = Toolkit.getDefaultToolkit().getDesktopProperty("apple.awt.contentScaleFactor")
            if (o is Float && o.toInt() > 2) 2f else 1f
        } else 1f
    }
    private val uiScale = if (dpi >= 2f) 2 else 1

    val window = ALSDViewerWindow(this, uiScale)
    val windowApp = LwjglApplication(
        window,
        LwjglApplicationConfiguration().apply {
            width = 768 * uiScale; height = 576 * uiScale
            x = 576; y = 128
            title = "Azur Lane SD Viewer"
            allowSoftwareMode = true
        }
    )

    val functions = BackendFunctions(this)
    val animationList = observableListOf<String>()
    var lastSelection = ""
    var fileName = ""

    val taskNameStr = SimpleStringProperty("当前任务：空闲中")
    val recordTaskNameStr = SimpleStringProperty("当前任务：空闲中")
    val analyzeTaskNameStr = SimpleStringProperty("当前任务：空闲中")
    private val zoomLabel = SimpleStringProperty("1.00")
    private val speedLabel = SimpleStringProperty("1.00")
    val controls = mutableListOf<Node>()

    var taskNameLabel: Label by singleAssign()
    var zoomSlider: Slider by singleAssign()
    var speedSlider: Slider by singleAssign()
    var loopCheckbox: CheckBox by singleAssign()
    var animationListView: ListView<String> by singleAssign()
    private var keepOnTopCheckbox: CheckBox by singleAssign()

    val actionTimestampTable = mapOf(
        "attack" to ActionTimestamp("attack"),
        "attack_left" to ActionTimestamp("attack_left"),
        "attack_swim" to ActionTimestamp("attack_swim"),
        "attack_swim_left" to ActionTimestamp("attack_swim_left")
    )

    val actionTimestampList: ObservableList<ActionTimestamp> =
        FXCollections.observableArrayList(actionTimestampTable.values)

    override val root = vbox {
        //region 导入文件
        hbox {
            alignment = Pos.CENTER_LEFT
            vboxConstraints {
                marginLeft = 16.0; marginTop = 16.0; marginRight = 16.0
            }
            button("导入文件") {
                minWidth = 80.0; minHeight = 30.0
                action {
                    isDisable = true
                    primaryStage.isAlwaysOnTop = false
                    val f = functions.importFile()
                    primaryStage.isAlwaysOnTop = keepOnTopCheckbox.isSelected
                    if (f != null) {
                        runAsync {
                            functions.loadFile(f)
                            isDisable = false
                        }
                    } else {
                        isDisable = false
                    }
                }
            }
            taskNameLabel = label(taskNameStr) {
                hboxConstraints {
                    marginLeft = 12.0
                }
            }
        }.also { controls.add(it) }
        //endregion
        //region 相机缩放
        hbox {
            alignment = Pos.CENTER_LEFT
            vboxConstraints {
                marginTop = 12.0; marginLeft = 16.0; marginRight = 16.0
            }
            label("缩放：")
            label(zoomLabel)
            zoomSlider = slider(0.01, 10.0, 1.0, Orientation.HORIZONTAL) {
                minWidth = 150.0
                hboxConstraints { marginLeft = 8.0 }
                addEventFilter(KeyEvent.ANY, Event::consume)
                valueProperty().addListener { _, _, new ->
                    zoomLabel.value = "%.2f".format(new).slice(0..3)
                    window.camera.zoom = 1 / new.toFloat()
                }
            }
            button("重置") {
                hboxConstraints { marginLeft = 8.0 }
                action {
                    val x = window.camera.position.x
                    window.resetCamera()
                    zoomSlider.value = 1.0
                    window.camera.position.x = x
                }
            }
        }.also { controls.add(it) }
        //endregion
        //region 播放速度
        hbox {
            alignment = Pos.CENTER_LEFT
            vboxConstraints {
                marginTop = 12.0; marginLeft = 16.0
            }
            label("速度：")
            label(speedLabel)
            speedSlider = slider(0.0, 3.0, 1.0, Orientation.HORIZONTAL) {
                minWidth = 150.0
                hboxConstraints { marginLeft = 8.0 }
                addEventFilter(KeyEvent.ANY, Event::consume)
                valueProperty().addListener { _, _, new ->
                    speedLabel.value = "%.2f".format(new)
                }
            }
            button("重置") {
                hboxConstraints { marginLeft = 8.0 }
                action {
                    speedSlider.value = 1.0
                }
            }
            isDisable = true
        }.also { controls.add(it) }
        //endregion
        //region 窗口置顶、循环播放
        hbox {
            alignment = Pos.CENTER_LEFT
            vboxConstraints {
                marginTop = 12.0; marginLeft = 16.0
            }
            label("其他设置：")
            vbox {
                hbox {
                    keepOnTopCheckbox = checkbox("窗口置顶") {
                        action {
                            primaryStage.isAlwaysOnTop = isSelected
                        }
                    }
                    loopCheckbox = checkbox("循环") {
                        isSelected = true
                        hboxConstraints { marginLeft = 16.0 }
                        action {
                            windowApp.postRunnable {
                                window.animGroup.forEach {
                                    it.state.setAnimation(
                                        0, lastSelection, isSelected
                                    )
                                }
                            }
                        }
                    }
                }
            }
            isDisable = true
        }.also { controls.add(it) }
        //endregion
        separator {
            vboxConstraints { marginTop = 12.0; marginLeft = 16.0; marginRight = 16.0 }
        }
        //region 动画管理
        vbox {
            vboxConstraints {
                marginTop = 12.0; marginLeft = 16.0; marginRight = 16.0
            }
            label("动画列表：")
            animationListView = listview(animationList) {
                vboxConstraints { marginTop = 12.0; }
                maxHeight = 112.0
                onUserSelectModified(clickCount = 1) { item ->
                    if (item != lastSelection) {
                        windowApp.postRunnable {
                            window.animGroup.forEach {
                                it.state.setAnimation(0, item, loopCheckbox.isSelected)
                            }
                        }
                        lastSelection = item
                    }
                }
                addEventFilter(KeyEvent.KEY_PRESSED) { it.consume() }
            }
            isDisable = true
        }.also { controls.add(it) }
        //endregion
        separator {
            vboxConstraints {
                marginTop = 12.0; marginBottom = 12.0
                marginLeft = 16.0; marginRight = 16.0
            }
        }
        //region 动画录制
        vbox {
            vboxConstraints {
                marginLeft = 16.0; marginRight = 16.0
            }
            label(recordTaskNameStr)
            hbox {
                vboxConstraints {
                    marginTop = 12.0
                }
                button("保存当前动画") {
                    minHeight = 30.0; minWidth = 128.0
                    action {
                        windowApp.postRunnable {
                            functions.recordCurrentAnimation()
                        }
                    }
                }
                button("保存所有动画") {
                    hboxConstraints { marginLeft = 16.0 }
                    minHeight = 30.0; minWidth = 128.0
                    action {
                        windowApp.postRunnable {
                            functions.recordAllAnimations()
                        }
                    }
                }
            }
            isDisable = true
        }.also { controls.add(it) }
        //endregion
        separator {
            vboxConstraints {
                marginTop = 12.0; marginBottom = 12.0
                marginLeft = 16.0; marginRight = 16.0
            }
        }
        //region 攻击动作分析
        vbox {
            vboxConstraints {
                marginLeft = 16.0
                marginRight = 16.0; marginBottom = 16.0
            }
            label("攻击动作事件时间轴：")
            tableview(actionTimestampList) {
                vboxConstraints { marginTop = 12.0 }
                minWidth = 272.0; maxHeight = 125.0
                isEditable = false; selectionModel = null
                readonlyColumn("动画名", ActionTimestamp::animationName) {
                    minWidth = 108.0; isSortable = false
                }
                column("action", ActionTimestamp::actionDurationProperty).apply {
                    minWidth = 80.0; isSortable = false
                }
                column("finish", ActionTimestamp::finishDurationProperty).apply {
                    minWidth = 80.0; isSortable = false
                }
            }
            hbox {
                alignment = Pos.CENTER_LEFT
                vboxConstraints { marginTop = 12.0 }
                button("批量导出") {
                    minWidth = 80.0; minHeight = 30.0
                    action {
                        isDisable = true
                        val folder = chooseDirectory("选择文件夹")
                        if (folder != null) {
                            runAsync {
                                functions.analyzeAll(folder)
                                isDisable = false
                            }
                        } else {
                            isDisable = false
                        }
                    }
                }
                label(analyzeTaskNameStr) {
                    hboxConstraints { marginLeft = 12.0 }
                }
            }
        }
        //endregion
    }
}

/**
 * Modified [tornadofx.isInsideRow]
 */
fun EventTarget.isValidRowModified(): Boolean {
    return when {
        this !is Node -> false
        this is TableColumnHeader -> false
        this is TableRow<*> -> !this.isEmpty
        this is TableView<*> || this is TreeTableRow<*>
                || this is TreeTableView<*> || this is ListCell<*> -> true
        this.parent != null -> this.parent.isValidRowModified()
        else -> false
    }
}

/**
 * Modified [tornadofx.onUserSelect]
 */
fun <T> ListView<T>.onUserSelectModified(clickCount: Int, action: (T) -> Unit) {
    val isSelected = { event: InputEvent ->
        event.target.isValidRowModified() && !selectionModel.isEmpty
    }
    addEventFilter(MouseEvent.MOUSE_CLICKED) { event ->
        if (event.clickCount == clickCount && isSelected(event)) {
            action(selectedItem!!)
        }
    }
    addEventFilter(KeyEvent.KEY_PRESSED) { it.consume() }
}

fun main() {
    launch<ALSDApp>()
}