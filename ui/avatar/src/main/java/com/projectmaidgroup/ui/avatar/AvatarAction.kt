package com.projectmaidgroup.ui.avatar

/**
 * AI 对话层能理解的“情绪”。
 *
 * 你后续让大模型判断情绪时，不要让大模型直接输出 mtn_02、exp_03 这种资源名。
 * 大模型只需要输出 HAPPY、SAD、ANGRY 这种语义。
 *
 * Live2D 层负责把语义翻译成具体动作和表情。
 */
enum class AvatarEmotion {
    NEUTRAL,    // 普通、默认
    HAPPY,      // 开心、夸奖、感谢、积极
    SAD,        // 难过、抱歉、安慰
    ANGRY,      // 生气、不满、严肃提醒
    SHY,        // 害羞、被夸、撒娇
    SURPRISED,  // 惊讶、突然发现
    THINKING,   // 思考、分析、等待
    CONFUSED,   // 疑惑、没听懂
    EXCITED     // 兴奋、强烈开心
}

/**
 * Live2D 最终执行的动作命令。
 *
 * motionGroup:
 *   对应 Mao.model3.json 里的 Motions 分组名。
 *   你现在有 Idle 和 TapBody。
 *
 * motionName:
 *   可以指定具体 motion 文件名，例如 motions/mtn_02.motion3.json。
 *   如果传 null，就从 motionGroup 中随机取一个。
 *
 * expressionName:
 *   对应 Mao.model3.json 里的 Expressions Name。
 *   你现在有 exp_01 到 exp_08。
 *
 * priority:
 *   动作优先级。越高越强制。
 *   1 适合 Idle，3 适合普通动作，5 适合强制打断。
 */
data class AvatarAction(
    val motionGroup: String? = null,
    val motionName: String? = null,
    val expressionName: String? = null,
    val priority: Int = 3,
    val force: Boolean = true
)

/**
 * 把“情绪”转换成“Live2D 动作命令”的地方。
 *
 * 重点：
 * 这里先使用你当前已有的资源：
 * - 动作组：Idle、TapBody
 * - 表情：exp_01 到 exp_08
 *
 * 等你以后给模型加了 Happy、Sad、Angry 等动作组，
 * 只需要改这里的映射，不需要改 AI 对话层。
 */
object AvatarActionResolver {

    fun fromEmotion(emotion: AvatarEmotion): AvatarAction {
        return when (emotion) {
            AvatarEmotion.NEUTRAL -> AvatarAction(
                motionGroup = "Idle",
                motionName = null,
                expressionName = null,
                priority = 1,
                force = false
            )

            AvatarEmotion.HAPPY -> AvatarAction(
                motionGroup = "TapBody",
                motionName = "motions/mtn_02.motion3.json",
                expressionName = "exp_01",
                priority = 3
            )

            AvatarEmotion.EXCITED -> AvatarAction(
                motionGroup = "TapBody",
                motionName = "motions/special_01.motion3.json",
                expressionName = "exp_02",
                priority = 4
            )

            AvatarEmotion.SAD -> AvatarAction(
                motionGroup = "TapBody",
                motionName = "motions/mtn_03.motion3.json",
                expressionName = "exp_03",
                priority = 3
            )

            AvatarEmotion.ANGRY -> AvatarAction(
                motionGroup = "TapBody",
                motionName = "motions/mtn_04.motion3.json",
                expressionName = "exp_04",
                priority = 4
            )

            AvatarEmotion.SHY -> AvatarAction(
                motionGroup = "TapBody",
                motionName = "motions/special_02.motion3.json",
                expressionName = "exp_05",
                priority = 3
            )

            AvatarEmotion.SURPRISED -> AvatarAction(
                motionGroup = "TapBody",
                motionName = "motions/special_03.motion3.json",
                expressionName = "exp_06",
                priority = 4
            )

            AvatarEmotion.THINKING -> AvatarAction(
                motionGroup = "TapBody",
                motionName = "motions/mtn_03.motion3.json",
                expressionName = "exp_07",
                priority = 3
            )

            AvatarEmotion.CONFUSED -> AvatarAction(
                motionGroup = "TapBody",
                motionName = "motions/mtn_04.motion3.json",
                expressionName = "exp_08",
                priority = 3
            )
        }
    }
}