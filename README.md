# 碧蓝航线 SD小人动画浏览器
浏览、录制碧蓝航线SD小人的动画以及分析攻击动作前摇

**<i>Issues and PRs in English are also welcome.</i>**

## 运行要求
 - JDK 11
 - JavaFX SDK. Releases压缩包里自带一套JavaFX组件，获取自官网

解压后双击`launch.bat`即可。运行后会出现一个黑console终端以及一左一右两个窗口

一并打包了JavaFX和Lwjgl框架所以十分的大，当然也可以自己构建
## 使用
### 动画浏览
![image](https://github.com/Deficuet/Azurlane-SDViewer/assets/36525579/56cf562f-3b67-41d4-aa42-9ebae1c86a5b)

点击`导入文件`后会自动加载并将SD小人显示在右侧窗口中。需要的文件位于游戏目录下`AssetBundles/char`，该文件夹存放所有SD小人的文件
 - **目前不支持查看 确捷 默认立绘 的小人，因为材质图片文件与模型不在同一个文件里，目前尚无解决办法，欢迎PR**

之后将会在动画列表内显示所有可用的动画，单击即可切换动画。加载完成后默认使用第一个动画
### 动画录制保存
![image](https://github.com/Deficuet/Azurlane-SDViewer/assets/36525579/b98e3395-7d25-4c4d-8419-52894d9b48ae)

可选录制当前正在显示的动画或者全部动画。输出位于同目录`output`文件夹下与导入文件同名的文件夹内。输出格式为`30 fps`的`.gif`文件，文件尺寸通常不超过`3 MB`；动作幅度小的话通常不超过`1 MB`

**录制流程**
 - 重置缩放及速度，快速过一遍动画以避免任何动画间过渡动作
 - 以`0.033 s`的时间步长再过一遍动画，测量能够容纳所有帧的最小图片尺寸
   - Lwjgl窗口的帧率通常是60，所以看起来可能像二倍速
 - 重新设置右侧窗口大小，以`0.033 s`的时间步长最后过一遍动画并录制。录制完毕后输出GIF文件。一共过三次动画
 - 所有任务完成后将恢复窗口至录制前的样子

### 攻击动作事件时间轴
![image](https://github.com/Deficuet/Azurlane-SDViewer/assets/36525579/73da4b39-eb69-485b-9902-b81e0647937d)

在导入文件之后将自动寻找四种列出的攻击动画，如果有就会分析一个动画循环内*第一个*`action`和*第一个*`finish`两个事件的时间点。其中从循环开始至`action`触发时间即为该攻击动画的前摇

**批量导出**

点击该按钮后，选择一文件夹，该文件夹内应当只包含若干游戏目录`AssetBundles/char`下的文件。选择完毕后将自动加载文件夹下所有文件（不加载模型至右侧窗口）并导出攻击前摇。输出格式为`JSON`文件，输出至`output`文件夹下**与导入文件夹同名**的JSON文件
