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
                "Live2D model == null after loadModel(). " +
                        "通常是 Cubism Framework 和 Live2DCubismCore.aar 版本不匹配，" +
                        "或者 moc3 文件与当前 Core 不兼容。"
            )
        }

        modelMatrix = CubismModelMatrix.create(model.canvasWidth, model.canvasHeight).apply {
            setWidth(1.8f)
            setCenterPosition(0.0f, 0.0f)
        }

        val androidRenderer = CubismRendererAndroid()
        setupRenderer(androidRenderer)

        val textureCount = setting.getTextureCount()
        Log.d("MaoUserModel", "textureCount = $textureCount")

        for (i in 0 until textureCount) {
            val texRelPath = setting.getTextureFileName(i)
            val fullTexPath = "$modelDir/$texRelPath"
            Log.d("MaoUserModel", "tex[$i] = $fullTexPath")

            val textureId = GLTextureLoader.loadTextureFromAssets(context, fullTexPath)
            loadedTextureIds += textureId
            androidRenderer.bindTexture(i, textureId)
        }

        model.update()
        Log.d("MaoUserModel", "load finished")
    }

    fun update(@Suppress("UNUSED_PARAMETER") deltaSec: Float) {
        if (model == null) return
        model.update()
    }

    fun draw(viewWidth: Int, viewHeight: Int) {
        val r = renderer ?: run {
            Log.e("MaoUserModel", "draw skipped: renderer == null")
            return
        }

        if (viewWidth <= 0 || viewHeight <= 0) {
            Log.e("MaoUserModel", "draw skipped: invalid surface size = ${viewWidth}x${viewHeight}")
            return
        }

        val projection = CubismMatrix44.create()

        if (viewWidth > viewHeight) {
            projection.scaleRelative(viewHeight.toFloat() / viewWidth.toFloat(), 1.0f)
        } else {
            projection.scaleRelative(1.0f, viewWidth.toFloat() / viewHeight.toFloat())
        }

        projection.multiplyByMatrix(modelMatrix)

        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        r.mvpMatrix = projection
        r.drawModel()
    }

    fun release() {
        loadedTextureIds.forEach { GLTextureLoader.deleteTexture(it) }
        loadedTextureIds.clear()
        delete()
    }
}