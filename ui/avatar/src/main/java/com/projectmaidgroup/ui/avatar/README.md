Live2D AI 对话动作接口与后续 API 对接说明
1. 当前目标
   本项目当前正在实现一个 Android + Jetpack Compose + Live2D 的 AI 聊天助手。
   本阶段 Live2D 模块的目标不是让 AI 直接操作模型资源文件，而是先封装出一套稳定的动作接口，使后续 AI 对话模块只需要输出“回复内容”和“情绪类型”，Live2D 层即可自动根据情绪播放对应动作和表情。
   整体目标如下：
   Plain textAI 回复文本  ↓情绪识别  ↓AvatarEmotion  ↓AvatarActionResolver  ↓AvatarAction  ↓Live2D View  ↓Live2DRenderer  ↓MaoUserModel  ↓播放 Live2D motion + expression

2. 当前项目中相关文件
   当前主要相关文件如下：
   Plain textapp/src/main/java/com/projectmaidgroup/mobileaidomestic/Live2dTalk.kt
   负责聊天页面、输入框、消息列表、Live2D 头像显示、当前本地占位回复逻辑。
   Plain textui/avatar/src/main/java/com/projectmaidgroup/ui/avatar/Live2DAvatarScreen.kt
   负责 Compose 层显示 Live2D 角色，目前通过 replyMotionTrigger 触发回复动作。当前版本还没有正式接入 AvatarEmotion 和 emotionTrigger。GitHub
   Plain textui/avatar/src/main/java/com/projectmaidgroup/ui/avatar/AvatarAction.kt
   负责定义 AI 对话层可使用的情绪枚举 AvatarEmotion、Live2D 执行动作命令 AvatarAction，以及情绪到动作的映射器 AvatarActionResolver。GitHub
   Plain textui/avatar/src/main/java/com/projectmaidgroup/ui/avatar/EmotionJudge.kt
   负责临时通过关键词判断 AI 回复文本的情绪，后续可以替换为真实大模型 API 返回的情绪字段。GitHub
   Plain textui/avatar/src/main/java/com/projectmaidgroup/ui/avatar/Live2DTextureView.kt
   负责 TextureView 形式的 Live2D 显示，目前已有 loadModel、setClearColor、playReplyMotion，但还没有暴露 playEmotion 和 playAction。GitHub
   Plain textui/avatar/src/main/java/com/projectmaidgroup/ui/avatar/Live2DRenderer.kt
   负责 Live2D 渲染调度，目前仍通过 pendingTapMotion 和 pendingReplyMotion 两个布尔值处理动作触发，还没有接入通用 AvatarAction 队列。GitHub
   Plain textui/avatar/src/main/java/com/projectmaidgroup/ui/avatar/live2d/MaoUserModel.kt
   负责真正加载和控制 Mao Live2D 模型，目前已经能加载 Idle 和 TapBody 动作组，并支持随机播放 TapBody 动作，但还没有完整支持指定动作、指定表情和 AvatarAction。GitHub

3. 当前对话脚本结构
   当前聊天页面主逻辑位于：
   Plain textapp/src/main/java/com/projectmaidgroup/mobileaidomestic/Live2dTalk.kt
   目前它内部有一个本地回复接口：
   Kotlinprivate interface AgentBackend {    suspend fun reply(input: String, userName: String): String}
   当前占位实现是：
   Kotlinprivate class LocalEchoAgent : AgentBackend {    override suspend fun reply(input: String, userName: String): String {        delay(450)        return "收到，$userName：$input"    }}
   也就是说，目前项目里的 AI 回复并不是真的来自大模型 API，而是本地回声回复。Live2DTalk() 中通过：
   Kotlinval backend = remember { LocalEchoAgent() }
   创建当前回复后端。发送消息时，在 onSend 中调用：
   Kotlinval answer = backend.reply(content, userName)agentAnimateTick++messages += ChatMessage(    id = System.currentTimeMillis() + 1,    role = ChatRole.AGENT,    content = answer)
   当前 agentAnimateTick++ 会触发 Live2D 的 replyMotionTrigger，从而播放一次随机回复动作。GitHub
   当前链路可以理解为：
   Plain text用户点击发送  ↓Live2dTalk.kt / onSend  ↓backend.reply(content, userName)  ↓LocalEchoAgent 返回本地占位回复  ↓agentAnimateTick++  ↓messages 添加 AI 回复  ↓Live2DAvatarScreen 收到 replyMotionTrigger  ↓播放随机回复动作

