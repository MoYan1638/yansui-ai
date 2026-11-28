# 言岁AI · 经验教训备忘

## ⚠ 最高准则：这是手机 App，先用移动端视角审查，不要往电脑上偏（2026-11-28 用户明确要求）
- 每次写/改/审查代码，第一步先问：手机进程随时会被冻结/杀（切后台、内存紧张、Doze、锁屏）、后台受限、网络会 WiFi↔流量切换、电量敏感、权限要运行时申请、屏幕小且触摸交互——桌面/网页里成立的做法在这里大多不成立
- 凡是从桌面/JVM/网页搬来的写法，默认当成有嫌疑，逐条按上面的移动端约束过一遍，而不是等用户提醒
- 高频踩坑点自查清单：
  - 长任务（>10s）：必须前台服务，绝不能 goAsync/普通线程（goAsync 只有约 10 秒窗口）
  - 文件落盘：必须原子写（.tmp + ATOMIC_MOVE），进程被杀不能留半截文件
  - 读系统/外部状态的 UI（电池白名单/权限/Shizuku）：挂 lifecycle ON_RESUME tick，从系统页返回要自动刷新
  - 网络：国内直连 GitHub 必失败，更新/技能下载走可用镜像 + 主源失败自动切备用
  - 后台定时：AlarmManager setAndAllowWhileIdle + 前台服务，重启后重排
  - 前台服务 Android 12+/14+ 启动限制：后台启动 FGS 会抛 ForegroundServiceStartNotAllowedException；豁免只有「精确闹钟 setExact」「电池白名单」「用户可见转换」等。setAndAllowWhileIdle 不算豁免！判断白名单走 FGS，否则 try-catch + goAsync 降级，绝不裸调
  - Manifest 声明 foregroundServiceType 后，API 34+ 的 startForeground 必须用带 type 的签名（ServiceCompat.startForeground(..., FOREGROUND_SERVICE_TYPE_DATA_SYNC)），否则 MissingForegroundServiceTypeException
  - JVM Stream.toList() 是 API 34/Java16+，minSdk 26 上老机型直接 NoSuchMethodError → 一律用 .collect(Collectors.toList())
  - Compose 里 mutableStateOf 声明必须 remember，否则重组丢状态
- 用户反复强调过这条，说明之前的审查还不够自觉——以后动手前先按此清单过一遍再写代码

## 2026-11-28 发布前 lint 体检（v0.8.33）
- `./gradlew :app-android:lintRelease` 必跑：抓到 3 个 Error 全是"桌面写法搬手机"——两处 Stream.toList()（历史列表/清空对话在 Android 8-13 崩溃）、一处 mutableStateOf 未 remember（引导页重组丢状态）
- 教训：assembleRelease 不跑 lint（只跑 lintVital），真正的 API 级别/Compose 检查要显式 lintRelease；用户要求"别等发布全是 bug"，以后每版发包前都跑一次
- warning 里多是依赖版本/图标/代码风格，可忽略；Error 才是崩溃级，必须清零

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

## 2026-11-28 移动端特有坑（v0.8.32）
- 规则：审查 App 不能用桌面/网页思维——手机进程随时被系统冻结/杀（切后台/内存紧张/Doze），桌面开线程等几分钟没事，手机上必挂
- 文件写入非原子：writeTextCompat 原直接 Files.write 覆盖，写一半被杀 → 半截 JSON，下次启动解析失败 → 会话/密钥/配置全丢。改为写唯一后缀 .tmp 再 ATOMIC_MOVE 覆盖（不支持则普通 move），一处改 core/NioCompat 全仓库受益
- BroadcastReceiver.goAsync() 只有约 10 秒窗口，定时任务 result.get(6, MINUTES) 基本必被腰斩。改为前台服务 AgentTaskService（startForegroundService + 常驻通知），Manifest 加 FOREGROUND_SERVICE / FOREGROUND_SERVICE_DATA_SYNC 权限 + foregroundServiceType="dataSync"（Android 14+ 必须声明类型）；Receiver 只拉起服务
- 移动端长任务（>10s）一律前台服务，不要 goAsync/普通线程

## 2026-11-28 应用内浏览器替代跳系统浏览器（v0.8.34）
- 需求：API Key 官网教程点「打开官网」老跳系统浏览器，用户嫌麻烦要应用内打开
- 方案：不用 Chrome Custom Tabs——国内机型常无支持浏览器会 fallback 回外部，达不到"不跳出"。用独立 Activity + WebView 真正内置
- BrowserActivity.kt：顶栏(返回/标题/浏览器兜底) + 进度条 + WebView；JS/DOM storage 开(平台控制台登录需要)、禁 file 访问；shouldOverrideUrlLoading 里 http(s) 留应用内、tel/mailto 交系统、其他协议拦掉；返回键 goBack 能退则退否则 finish；Android 15 强制 edge-to-edge 顶栏加 statusBar inset
- 注册 Manifest：BrowserActivity exported=false；openUrl 改成 BrowserActivity.start，失败兜底外部 ACTION_VIEW
- 发包前 lintRelease 务必跑（已固化为规矩）；SSH push 用 ssh://git@ssh.github.com:443 显式 URL 推完后，本地 origin/main 跟踪引用不会自动更新会显示 ahead N（假象），git fetch 该 SSH URL 后 git update-ref refs/remotes/origin/main FETCH_HEAD 修正

