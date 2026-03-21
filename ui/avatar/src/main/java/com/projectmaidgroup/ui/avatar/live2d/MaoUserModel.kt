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

class MaoUserModel(
    private val context: Context
) : CubismUserModel() {

    private val loadedTextureIds = mutableListOf<Int>()
    private var androidRenderer: CubismRendererAndroid? = null

    private val idleMotions = linkedMapOf<String, CubismMotion>()
    private var currentMotionStarted = false

    fun load(modelDir: String, modelJson: String) {
        Log.d("MaoUserModel", "load start: $modelDir/$modelJson")

        val settingBytes = AssetUtil.readBytes(context, "$modelDir/$modelJson")
        val setting = CubismModelSettingJson(settingBytes)

        // 1) moc3
        val mocName = setting.getModelFileName()
        Log.d("MaoUserModel", "moc = $mocName")
        val mocBytes = AssetUtil.readBytes(context, "$modelDir/$mocName")
        loadModel(mocBytes)

        if (model == null) {
            throw IllegalStateException("model == null after loadModel()")
        }

        Log.d("MaoUserModel", "model created: canvas=${model.canvasWidth} x ${model.canvasHeight}")

        // 2) physics
        loadPhysicsIfExists(setting, modelDir)

        // 3) pose
        loadPoseIfExists(setting, modelDir)

        // 4) 先不要加载 expression，排除脸部异常
        // loadExpressions(setting, modelDir)

        // 5) idle motions
        loadIdleMotions(setting, modelDir)

        // 6) matrix
        modelMatrix = CubismModelMatrix.create(model.canvasWidth, model.canvasHeight).apply {
            setWidth(2.6f)
            setCenterPosition(1.3f, 1.5f)
        }

        // 7) renderer
        val renderer = CubismRendererAndroid()
        setupRenderer(renderer)
        renderer.isPremultipliedAlpha(true)
        androidRenderer = renderer
        Log.d("MaoUserModel", "renderer setup finished")

        // 8) textures
        val textureCount = setting.getTextureCount()
        Log.d("MaoUserModel", "textureCount = $textureCount")

        for (i in 0 until textureCount) {
            val texRelPath = setting.getTextureFileName(i)
            val fullTexPath = "$modelDir/$texRelPath"
            Log.d("MaoUserModel", "tex[$i] = $fullTexPath")

            val textureId = GLTextureLoader.loadTextureFromAssets(context, fullTexPath)
            loadedTextureIds += textureId
            renderer.bindTexture(i, textureId)
        }

        model.update()

        // 9) 优先启动 nature 动画
        startNatureMotionIfExists()

        Log.d("MaoUserModel", "load finished")
    }

    private fun loadPhysicsIfExists(setting: CubismModelSettingJson, modelDir: String) {
        try {
            val physicsFile = setting.getPhysicsFileName()
            if (!physicsFile.isNullOrEmpty()) {
                val physicsBytes = AssetUtil.readBytes(context, "$modelDir/$physicsFile")
                loadPhysics(physicsBytes)
                Log.d("MaoUserModel", "physics loaded: $physicsFile")
            } else {
                Log.d("MaoUserModel", "physics not found")
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
                Log.d("MaoUserModel", "pose loaded: $poseFile")
            } else {
                Log.d("MaoUserModel", "pose not found")
            }
        } catch (t: Throwable) {
            Log.w("MaoUserModel", "loadPoseIfExists failed", t)
        }
    }

    private fun loadIdleMotions(setting: CubismModelSettingJson, modelDir: String) {
        try {
            val group = "Idle"
            val motionCount = setting.getMotionCount(group)
            Log.d("MaoUserModel", "Idle motionCount = $motionCount")

            for (i in 0 until motionCount) {
                val motionFile = setting.getMotionFileName(group, i)
                val motionBytes = AssetUtil.readBytes(context, "$modelDir/$motionFile")

                val motion = loadMotion(
                    motionBytes,
                    null,
                    null,
                    false
                )

                if (motion != null) {
                    try {
                        motion.setLoop(true)
                        motion.setLoopFadeIn(true)

                        val fadeIn = setting.getMotionFadeInTimeValue(group, i)
                        if (fadeIn >= 0f) motion.setFadeInTime(fadeIn)

                        val fadeOut = setting.getMotionFadeOutTimeValue(group, i)
                        if (fadeOut >= 0f) motion.setFadeOutTime(fadeOut)
                    } catch (t: Throwable) {
                        Log.w("MaoUserModel", "motion option setup failed: $motionFile", t)
                    }

                    idleMotions[motionFile] = motion
                    Log.d("MaoUserModel", "idle motion loaded: $motionFile")
                }
            }
        } catch (t: Throwable) {
            Log.w("MaoUserModel", "loadIdleMotions failed", t)
        }
    }

    private fun startNatureMotionIfExists() {
        if (idleMotions.isEmpty()) {
            Log.w("MaoUserModel", "no idle motions loaded")
            return
        }

        val natureEntry = idleMotions.entries.firstOrNull {
            it.key.contains("nature", ignoreCase = true)
        } ?: idleMotions.entries.firstOrNull()

        if (natureEntry == null) {
            Log.w("MaoUserModel", "no nature/idle motion found")
            return
        }

        try {
            motionManager.stopAllMotions()
            motionManager.startMotionPriority(natureEntry.value, 3)
            currentMotionStarted = true
            Log.d("MaoUserModel", "started idle motion: ${natureEntry.key}")
        } catch (t: Throwable) {
            currentMotionStarted = false
            Log.e("MaoUserModel", "startNatureMotionIfExists failed", t)
        }
    }

    fun update(deltaSec: Float) {
        if (model == null) return

        try {
            model.loadParameters()
        } catch (t: Throwable) {
            Log.w("MaoUserModel", "model.loadParameters failed", t)
        }

        try {
            motionManager.updateMotion(model, deltaSec)
        } catch (t: Throwable) {
            Log.w("MaoUserModel", "motionManager.updateMotion failed", t)
        }

        try {
            expressionManager.updateMotion(model, deltaSec)
        } catch (t: Throwable) {
            Log.w("MaoUserModel", "expressionManager.updateMotion failed", t)
        }

        try {
            physics?.evaluate(model, deltaSec)
        } catch (t: Throwable) {
            Log.w("MaoUserModel", "physics evaluate failed", t)
        }

        try {
            pose?.updateParameters(model, deltaSec)
        } catch (t: Throwable) {
            Log.w("MaoUserModel", "pose update failed", t)
        }

        model.update()

        try {
            if (currentMotionStarted && motionManager.isFinished()) {
                currentMotionStarted = false
                startNatureMotionIfExists()
            }
        } catch (t: Throwable) {
            Log.w("MaoUserModel", "motion replay check failed", t)
        }
    }

    fun draw(viewWidth: Int, viewHeight: Int) {
        val renderer = androidRenderer ?: run {
            Log.e("MaoUserModel", "draw skipped: renderer == null")
            return
        }

        if (viewWidth <= 0 || viewHeight <= 0) {
            Log.e("MaoUserModel", "draw skipped: invalid size = ${viewWidth}x${viewHeight}")
            return
        }

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