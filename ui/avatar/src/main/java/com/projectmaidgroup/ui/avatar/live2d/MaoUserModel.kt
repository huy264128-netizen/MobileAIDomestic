package com.projectmaidgroup.ui.avatar.live2d

import android.content.Context
import android.opengl.GLES20
import android.util.Log
import com.live2d.sdk.cubism.framework.CubismModelSettingJson
import com.live2d.sdk.cubism.framework.math.CubismMatrix44
import com.live2d.sdk.cubism.framework.math.CubismModelMatrix
import com.live2d.sdk.cubism.framework.model.CubismUserModel
import com.live2d.sdk.cubism.framework.motion.CubismExpressionMotion
import com.live2d.sdk.cubism.framework.motion.CubismMotion
import com.live2d.sdk.cubism.framework.rendering.android.CubismRendererAndroid
import com.projectmaidgroup.ui.avatar.AvatarAction
import kotlin.random.Random

/**
 * Mao 模型的真正 Live2D 控制类。
 *
 * 它只负责：
 * 1. 加载模型资源
 * 2. 保存动作组
 * 3. 保存表情
 * 4. 根据外部传入的 AvatarAction 播放动作和表情
 *
 * 注意：
 * 这个类运行在 OpenGL 渲染线程里。
 * 外部不要直接跨线程调用它，应该通过 Live2DRenderer.queueAction(...) 调用。
 */
