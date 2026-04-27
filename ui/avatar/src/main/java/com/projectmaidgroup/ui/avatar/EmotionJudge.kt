package com.projectmaidgroup.ui.avatar

/**
 * 简单关键词情绪判断器。
 *
 * 这个版本不依赖大模型，适合你先把 Live2D 动作链路跑通。
 * 后续你可以把它替换成真正的 AI 情绪判断。
 */
object EmotionJudge {

    fun guess(text: String): AvatarEmotion {
        val t = text.lowercase()

        return when {
            hasAny(t, listOf("哈哈", "开心", "太好了", "棒", "厉害", "恭喜", "喜欢", "谢谢", "爱你")) ->
                AvatarEmotion.HAPPY

            hasAny(t, listOf("抱歉", "对不起", "难过", "遗憾", "伤心", "心疼", "没关系")) ->
                AvatarEmotion.SAD

            hasAny(t, listOf("不可以", "不能这样", "危险", "警告", "严重", "生气", "过分")) ->
                AvatarEmotion.ANGRY

            hasAny(t, listOf("诶", "欸", "哇", "竟然", "真的假的", "惊讶", "没想到")) ->
                AvatarEmotion.SURPRISED

            hasAny(t, listOf("让我想想", "分析一下", "思考", "推理", "判断", "可能是")) ->
                AvatarEmotion.THINKING

            hasAny(t, listOf("不太确定", "没听懂", "疑惑", "奇怪", "为什么")) ->
                AvatarEmotion.CONFUSED

            hasAny(t, listOf("害羞", "不好意思", "嘿嘿", "夸我", "脸红")) ->
                AvatarEmotion.SHY

            hasAny(t, listOf("冲", "太强了", "太棒了", "激动", "兴奋")) ->
                AvatarEmotion.EXCITED

            else ->
                AvatarEmotion.NEUTRAL
        }
    }

    private fun hasAny(text: String, words: List<String>): Boolean{
        return words.any { text.contains(it) }
    }
}