4. 当前动作接口设计
   4.1 AvatarEmotion
   AvatarEmotion 是 AI 对话层应该使用的情绪枚举。
   后续 AI 不应该直接输出 Live2D 资源名，例如：
   Plain textmotions/mtn_02.motion3.jsonexp_01TapBody
   而应该输出语义化情绪，例如：
   Plain textHAPPYSADANGRYTHINKINGCONFUSED
   当前已有情绪类型包括：
   Kotlinenum class AvatarEmotion {    NEUTRAL,    HAPPY,    SAD,    ANGRY,    SHY,    SURPRISED,    THINKING,    CONFUSED,    EXCITED}
   4.2 AvatarAction
   AvatarAction 是 Live2D 层最终执行的动作命令。
   当前设计如下：
   Kotlindata class AvatarAction(    val motionGroup: String? = null,    val motionName: String? = null,    val expressionName: String? = null,    val priority: Int = 3,    val force: Boolean = true)
   字段含义：
   字段作用motionGroupLive2D model3.json 中的动作组，例如 Idle、TapBodymotionName具体动作文件，例如 motions/mtn_02.motion3.jsonexpressionName具体表情名，例如 exp_01priority动作优先级，数字越高越优先force是否强制打断当前动作
   4.3 AvatarActionResolver
   AvatarActionResolver 用于把 AI 输出的情绪转换为具体 Live2D 动作。
   示例：
   KotlinAvatarEmotion.HAPPY -> AvatarAction(    motionGroup = "TapBody",    motionName = "motions/mtn_02.motion3.json",    expressionName = "exp_01",    priority = 3)
   这样做的好处是：
   Plain textAI 对话层只关心情绪Live2D 层负责资源映射模型资源变化时只改 AvatarActionResolver聊天主流程不需要改

5. 当前还需要补完的 Live2D 链路
   当前已经有 AvatarEmotion、AvatarAction 和 AvatarActionResolver，但它们还没有完全接入底层执行链路。
   需要补完的目标链路是：
   Plain textLive2dTalk.kt  ↓Live2DAvatarScreen.kt  ↓Live2DTextureView.kt  ↓Live2DRenderer.kt  ↓MaoUserModel.kt
   5.1 Live2dTalk.kt 需要新增情绪状态
   在 Live2DTalk() 中，当前已有：
   Kotlinvar agentAnimateTick by rememberSaveable { mutableIntStateOf(0) }
   建议新增：
   Kotlinvar avatarEmotion by rememberSaveable {    mutableStateOf(AvatarEmotion.NEUTRAL)}var avatarEmotionTrigger by rememberSaveable {    mutableIntStateOf(0)}
   同时需要在文件顶部增加 import：
   Kotlinimport com.projectmaidgroup.ui.avatar.AvatarEmotionimport com.projectmaidgroup.ui.avatar.EmotionJudge
   后续如果 AI API 直接返回情绪，则还需要：
   Kotlinimport com.projectmaidgroup.ui.avatar.AvatarEmotion
   5.2 Live2DAvatarScreen 调用需要增加 emotion 参数
   当前调用是：
   KotlinLive2DAvatarScreen(    modifier = Modifier.fillMaxSize(),    model = AvatarModels.DefaultAssistant,    backgroundColor = live2dBgColor.toArgb(),    replyMotionTrigger = agentAnimateTick)
   建议改为：
   KotlinLive2DAvatarScreen(    modifier = Modifier.fillMaxSize(),    model = AvatarModels.DefaultAssistant,    backgroundColor = live2dBgColor.toArgb(),    replyMotionTrigger = agentAnimateTick,    emotion = avatarEmotion,    emotionTrigger = avatarEmotionTrigger)
   5.3 onSend 中需要在 AI 回复后更新情绪
   当前发送逻辑中有：
   Kotlinval answer = backend.reply(content, userName)agentAnimateTick++messages += ChatMessage(    id = System.currentTimeMillis() + 1,    role = ChatRole.AGENT,    content = answer)
   如果暂时还没有真实 API，可以先用本地关键词判断：
   Kotlinval answer = backend.reply(content, userName)avatarEmotion = EmotionJudge.guess(answer)avatarEmotionTrigger++agentAnimateTick++messages += ChatMessage(    id = System.currentTimeMillis() + 1,    role = ChatRole.AGENT,    content = answer)
   后续如果 API 返回了情绪字段，则改成：
   Kotlinval result = backend.reply(content, userName)avatarEmotion = result.emotionavatarEmotionTrigger++agentAnimateTick++messages += ChatMessage(    id = System.currentTimeMillis() + 1,    role = ChatRole.AGENT,    content = result.reply)

