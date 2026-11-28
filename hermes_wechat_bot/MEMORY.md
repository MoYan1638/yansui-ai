# 言岁AI · 经验教训备忘

## 2026-11-27 设置页 Key 管理两坑（v0.8.21）
- 密钥字段回显必须掩码：存储加密≠显示安全，截图/旁观即泄露；Field 加 masked 参数 + 显示/隐藏切换（Visibility 图标在 icons-extended，不想加依赖就用文字按钮）
- 多厂商 Key 共存时，切换供应商必须同时换 key：`apiKey = engine.keyFor(providerKeyOf(url)) ?: ""`，否则上一家的 Key 会被保存到新厂商名下（错误绑定且实调会 401）
- Kotlin 语法坑：`trailingIcon = if (x) {{...} else null` 双大括号写法编译错，要写成 `if (x) { { ... } } else null` 分行

## 2026-11-27 技能热更新：源内容会变，下载必须校验
- 事故：用户检查更新把 love-companion 拉成了上游原版——当时远端 skills/love-companion 短暂停的是原版（另一条工作线推的 update 提交）
- 加固（SkillUpdater.kt）：①raw/mirror 请求带 `?t=<ms>` 破 CDN/镜像缓存；②SKILL.md 下载后校验头部含「移动端适配版」，否则中止且不动本地
- 规则：版本比对用 `!=` 而非大小比较，降级回移动版也能触发重装；远端同路径内容可能被多次覆盖，消费端必须做内容指纹校验

## 2026-11-27 网络：git push 被代理挡时用 GitHub API 兜底
- 现象：`git push` 持续 502/schannel 断连，但 `gh`（走 api.github.com）能通
- 兜底链路：
  - 发 release：`gh release create vX.Y.Z apk文件` 不受 git push 影响
  - 改单文件（如 update.json）：`gh api -X PUT repos/OWNER/REPO/contents/FILE -f content=<base64> -f sha=<旧sha>` 直接提交（先 `gh api .../contents/FILE --jq .sha` 取旧 sha）
  - 注意：API 提交会让本地落后远端一笔，之后 git push 会报 non-fast-forward，需先 pull --rebase；rebase 冲突通常只有 update.json（代码远端已有），`git checkout --theirs update.json && git add && git rebase --continue` 即可
  - git blobs/tree API 重建整个提交的方案在 Windows 命令行会超长且触发安全策略拦截，不要走

## 2026-11-27 液态玻璃导航：指示器盖住的内容必须在采样层里都有
- 结构：真实标签 Row 在下、透镜指示器 drawBackdrop 在上盖着画；透镜内容取自隐藏染色层（tabsBackdrop）
- 教训：隐藏染色层只录了图标、文字放了 `Text(" ")` 占位 → 指示器滑到哪，哪的标题就被玻璃盖住消失（v0.8.19 修复）
- 规则：凡是会被透镜/玻璃盖住重画的元素，隐藏采样层必须 1:1 录全套（图标+文字+字重），否则盖住即消失

## 2026-11-27 安全：API Key 加密存储
- Android Keystore 硬件密钥 + AES-256-GCM，密钥不出 TEE，备份/反编译拿不到
- 落盘格式 `enc:v1:<ivB64>:<ctB64>`；无前缀视为旧明文，读时兼容、写时升级（自动迁移）
- core 纯 Kotlin 模块不 import Android：加解密用钩子参数注入（`ModelConfig.load(path, decryptApiKey)`），实现放 App 层
- 教训：系统组件（com.android.providers.downloads）发的广播必须 RECEIVER_EXPORTED，NOT_EXPORTED 在 Android 13+ 会被拦（见 v0.8.15）

## 2026-11-28 上下文压缩丢 tool_calls → DeepSeek 400（v0.8.27）
- 事故：工具调用多次后请求全报 HTTP 400 "Messages with role 'tool' must be a response to a preceding message with 'tool_calls'"
- 根因：`AgentLoop.compressIfNeeded` 把早期 assistant 消息压成普通占位消息时丢掉 `tool_calls`，紧随其后的 tool 结果成孤儿
- 修复两处：①压缩 assistant 时保留原 `tool_calls` 字段（只压 content）；②`AgentEngine.loadHistory` 加载旧检查点时剔除前面没有 tool_calls 的孤儿 tool 消息，避免用户已损坏的会话继续报错
- 规则：OpenAI 兼容协议里 assistant(tool_calls) 与 tool(tool_call_id) 是严格配对，任何裁剪/压缩历史的操作都必须保配对