class MaoUserModel(
    private val context: Context
) : CubismUserModel() {

    private val loadedTextureIds = mutableListOf<Int>()
    private var androidRenderer: CubismRendererAndroid? = null

    /**
     * 所有动作组。
     *
     * 结构：
     * motionGroups["TapBody"]["motions/mtn_02.motion3.json"] = CubismMotion
     */
    private val motionGroups = linkedMapOf<String, LinkedHashMap<String, CubismMotion>>()

    /**
     * 所有表情。
     *
     * 结构：
     * expressions["exp_01"] = CubismExpressionMotion
     */
    private val expressions = linkedMapOf<String, CubismExpressionMotion>()

    private var currentMotionStarted = false

    fun load(modelDir: String, modelJson: String) {
        Log.d(TAG, "load start: $modelDir/$modelJson")

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

        loadAllExpressions(setting, modelDir)
        loadKnownMotionGroups(setting, modelDir)

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
        startIdleMotion()

        Log.d(TAG, "load finished. groups=${motionGroups.keys}, expressions=${expressions.keys}")
    }

    private fun loadPhysicsIfExists(setting: CubismModelSettingJson, modelDir: String) {
        try {
            val physicsFile = setting.getPhysicsFileName()
            if (!physicsFile.isNullOrEmpty()) {
                val physicsBytes = AssetUtil.readBytes(context, "$modelDir/$physicsFile")
                loadPhysics(physicsBytes)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "loadPhysicsIfExists failed", t)
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
            Log.w(TAG, "loadPoseIfExists failed", t)
        }
    }

    /**
     * 加载 model3.json 中的 Expressions。
     *
     * 你的 Mao.model3.json 里现在有 exp_01 到 exp_08。
     */
    private fun loadAllExpressions(setting: CubismModelSettingJson, modelDir: String) {
        try {
            val count = setting.getExpressionCount()
            for (i in 0 until count) {
                val name = setting.getExpressionName(i)
                val file = setting.getExpressionFileName(i)

                if (name.isNullOrEmpty() || file.isNullOrEmpty()) {
                    continue
                }

                val bytes = AssetUtil.readBytes(context, "$modelDir/$file")
                val expression = loadExpression(bytes)
                if (expression != null) {
                    expressions[name] = expression
                    Log.d(TAG, "expression loaded: $name -> $file")
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "loadAllExpressions failed", t)
        }
    }

    /**
     * 加载动作组。
     *
     * 这里先写一个 knownGroups，是为了兼容你当前 SDK 的 CubismModelSettingJson。
     * 有些版本能枚举所有 motion group，有些项目里没有直接用这个能力。
     *
     * 你现在 model3.json 只有 Idle 和 TapBody。
     * 以后你加 Happy、Sad、Angry 动作组时，把名字加进这个列表即可。
     */
    private fun loadKnownMotionGroups(setting: CubismModelSettingJson, modelDir: String) {
        val knownGroups = listOf(
            "Idle",
            "TapBody",
            "Happy",
            "Sad",
            "Angry",
            "Shy",
            "Surprised",
            "Thinking",
            "Confused",
            "Excited"
        )

        for (group in knownGroups) {
            val loop = group == "Idle"
            loadMotionGroup(setting, modelDir, group, loop)
        }
    }

    private fun loadMotionGroup(
        setting: CubismModelSettingJson,
        modelDir: String,
        group: String,
        loop: Boolean
    ) {
        try {
            val motionCount = setting.getMotionCount(group)
            if (motionCount <= 0) return

            val groupMap = linkedMapOf<String, CubismMotion>()

            for (i in 0 until motionCount) {
                val motionFile = setting.getMotionFileName(group, i)
                if (motionFile.isNullOrEmpty()) continue

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

                    groupMap[motionFile] = motion
                    Log.d(TAG, "motion loaded: $group -> $motionFile")
                }
            }

            if (groupMap.isNotEmpty()) {
                motionGroups[group] = groupMap
            }
        } catch (t: Throwable) {
            Log.w(TAG, "loadMotionGroup failed: $group", t)
        }
    }

    /**
     * 默认待机动作。
     */
    private fun startIdleMotion() {
        val idle = motionGroups["Idle"] ?: return
        val entry = idle.entries.firstOrNull() ?: return

        try {
            motionManager.stopAllMotions()
            motionManager.startMotionPriority(entry.value, 1)
            currentMotionStarted = true
        } catch (t: Throwable) {
            currentMotionStarted = false
            Log.e(TAG, "startIdleMotion failed", t)
        }
    }

    fun playTapMotion() {
        playMotion(group = "TapBody", motionName = null, priority = 3, force = true)
    }

    fun playRandomReplyMotion() {
        playMotion(group = "TapBody", motionName = null, priority = 3, force = true)
    }

    /**
     * 外部最终统一调用这个方法。
     */
    fun playAction(action: AvatarAction) {
        try {
            action.expressionName?.let { playExpression(it) }

            if (action.motionGroup != null) {
                playMotion(
                    group = action.motionGroup,
                    motionName = action.motionName,
                    priority = action.priority,
                    force = action.force
                )
            }
        } catch (t: Throwable) {
            Log.e(TAG, "playAction failed: $action", t)
        }
    }

    /**
     * 播放表情。
     */
    fun playExpression(expressionName: String) {
        val expression = expressions[expressionName]
        if (expression == null) {
            Log.w(TAG, "expression not found: $expressionName, available=${expressions.keys}")
            return
        }

        try {
            expressionManager.startMotionPriority(expression, 3)
            Log.d(TAG, "playExpression: $expressionName")
        } catch (t: Throwable) {
            Log.e(TAG, "playExpression failed: $expressionName", t)
        }
    }

    /**
     * 播放动作。
     *
     * group:
     *   例如 TapBody
     *
     * motionName:
     *   如果为 null，从 group 里随机取一个。
     *   如果不为 null，就播放指定文件。
     */
    fun playMotion(
        group: String,
        motionName: String? = null,
        priority: Int = 3,
        force: Boolean = true
    ) {
        val groupMap = motionGroups[group]

        if (groupMap.isNullOrEmpty()) {
            Log.w(TAG, "motion group not found or empty: $group, available=${motionGroups.keys}")
            if (group != "Idle") {
                startIdleMotion()
            }
            return
        }

        val motion = if (motionName != null) {
            groupMap[motionName]
        } else {
            groupMap.values.toList()[Random.nextInt(groupMap.size)]
        }

        if (motion == null) {
            Log.w(TAG, "motion not found: group=$group, motionName=$motionName, available=${groupMap.keys}")
            return
        }

        try {
            if (force) {
                motionManager.stopAllMotions()
            }

            motionManager.startMotionPriority(motion, priority)
            currentMotionStarted = true

            Log.d(TAG, "playMotion: group=$group, motion=$motionName, priority=$priority")
        } catch (t: Throwable) {
            Log.e(TAG, "playMotion failed: group=$group, motion=$motionName", t)
        }
    }

    fun update(deltaSec: Float) {
        if (model == null) return

        try {
            model.loadParameters()
        } catch (_: Throwable) {
        }

        try {
            motionManager.updateMotion(model, deltaSec)
        } catch (_: Throwable) {
        }

        try {
            expressionManager.updateMotion(model, deltaSec)
        } catch (_: Throwable) {
        }

        try {
            physics?.evaluate(model, deltaSec)
        } catch (_: Throwable) {
        }

        try {
            pose?.updateParameters(model, deltaSec)
        } catch (_: Throwable) {
        }

        model.update()

        try {
            if (currentMotionStarted && motionManager.isFinished()) {
                currentMotionStarted = false
                startIdleMotion()
            }
        } catch (_: Throwable) {
        }
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

    fun release() {
        loadedTextureIds.forEach { GLTextureLoader.deleteTexture(it) }
        loadedTextureIds.clear()
        androidRenderer = null
        delete()
    }

    companion object {
        private const val TAG = "MaoUserModel"
    }
}