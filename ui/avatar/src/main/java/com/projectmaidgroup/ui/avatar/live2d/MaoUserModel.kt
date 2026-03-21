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

class MaoUserModel(
    private val context: Context
) : CubismUserModel() {

    private val loadedTextureIds = mutableListOf<Int>()
    private var androidRenderer: CubismRendererAndroid? = null

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

        loadMotionGroup(setting, modelDir, "Idle", loop = true, target = idleMotions)
        loadMotionGroup(setting, modelDir, "TapBody", loop = false, target = tapBodyMotions)

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
                    if (fadeIn >= 0f) motion.setFadeInTime(fadeIn)

                    val fadeOut = setting.getMotionFadeOutTimeValue(group, i)
                    if (fadeOut >= 0f) motion.setFadeOutTime(fadeOut)

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
        playRandomMotionFrom(tapBodyMotions)
    }

    fun playRandomReplyMotion() {
        playRandomMotionFrom(tapBodyMotions)
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

    fun release() {
        loadedTextureIds.forEach { GLTextureLoader.deleteTexture(it) }
        loadedTextureIds.clear()
        androidRenderer = null
        delete()
    }
}