6. 后续 API 应该接在哪里
   6.1 推荐接入位置
   后续大模型 API 最推荐接在：
   Plain textapp/src/main/java/com/projectmaidgroup/mobileaidomestic/Live2dTalk.kt
   具体位置是当前的：
   Kotlinprivate interface AgentBackend {    suspend fun reply(input: String, userName: String): String}
   和：
   Kotlinprivate class LocalEchoAgent : AgentBackend
   也就是说，后续 API 不建议直接写在 onSend 里面，而是新增一个新的后端实现，例如：
   Kotlinprivate class RemoteApiAgent : AgentBackend {    override suspend fun reply(input: String, userName: String): String {        // 在这里调用远端 AI API    }}
   然后把：
   Kotlinval backend = remember { LocalEchoAgent() }
   替换成：
   Kotlinval backend = remember { RemoteApiAgent() }
   这样聊天 UI 不需要关心底层到底是本地回复、远端 API、本地模型，还是 Agent 框架。

6.2 但为了支持情绪，建议升级 AgentBackend 返回值
当前 AgentBackend.reply(...) 只返回 String：
Kotlinprivate interface AgentBackend {    suspend fun reply(input: String, userName: String): String}
这只能返回回复文本，不能返回情绪。
建议改为：
Kotlinprivate data class AgentReply(    val text: String,    val emotion: AvatarEmotion = AvatarEmotion.NEUTRAL)private interface AgentBackend {    suspend fun reply(input: String, userName: String): AgentReply}
然后本地占位实现改成：
Kotlinprivate class LocalEchoAgent : AgentBackend {    override suspend fun reply(input: String, userName: String): AgentReply {        delay(450)        val text = "收到，$userName：$input"        return AgentReply(            text = text,            emotion = EmotionJudge.guess(text)        )    }}
后续远端 API 实现可以写成：
Kotlinprivate class RemoteApiAgent : AgentBackend {    override suspend fun reply(input: String, userName: String): AgentReply {        /*         * 这里调用 HTTP API。         *         * 推荐 API 返回：         * {         *   "reply": "当然可以，我来帮你一步一步完成。",         *   "emotion": "HAPPY"         * }         */        TODO("接入远端 API")    }}
发送逻辑则改为：
Kotlinscope.launch {    val result = backend.reply(content, userName)    avatarEmotion = result.emotion    avatarEmotionTrigger++    agentAnimateTick++    messages += ChatMessage(        id = System.currentTimeMillis() + 1,        role = ChatRole.AGENT,        content = result.text    )}

6.3 推荐的 API 返回 JSON
后续远端大模型 API 推荐返回固定 JSON：
JSON{  "reply": "当然可以，我来帮你一步一步完成。",  "emotion": "HAPPY"}
字段说明：
字段类型说明replyStringAI 要展示在聊天气泡中的回复文本emotionStringAI 根据回复语气判断出的情绪
emotion 推荐只允许以下值：
Plain textNEUTRALHAPPYSADANGRYSHYSURPRISEDTHINKINGCONFUSEDEXCITED
不要让 API 返回：
Plain textmotions/mtn_02.motion3.jsonexp_01TapBody
原因是这些属于 Live2D 资源细节。
API 只应该返回语义情绪，具体资源映射由客户端 AvatarActionResolver 处理。

7. 推荐新增的情绪解析函数
   可以在 Live2dTalk.kt 里先写一个简单解析函数：
   Kotlinprivate fun parseEmotion(value: String?): AvatarEmotion {    return when (value?.uppercase()) {        "HAPPY" -> AvatarEmotion.HAPPY        "SAD" -> AvatarEmotion.SAD        "ANGRY" -> AvatarEmotion.ANGRY        "SHY" -> AvatarEmotion.SHY        "SURPRISED" -> AvatarEmotion.SURPRISED        "THINKING" -> AvatarEmotion.THINKING        "CONFUSED" -> AvatarEmotion.CONFUSED        "EXCITED" -> AvatarEmotion.EXCITED        "NEUTRAL" -> AvatarEmotion.NEUTRAL        else -> AvatarEmotion.NEUTRAL    }}
   如果 API 返回：
   JSON{  "reply": "我想想看，这个问题需要分几步处理。",  "emotion": "THINKING"}
   则客户端解析后：
   KotlinavatarEmotion = parseEmotion(apiResult.emotion)avatarEmotionTrigger++
   最终 Live2D 会播放 THINKING 对应的动作和表情。

8. 后续 API 接入推荐结构
   推荐后续形成这样的结构：
   Plain textLive2dTalk.kt  ├─ ChatMessage  ├─ AgentReply  ├─ AgentBackend  ├─ LocalEchoAgent  ├─ RemoteApiAgent  ├─ parseEmotion()  └─ Live2DTalk()
   其中：
   Plain textChatMessage
   只负责聊天记录显示。
   Plain textAgentReply
   负责承载 AI 返回结果，包括文本和情绪。
   Plain textAgentBackend
   负责统一聊天后端接口。
   Plain textLocalEchoAgent
   负责本地占位回复，方便没有 API 时测试 UI。
   Plain textRemoteApiAgent
   负责后续真正调用远端大模型 API。
   Plain textparseEmotion()
   负责把 API 返回的字符串情绪转换成 AvatarEmotion。
   Plain textLive2DTalk()
   负责页面状态、发送消息、更新消息列表、触发 Live2D 动作。

9. 推荐的最终调用链路
   后续完整对话链路应为：
   Plain text用户输入文本  ↓点击发送按钮  ↓Live2dTalk.kt / onSend  ↓backend.reply(content, userName)  ↓RemoteApiAgent 调用远端大模型 API  ↓API 返回 reply + emotion  ↓parseEmotion(emotion)  ↓更新 avatarEmotion  ↓avatarEmotionTrigger++  ↓messages 添加 AI 回复  ↓Live2DAvatarScreen 收到 emotionTrigger 变化  ↓Live2DTextureView.playEmotion(emotion)  ↓Live2DRenderer.playEmotion(emotion)  ↓AvatarActionResolver.fromEmotion(emotion)  ↓MaoUserModel.playAction(action)  ↓Live2D 播放对应 motion + expression

10. 当前还需要补完的代码点
    当前仓库里已经有情绪和动作接口定义，但还没有完整接到底层执行。需要补完以下内容。
    10.1 修正 EmotionJudge.kt
    当前 EmotionJudge.kt 中：
    Kotlinprivate fun hasAny(text: String, words: List): Boolean
    应该改成：
    Kotlinprivate fun hasAny(text: String, words: List<String>): Boolean
    否则 Kotlin 泛型不完整，容易编译报错。GitHub

10.2 Live2DAvatarScreen.kt 增加 emotion 参数
建议把函数参数从：
Kotlin@Composablefun Live2DAvatarScreen(    model: Live2DModelSpec,    modifier: Modifier = Modifier,    backgroundColor: Int,    replyMotionTrigger: Int)
改成：
Kotlin@Composablefun Live2DAvatarScreen(    model: Live2DModelSpec,    modifier: Modifier = Modifier,    backgroundColor: Int,    replyMotionTrigger: Int = 0,    emotion: AvatarEmotion = AvatarEmotion.NEUTRAL,    emotionTrigger: Int = 0)
并在 update 中加入：
Kotlinif (view.lastEmotionTrigger != emotionTrigger) {    view.lastEmotionTrigger = emotionTrigger    if (emotionTrigger > 0) {        view.playEmotion(emotion)    }}

10.3 Live2DTextureView.kt 增加对外动作接口
当前 Live2DTextureView.kt 只有 playReplyMotion()，建议新增：
Kotlin@Volatilevar lastEmotionTrigger: Int = Int.MIN_VALUEfun playAction(action: AvatarAction) {    live2dRenderer.queueAction(action)    renderThread?.requestRender()}fun playEmotion(emotion: AvatarEmotion) {    live2dRenderer.playEmotion(emotion)    renderThread?.requestRender()}

10.4 Live2DRenderer.kt 增加 AvatarAction 队列
当前 Live2DRenderer.kt 仍使用：
Kotlinprivate var pendingTapMotion = falseprivate var pendingReplyMotion = false
建议改为：
Kotlinprivate val pendingActions = ConcurrentLinkedQueue<AvatarAction>()
并增加：
Kotlinfun queueAction(action: AvatarAction) {    pendingActions.offer(action)}fun playEmotion(emotion: AvatarEmotion) {    queueAction(AvatarActionResolver.fromEmotion(emotion))}
在 onDrawFrame() 中执行：
Kotlinprivate fun consumePendingActions(model: MaoUserModel) {    while (true) {        val action = pendingActions.poll() ?: break        model.playAction(action)    }}

10.5 MaoUserModel.kt 增加指定动作和表情执行能力
当前 MaoUserModel.kt 只支持随机播放 TapBody，还不能根据 AvatarAction 指定动作和表情。建议补充：
Kotlinfun playAction(action: AvatarAction) {    action.expressionName?.let { playExpression(it) }    if (action.motionGroup != null) {        playMotion(            group = action.motionGroup,            motionName = action.motionName,            priority = action.priority,            force = action.force        )    }}
同时需要加载表情资源：
Kotlinprivate val expressions = linkedMapOf<String, CubismExpressionMotion>()
并在模型加载时读取 model3.json 中的 Expressions。

11. 最小可运行版本建议
    如果暂时不想一次性接远端 API，可以先按下面顺序做：
    Plain text1. 修正 EmotionJudge.kt 的 List<String>2. Live2DAvatarScreen 增加 emotion 和 emotionTrigger3. Live2DTextureView 增加 playEmotion4. Live2DRenderer 增加 queueAction 和 playEmotion5. MaoUserModel 增加 playAction、playMotion、playExpression6. Live2dTalk.kt 中增加 avatarEmotion 和 avatarEmotionTrigger7. onSend 中先用 EmotionJudge.guess(answer)8. 按钮或聊天测试动作是否触发9. 最后再替换 LocalEchoAgent 为 RemoteApiAgent

12. 推荐给后端或 API 对接同学看的说明
    后续 AI API 不需要了解 Live2D 的具体动作文件，也不需要返回 Live2D 资源路径。
    API 只需要返回：
    JSON{  "reply": "显示给用户看的回复文本",  "emotion": "HAPPY"}
    客户端会负责：
    Plain text1. 展示 reply 到聊天气泡2. 把 emotion 转成 AvatarEmotion3. 通过 AvatarActionResolver 转成 Live2D 动作4. 触发角色播放对应动画
    这样可以保证：
    Plain textAI 服务只负责语言和情绪判断客户端 Live2D 模块负责动作映射后续更换模型资源时不影响 API后续更换 API 时不影响 Live2D 资源

13. 一句话总结
    当前项目已经具备聊天 UI、Live2D 展示和基础动作触发能力；下一步应在 Live2dTalk.kt 中把 AgentBackend 从单纯返回字符串升级为返回 AgentReply(text, emotion)，再把 emotion 传给 Live2DAvatarScreen，最终通过 AvatarActionResolver -> Live2DRenderer -> MaoUserModel 完成 AI 情绪驱动 Live2D 动作播放。