## 2026-11-28 代理只通 api.github.com 时的 push 路线
- 现象：Clash Verge 系统代理 53812，api.github.com 200 正常，但 CONNECT github.com:443 超时 → git push 502/Empty reply，gh release / API 正常
- 最稳兜底（比 Contents API 改单文件更完整）：`GIT_SSH_COMMAND="ssh -o BatchMode=yes" git push ssh://git@ssh.github.com:443/OWNER/REPO.git main:main`
  - SSH 走 443 复用 HTTPS 端口，绕开失效的 github.com CONNECT 规则
  - 首次需接受 host key（`StrictHostKeyChecking=accept-new`）
  - 本机 SSH 密钥读取会触发权限弹窗，需用户允许；被拒时当轮不要再重试
- 顺序：优先直推；不通先试 SSH 443；仍不行再用 gh release + Contents API 兜底

## 2026-11-28 更新下载"一直失败"根因 & 镜像选型（v0.8.29）
- 根因：AppUpdater.enqueueDownload 只用 release.urls.first()（GitHub 直连），国内手机直连 github release 必超时；镜像地址解析出来了却从没被使用，失败重试还是同一死地址 → "一直失败"
- 修复：①enqueueDownload 加 idx 参数；②新增 nextSource()；③Screens 加 appDlSrcIdx + onDownloadFailed(id,reason)，失败自动切下一源重下（用 id 比对防止广播与轮询双触发，切源前 remove 旧任务避免同名文件占用）；④下载按钮重置 idx=0
- 镜像实测（无代理模拟手机）：github 直连超时 0B；ghproxy.net 30s 仅 3.9MB（不稳，弃用）；gh-proxy.com 完整 8.7MB 可用 → 定为 apk 主源，github 直连降为备用
- 规则：update.json 里 apk 字段放国内可达镜像、apk_mirror 放 github 直连；UPDATE_MIRROR 也从 ghproxy.net 改 gh-proxy.com

## 2026-11-28 设置页系统状态从外部返回不刷新（v0.8.30）
- 现象：去系统设置开电池白名单/通知权限/Shizuku 授权后返回 App，设置页状态还是旧的，必须重进应用才刷新
- 根因：这些状态（isIgnoringBatteryOptimizations / checkSelfPermission / ShizukuGate）都是重组时直接读系统值，但从外部 Activity 返回不会触发本页重组；shizukuTick 只在点按钮时 ++
- 修复：SettingsScreen 顶部加 resumeTick，用 DisposableEffect + LifecycleEventObserver 监听 ON_RESUME 时 ++；三个状态分别 remember(resumeTick) / remember(shizukuTick, resumeTick) 依赖它，返回即刷新
- 规则：Compose 里凡是"读系统/外部状态"的 UI，都要挂一个 lifecycle ON_RESUME tick 驱动，否则从系统页返回不会更新

## 2026-11-28 代码审查修复（v0.8.31）
- 并行 tool_calls 清理 bug：AgentEngine.loadHistory 清理孤儿 tool 时原用单个 boolean（prevHadToolCalls），一条 assistant 带 N 个并行 tool_calls 时只保留第 1 条 tool 结果，第 2 条起被误删 → assistant 声明 N 个调用却剩 1 个结果，DeepSeek 仍 400。改成 pendingToolResults 计数，按声明数量逐条保留
- 定时任务上下文隔离：CronTaskReceiver 用独立 AgentEngine 但与 UI 共用 history.json，到点执行若用户正在聊天会互相覆盖丢对话。给 AgentEngine 加 ephemeralHistory 参数，定时任务用它 → loadHistory/saveHistory 直接 return，全新空上下文，不碰用户对话
- 清空长期记忆原无二次确认（不可恢复），补 AlertDialog 确认
- SkillUpdater 的 BASE_MIRROR 从 ghproxy.net（实测不稳）统一改 gh-proxy.com
- 规则：同一检查点文件不能被多个 AgentEngine 实例同时读写；孤儿 tool 清理必须按 tool_calls 数量计数，不能用布尔
