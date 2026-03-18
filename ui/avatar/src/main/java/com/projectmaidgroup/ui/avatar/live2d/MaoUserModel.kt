package com.projectmaidgroup.ui.avatar.live2d

import android.content.Context
import android.opengl.GLES20
import android.util.Log
import com.live2d.sdk.cubism.framework.CubismModelSettingJson
import com.live2d.sdk.cubism.framework.math.CubismMatrix44
import com.live2d.sdk.cubism.framework.math.CubismModelMatrix
import com.live2d.sdk.cubism.framework.model.CubismUserModel
import com.live2d.sdk.cubism.framework.rendering.android.CubismRendererAndroid

class MaoUserModel(
    private val context: Context
) : CubismUserModel() {

    private val loadedTextureIds = mutableListOf<Int>()
    private var androidRenderer: CubismRendererAndroid? = null

    fun load(modelDir: String, modelJson: String) {
        Log.d("MaoUserModel", "load start: $modelDir/$modelJson")

        val settingBytes = AssetUtil.readBytes(context, "$modelDir/$modelJson")
        val setting = CubismModelSettingJson(settingBytes)

        val mocName = setting.getModelFileName()
        Log.d("MaoUserModel", "moc = $mocName")

        val mocBytes = AssetUtil.readBytes(context, "$modelDir/$mocName")
        loadModel(mocBytes)

        if (model == null) {
            throw IllegalStateException(
                "model == null after loadModel(). " +
                        "优先怀疑：Live2D Framework、Live2DCubismCore.aar、Mao.moc3 版本不匹配。"
            )
        }

        Log.d("MaoUserModel", "model created: canvas=${model.canvasWidth} x ${model.canvasHeight}")

        modelMatrix = CubismModelMatrix.create(model.canvasWidth, model.canvasHeight).apply {
            setWidth(1.5f)
            setCenterPosition(0.75f, 0.65f)
        }

        val renderer = CubismRendererAndroid()
        setupRenderer(renderer)
        androidRenderer = renderer
        Log.d("MaoUserModel", "renderer setup finished")

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
        Log.d("MaoUserModel", "load finished")
    }

    fun update(@Suppress("UNUSED_PARAMETER") deltaSec: Float) {
        if (model == null) return
        model.update()
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

        //projection.multiplyByMatrix(modelMatrix)

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