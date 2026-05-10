# 智能体工具调用与平台问题说明（维护用 README）

本文档记录本仓库中与 **LangChain4j 工具调用**、**Live2D（ui/avatar）**、**Shizuku** 相关的主要改动、新增能力，以及曾出现的 **编译报错 / 运行时闪退** 的原因与对应修复，便于后续成员排查与演进。

---

## 一、工具调用（LangChain4j + 蓝心 + Shizuku）

### 1.1 目标与行为

- 在 **Android 应用进程内** 使用 **LangChain4j**（`OpenAiChatModel` + `AiServices`）对接 **蓝心等 OpenAI 兼容 Chat Completions** 接口。
- 向模型注册 **本地工具**：通过 **Shizuku UserService** 在特权环境执行 **shell 命令**（`runPrivilegedShellCommand`），将 stdout 等结果返回给模型，用于多轮工具调用后再生成最终中文回复。
- 默认聊天后端在 `Live2dTalk` 中使用 `**LangChain4jShizukuAgent`**（见 `app/.../Live2dTalk.kt` 中 `remember { LangChain4jShizukuAgent() }`）。

### 1.2 主要新增 / 调整的文件（`app` 模块）


| 路径                           | 说明                                                                               |
| ---------------------------- | -------------------------------------------------------------------------------- |
| `AgentContracts.kt`          | `AgentBackend`、`AgentReply` 对话契约                                                 |
| `VivoAigcAuth.kt`            | 蓝心密钥候选解析（组合 key / AppKey）                                                        |
| `VivoRemoteAgent.kt`         | **无工具**：`LocalEchoAgent`、`RemoteVivoBlueLMAgent`（OkHttp 直连蓝心），密钥来自 `BuildConfig` |
| `LangChain4jShizukuAgent.kt` | **有工具**：LangChain4j + `MessageWindowChatMemory` + 多密钥重试                          |
| `agent/MaidAssistant.kt`     | `AiServices` 接口与 System/User 提示模板                                                |
| `agent/ShizukuTooling.kt`    | `@Tool runPrivilegedShellCommand`，内部调用 `ShizukuServiceManager.runCommand`        |


### 1.3 依赖（`app/build.gradle.kts`）

- `dev.langchain4j:langchain4j`、`langchain4j-open-ai`（版本见 `gradle/libs.versions.toml`）
- `org.slf4j:slf4j-android`（避免 LangChain4j 依赖 slf4j 时在 Android 上无绑定）
- 部分 LangChain 相关 JAR 的 `META-INF` 在 `packaging.resources.pickFirsts` 中做了合并，减少打包冲突

### 1.4 配置说明

- 蓝心等密钥仍通过根目录 `**local.properties`** 注入 `**BuildConfig`**（`VIVO_AIGC_*`），与原先 Gradle 逻辑一致。
- `LangChain4jShizukuAgent` 会将 `VIVO_AIGC_BASE_URL` 规范为 OpenAI4j 期望的 **仅含 `/v1` 的 baseUrl**（若配置为完整 `.../v1/chat/completions` 会自动剥后缀）。

### 1.5 工具调用路径上的稳定性改动（`ShizukuTooling`）

**问题（潜在闪退 / 卡死）**：智能体 `reply` 已在 `**Dispatchers.IO`** 上执行；若工具内再使用 `**runBlocking(Dispatchers.IO)`** 去调用 `**suspend` 的 `ShizukuManager.execCommand`**，可能与 Kotlin **IO 线程池嵌套**，出现 **饥饿 / 死锁**，表现为长时间无响应或进程异常结束。

**改动**：工具改为 **同步** 调用 `**ShizukuServiceManager.runCommand`**（Binder 本身为阻塞调用），去掉 `runBlocking`；并对返回文本做 长度截断（避免 Binder `**TransactionTooLarge`** 或 OOM）。

**补充**：`Live2dTalk` 发送消息协程中对 `**backend.reply`** 增加 `**try/catch`**，将未捕获异常转为气泡内错误提示并打 Log，避免协程未处理异常直接导致糟糕体验。

---

## 二、Shizuku：两次典型报错与修复

