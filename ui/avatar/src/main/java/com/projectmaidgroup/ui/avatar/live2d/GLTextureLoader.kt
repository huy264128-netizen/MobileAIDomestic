package com.projectmaidgroup.ui.avatar.live2d

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils

object GLTextureLoader {

    fun loadTextureFromAssets(context: Context, assetPath: String): Int {
        val bitmap = context.assets.open(assetPath).use { input ->
            BitmapFactory.decodeStream(input)
        } ?: error("Decode bitmap failed: $assetPath")

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val textureId = textures[0]
        require(textureId != 0) { "glGenTextures failed for $assetPath" }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        bitmap.recycle()

        return textureId
    }

    fun deleteTexture(textureId: Int) {
        if (textureId == 0) return
        GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
    }
}