## 2026-11-28 更新完成反复弹安装 + 安装包残留（v0.8.35）
- 现象：更新装好后仍反复弹系统安装器；系统通知栏一直挂着「下载完成」，点一下又弹
- 根因：下载任务成功后从没 dm.remove(id)，DownloadManager 的通知常驻且点通知会重发完成广播；resumeDownload 进设置页又读到同一个 SUCCESSFUL 旧记录重复拉起；autoInstall 无去重
- 修复：①新增 AppUpdater.afterInstallLaunched(ctx,id)=dm.remove(任务,通知随之消失)+清 pref；②Screens 加 autoInstall(f,id) 用 autoInstalledId 去重，三处自动拉起(广播/轮询/resume)统一走它；③手动「立即安装」按钮拉起后也调 afterInstallLaunched；④clearStaleApk 只删 cache/update.apk（私有目录那份由 dm.remove 删，不能手动删否则已下载未安装的包会被毁）
- 时序坑：cache/update.apk 不能拉起安装器当场删——安装器此刻可能还在读；留到下次进设置页 clearStaleApk 删

## 2026-11-28 弹安装的真正根源：历史通知堆积（v0.8.36，0.8.35 修复不彻底的补刀）
- 用户反馈装好后打开 App 仍弹安装。0.8.35 只清了「当前记录的那一个任务」，但 0.8.30~0.8.34 每次成功下载都留一条 DownloadManager 完成通知 + 任务，通知栏积了好几条，点任何一条都会打开对应 apk 重弹安装器；且清理逻辑只在进设置页触发，主界面根本跑不到
- 根治：MainActivity.onCreate 即调 AppUpdater.clearFinishedTasks(ctx, currentVer)——query 本应用全部下载任务（DownloadManager.query 天然只返回本 uid 的任务），从 COLUMN_TITLE 解析版本号（yansui-ai-x.y.z-release.apk），<= 当前版本或 FAILED 的 dm.remove（删任务+通知+安装包），> 当前版本的保留给 resumeDownload 弹安装
- 规则：**DownloadManager 任务完成通知的生命周期 = 任务本身**，remove 是唯一清理方式；凡是往通知栏发持久通知的组件（DownloadManager 等），清理必须放在「应用启动」而非「某个页面进入时」

## 2026-11-28 供应商「自定义…」片点击无反应（v0.8.37）
- 现象：设置页供应商片里点「自定义…」完全没反应
- 根因：providers 列表里自定义项的值是 ("" to "")，点击处理写成 `if (pair.first.isNotBlank())` 才切换，空地址被跳过；选中态 `sel = pair.first == baseUrl` 也让自定义地址永远不高亮
- 修复：区分 isCustom(pair.first.isBlank())，自定义点击→清空 baseUrl/model/apiKey 供手填，其余照旧切换；sel 用 usingBuiltIn 反推，地址不在内置厂商里就高亮自定义片
- 规则：列表里用「空值」当特殊项标识时，凡是 if(isNotBlank) 之类的守卫都会误杀它，特殊项必须单独分支处理

## 2026-11-28 自定义 API 持久化为可选供应商（v0.8.38）
- 需求：自定义 API 保存后应成为可选供应商片，切走回来能回显，可删除；不能"一保存就找不到"
- 实现：AgentEngine 新增 custom-providers.json（data/ 下），存 CustomProvider(name,baseUrl,model) 列表；Key 仍复用 keys.json 按 providerKeyOf(baseUrl) 加密存
  - upsertCustomProvider 按 baseUrl 归一去重；removeCustomProvider 同时删记录+删 Key+saveKeys
  - initialize 里 loadCustomProviders（ephemeral 跳过）
- UI：providers 列表 = 内置预设 + openai + 已存自定义 + 「自定义…」新增入口；用 customTick 状态在增删后重建；自定义片旁加 × 删除按钮 + 确认弹窗（删当前使用地址则回退默认 DeepSeek）；builtinUrls 集合判断保存时是否登记为自定义
- 规则：用户"新增的配置项"必须持久化并出现在可选列表里，不能只写当前配置就丢；删除配置要连带清理其关联数据（Key）
