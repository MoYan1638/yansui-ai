# Mobile Agent

手机 Agent 项目 monorepo（方案见 Obsidian：`项目/手机Agent-自研方案.md`）。

```
core/          Agent 核心：模型适配层 + 有界工具循环 + 内置工具 + 文件记忆（纯 Kotlin，无 Android 依赖）
skill-loader/  M2：解析 SKILL.md → 系统指令 + 工具清单
app-android/   M3：Jetpack Compose 壳
skills/        随包内置的 skill
docs/          配置样例等
prototype/     UI 高保真原型（HTML，M3 视觉基准）
data/          本地运行数据（配置/记忆/会话，gitignore，不上传）
```

## M1 运行（JVM 命令行版核心循环）

1. 复制 `docs/config.example.json` → `data/config.json`，填入 OpenAI 兼容端点与模型名
2. API Key 建议走环境变量（不落盘）：`MOBILE_AGENT_API_KEY`，或直接写进 config
3. 运行：`gradle :core:run`（首次会自动下载依赖）

命令行内指令：`/exit` 退出 · `/new` 开新会话 · `/memory` 查看记忆 · `/history` 消息数

## 里程碑

- [x] M1：JVM 核心循环——连 API、调工具、存记忆、命令行对话
- [ ] M2：Skill 加载器——解析 SKILL.md，跑通 3 个 moyan-skills
- [ ] M3：Android 壳——Compose + 4 页原型还原 + 首启引导
- [ ] M4：进阶档——Shizuku 检测 + 命令行工具解锁