### 2.1 `process name suffix must not be null` → `NoClassDefFoundError: ShizukuManager`

**原因**：Shizuku **13+** 的 `UserServiceArgs` 在 `bindUserService` 时要求 **非空的进程名后缀**；未调用 `**processNameSuffix(...)`** 时内部为 `null`，触发 NPE，进而导致 `**ShizukuManager` 静态初始化失败**，表现为 `**ExceptionInInitializerError` / `NoClassDefFoundError`**。

**改动**：在 `platform/shizuku_service/.../ShizukuServiceManager.kt` 的 `UserServiceArgs` 链上增加例如 `**.processNameSuffix("maid_shizuku_user_service")`**。

### 2.2 `SecurityException: unable to find package com.projectmaidgroup.platform.shizuku_service`

**原因**：`ComponentName` 的第一个参数写成了 **库模块的 `namespace`**（`com.projectmaidgroup.platform.shizuku_service`），但设备上 **并不存在该包名的独立安装包**；UserService 实际运行在 **主应用 `applicationId`** 对应的 APK 中。

**改动**：

- 在 `**platform/shizuku_service/build.gradle.kts`** 中启用 `**buildConfig`**，并写入 `**SHIZUKU_HOST_APPLICATION_ID`**（当前与 `**app` 的 `applicationId**` 一致：`com.projectmaidgroup.mobileaidomestic`）。
- `ComponentName` 使用 `**BuildConfig.SHIZUKU_HOST_APPLICATION_ID**` + `**ShizukuServiceImpl::class.java.name**`（类全名仍在 `com.projectmaidgroup.platform.shizuku_service` 包下）。

**维护注意**：若修改主应用 `**applicationId`**，必须同步更新 `**SHIZUKU_HOST_APPLICATION_ID`**。

---

## 三、Live2D（`ui/avatar`）：IDE 大量报错与缺失类型

### 3.1 重复的 `AssetUtil` 导致「重复类 / Redeclaration」

**现象**：`Unresolved reference` 或重复定义等大量 IDE/编译错误。

**原因**：曾存在两个源文件均声明 `**package com.projectmaidgroup.ui.avatar.live2d`** 且均为 `**object AssetUtil`**：**  
**其一误放在目录 `**.../ui/avatar/AssetUtil.kt`**（路径与包名不一致），与 `**.../ui/avatar/live2d/AssetUtil.kt**` 冲突。

**改动**：删除错误路径下的重复 `**ui/avatar/.../avatar/AssetUtil.kt`**，仅保留 `**live2d/AssetUtil.kt`**；并去掉 `Live2DRenderer.kt` 中同包多余 import。

### 3.2 `AvatarEmotion` / `AvatarEmotionMapper` / `Live2DMotionCommand` 未定义

**现象**：`MaoUserModel.kt` 等处 `**Unresolved reference`**。

**原因**：业务类型被引用但未在仓库中提交或遗失。

**改动**：新增 `**ui/avatar/.../Live2DMotionTypes.kt`**，包含 `**AvatarEmotion`** 枚举、`**Live2DMotionCommand**` 数据类、`**AvatarEmotionMapper**`（默认将情绪映射到 Mao 模型常见分组 `**TapBody**`，可按实际模型调整）。

---

## 四、与根目录 `README.md` 的关系

根目录 `**README.md**` 仍描述产品整体目标与目录结构；**本文件**侧重 **智能体工具调用实现细节** 与 **Live2D / Shizuku 踩坑记录**。若产品说明有冲突，以代码与 Gradle 为准并建议更新根 README 中的「占位 Agent」等表述。

---

## 五、验证建议（工具调用）

1. 配置 `**local.properties`** 中 `**VIVO_AIGC_*`**，Sync 并 Rebuild。
2. 安装 **Shizuku** 并为本应用授权。
3. 在聊天中发送明确要求执行 **无害 shell** 的句子（如 `getprop ro.product.model`），确认回复中出现 **真实命令输出** 而非编造。
4. 若仍失败，查看 Logcat 中 `**LangChain4jShizukuAgent`**、`**ShizukuTooling`**、`**Live2DTalk**` 标签。

---

*文档随代码演进可继续补充；最后更新以 Git 提交记录为准。*