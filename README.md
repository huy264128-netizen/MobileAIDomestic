# Live2D Android Chat Assistant

一个基于 **Android + Jetpack Compose + Live2D + Shizuku** 的本地聊天助手 Demo。  
当前版本重点完成了：

- Live2D 角色展示与基础动作触发
- 聊天 UI 与消息流
- 本地占位 Agent（回声回复）
- Shizuku 能力封装与 AIDL Service 通路
- 为后续接入 **HTTP API / 大模型 / 智能体 / 工具调用** 预留接口

---

## 1. 项目目标

本项目的目标不是把“模型能力”写死在客户端里，而是把 **聊天界面、角色渲染、动作反馈、系统能力调用** 先搭好，方便后续由其他成员接入：

- 远端大模型 API
- 本地模型 / 边缘推理
- Agent 框架
- 工具调用（如设备控制、自动化、命令执行）
- TTS / ASR / 记忆 / RAG 等扩展能力

当前你看到的默认回复仅为占位逻辑，方便前端联调与交互验证。

---

## 2. 项目结构

```text
.
├─ app                            # Android 主应用，聊天界面与页面入口
├─ ui/avatar                      # Live2D 角色显示、模型加载、渲染与动作触发
├─ platform/shizuku_for_maid      # Shizuku 客户端封装，对外提供命令执行能力
├─ platform/shizuku_service       # Shizuku UserService + AIDL 定义与实现
├─ third_party/live2d_framework   # Live2D Framework
└─ app/libs/Live2DCubismCore.aar  # Live2D Core AAR