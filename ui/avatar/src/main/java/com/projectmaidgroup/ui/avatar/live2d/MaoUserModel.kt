package com.projectmaidgroup.ui.avatar.live2d

import android.content.Context
import android.opengl.GLES20
import android.util.Log
import com.live2d.sdk.cubism.framework.CubismModelSettingJson
import com.live2d.sdk.cubism.framework.math.CubismMatrix44
import com.live2d.sdk.cubism.framework.math.CubismModelMatrix
import com.live2d.sdk.cubism.framework.model.CubismUserModel
import com.live2d.sdk.cubism.framework.motion.CubismMotion
import com.live2d.sdk.cubism.framework.rendering.android.CubismRendererAndroid
import kotlin.random.Random
import com.projectmaidgroup.ui.avatar.AvatarEmotion
import com.projectmaidgroup.ui.avatar.AvatarEmotionMapper
import com.projectmaidgroup.ui.avatar.Live2DMotionCommand
import org.json.JSONObject

class MaoUserModel(
    private val context: Context
) : CubismUserModel() {

    private val loadedTextureIds = mutableListOf<Int>()
    private var androidRenderer: CubismRendererAndroid? = null

    private val motionGroups = linkedMapOf<String, LinkedHashMap<String, CubismMotion>>()
    private val idleMotions = linkedMapOf<String, CubismMotion>()
    private val tapBodyMotions = linkedMapOf<String, CubismMotion>()

    private var currentMotionStarted = false

    fun load(modelDir: String, modelJson: String) {
        Log.d("MaoUserModel", "load start: $modelDir/$modelJson")

        val settingBytes = AssetUtil.readBytes(context, "$modelDir/$modelJson")
        val setting = CubismModelSettingJson(settingBytes)

        val mocName = setting.getModelFileName()
        val mocBytes = AssetUtil.readBytes(context, "$modelDir/$mocName")
        loadModel(mocBytes)

        if (model == null) {
            throw IllegalStateException("model == null after loadModel()")
        }

        loadPhysicsIfExists(setting, modelDir)
        loadPoseIfExists(setting, modelDir)

        loadAllMotionGroups(
            setting = setting,
            modelDir = modelDir,
            modelJson = modelJson
        )

        idleMotions.clear()
        tapBodyMotions.clear()

        motionGroups["Idle"]?.let { idleMotions.putAll(it) }
        motionGroups["TapBody"]?.let { tapBodyMotions.putAll(it) }

        modelMatrix = CubismModelMatrix.create(model.canvasWidth, model.canvasHeight).apply {
            setWidth(2.6f)
            setCenterPosition(1.3f, 1.5f)
        }

        val renderer = CubismRendererAndroid()
        setupRenderer(renderer)
        renderer.isPremultipliedAlpha(true)
        androidRenderer = renderer

        val textureCount = setting.getTextureCount()
        for (i in 0 until textureCount) {
            val texRelPath = setting.getTextureFileName(i)
            val fullTexPath = "$modelDir/$texRelPath"
            val textureId = GLTextureLoader.loadTextureFromAssets(context, fullTexPath)
            loadedTextureIds += textureId
            renderer.bindTexture(i, textureId)
        }

        model.update()
        startNatureMotionIfExists()
        Log.d("MaoUserModel", "load finished")
    }

    private fun loadPhysicsIfExists(setting: CubismModelSettingJson, modelDir: String) {
        try {
            val physicsFile = setting.getPhysicsFileName()
            if (!physicsFile.isNullOrEmpty()) {
                val physicsBytes = AssetUtil.readBytes(context, "$modelDir/$physicsFile")
                loadPhysics(physicsBytes)
            }
        } catch (t: Throwable) {
            Log.w("MaoUserModel", "loadPhysicsIfExists failed", t)
        }
    }

    private fun loadPoseIfExists(setting: CubismModelSettingJson, modelDir: String) {
        try {
            val poseFile = setting.getPoseFileName()
            if (!poseFile.isNullOrEmpty()) {
                val poseBytes = AssetUtil.readBytes(context, "$modelDir/$poseFile")
                loadPose(poseBytes)
            }
        } catch (t: Throwable) {
            Log.w("MaoUserModel", "loadPoseIfExists failed", t)
        }
    }

    private fun loadMotionGroup(
        setting: CubismModelSettingJson,
        modelDir: String,
        group: String,
        loop: Boolean,
        target: LinkedHashMap<String, CubismMotion>
    ) {
        try {
            val motionCount = setting.getMotionCount(group)

            for (i in 0 until motionCount) {
                val motionFile = setting.getMotionFileName(group, i)
                val motionBytes = AssetUtil.readBytes(context, "$modelDir/$motionFile")
                val motion = loadMotion(motionBytes, null, null, false)

                if (motion != null) {
                    motion.setLoop(loop)
                    motion.setLoopFadeIn(loop)

                    val fadeIn = setting.getMotionFadeInTimeValue(group, i)
                    if (fadeIn >= 0f) {
                        motion.setFadeInTime(fadeIn)
                    }

                    val fadeOut = setting.getMotionFadeOutTimeValue(group, i)
                    if (fadeOut >= 0f) {
                        motion.setFadeOutTime(fadeOut)
                    }

                    target[motionFile] = motion
                }
            }
        } catch (t: Throwable) {
            Log.w("MaoUserModel", "loadMotionGroup failed: $group", t)
        }
    }

    private fun startNatureMotionIfExists() {
        if (idleMotions.isEmpty()) return

        val natureEntry =
            idleMotions.entries.firstOrNull { it.key.contains("nature", ignoreCase = true) }
                ?: idleMotions.entries.firstOrNull()
                ?: return

        try {
            motionManager.stopAllMotions()
            motionManager.startMotionPriority(natureEntry.value, 1)
            currentMotionStarted = true
        } catch (t: Throwable) {
            currentMotionStarted = false
            Log.e("MaoUserModel", "startNatureMotionIfExists failed", t)
        }
    }

    fun playTapMotion() {
        playMotion(
            Live2DMotionCommand(
                emotion = AvatarEmotion.NEUTRAL,
                group = "TapBody"
            )
        )
    }

    fun playRandomReplyMotion() {
        playMotion(
            Live2DMotionCommand(
                emotion = AvatarEmotion.HAPPY,
                group = "TapBody"
            )
        )
    }

    private fun playRandomMotionFrom(source: LinkedHashMap<String, CubismMotion>) {
        if (source.isEmpty()) {
            startNatureMotionIfExists()
            return
        }

        val motion = source.values.toList()[Random.nextInt(source.size)]

        try {
            motionManager.stopAllMotions()
            motionManager.startMotionPriority(motion, 3)
            currentMotionStarted = true
        } catch (t: Throwable) {
            Log.e("MaoUserModel", "playRandomMotionFrom failed", t)
        }
    }

    fun update(deltaSec: Float) {
        if (model == null) return

        try { model.loadParameters() } catch (_: Throwable) {}
        try { motionManager.updateMotion(model, deltaSec) } catch (_: Throwable) {}
        try { expressionManager.updateMotion(model, deltaSec) } catch (_: Throwable) {}
        try { physics?.evaluate(model, deltaSec) } catch (_: Throwable) {}
        try { pose?.updateParameters(model, deltaSec) } catch (_: Throwable) {}

        model.update()

        try {
            if (currentMotionStarted && motionManager.isFinished()) {
                currentMotionStarted = false
                startNatureMotionIfExists()
            }
        } catch (_: Throwable) {}
    }

    fun draw(viewWidth: Int, viewHeight: Int) {
        val renderer = androidRenderer ?: return
        if (viewWidth <= 0 || viewHeight <= 0) return

        val projection = CubismMatrix44.create()
        val aspect = viewWidth.toFloat() / viewHeight.toFloat()

        if (aspect > 1f) {
            projection.scaleRelative(1f / aspect, 1f)
        } else {
            projection.scaleRelative(1f, aspect)
        }

        projection.multiplyByMatrix(modelMatrix)

        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        renderer.mvpMatrix = projection
        renderer.drawModel()
    }
    private fun loadAllMotionGroups(
        setting: CubismModelSettingJson,
        modelDir: String,
        modelJson: String
    ) {
        motionGroups.clear()

        val modelJsonText = try {
            context.assets.open("$modelDir/$modelJson")
                .bufferedReader()
                .use { it.readText() }
        } catch (t: Throwable) {
            Log.w("MaoUserModel", "read model json failed", t)
            null
        } ?: return

        val motionsJson = try {
            JSONObject(modelJsonText)
                .optJSONObject("FileReferences")
                ?.optJSONObject("Motions")
        } catch (t: Throwable) {
            Log.w("MaoUserModel", "parse motions failed", t)
            null
        } ?: return

        val keys = motionsJson.keys()

        while (keys.hasNext()) {
            val group = keys.next()
            val target = linkedMapOf<String, CubismMotion>()
            val loop = group.equals("Idle", ignoreCase = true)

            loadMotionGroup(
                setting = setting,
                modelDir = modelDir,
                group = group,
                loop = loop,
                target = target
            )

            if (target.isNotEmpty()) {
                motionGroups[group] = target
            }
        }
    }
    fun release() {
        loadedTextureIds.forEach { GLTextureLoader.deleteTexture(it) }
        loadedTextureIds.clear()
        androidRenderer = null
        delete()
    }
    fun playEmotion(emotion: AvatarEmotion) {
        playMotion(AvatarEmotionMapper.toMotionCommand(emotion))
    }

    fun playMotion(command: Live2DMotionCommand) {
        val realCommand =
            if (command.group == null && command.motionFile == null) {
                AvatarEmotionMapper.toMotionCommand(command.emotion)
            } else {
                command
            }

        val targetMotion = findMotion(realCommand)

        if (targetMotion == null) {
            Log.w("MaoUserModel", "motion not found: $realCommand")
            playRandomMotionFrom(tapBodyMotions)
            return
        }

        startMotion(targetMotion, realCommand.priority)
    }

    private fun findMotion(command: Live2DMotionCommand): CubismMotion? {
        val groupName = command.group
        val motionFile = command.motionFile

        if (groupName != null && motionFile != null) {
            return motionGroups[groupName]?.get(motionFile)
        }

        if (groupName != null) {
            val group = motionGroups[groupName] ?: return null
            if (group.isEmpty()) return null
            return group.values.toList()[Random.nextInt(group.size)]
        }

        if (motionFile != null) {
            motionGroups.values.forEach { group ->
                group[motionFile]?.let { return it }
            }
        }

        return null
    }

    private fun startMotion(motion: CubismMotion, priority: Int) {
        try {
            motionManager.stopAllMotions()
            motionManager.startMotionPriority(motion, priority)
            currentMotionStarted = true
        } catch (t: Throwable) {
            Log.e("MaoUserModel", "startMotion failed", t)
        }
    }

    fun getAvailableMotionNames(): Map<String, List<String>> {
        return motionGroups.mapValues { entry ->
            entry.value.keys.toList()
        }
    }
}