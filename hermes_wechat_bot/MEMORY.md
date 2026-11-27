# 言岁AI · 经验教训备忘

## 2026-11-27 技能热更新：源内容会变，下载必须校验
- 事故：用户检查更新把 love-companion 拉成了上游原版——当时远端 skills/love-companion 短暂停的是原版（另一条工作线推的 update 提交）
- 加固（SkillUpdater.kt）：①raw/mirror 请求带 `?t=<ms>` 破 CDN/镜像缓存；②SKILL.md 下载后校验头部含「移动端适配版」，否则中止且不动本地
- 规则：版本比对用 `!=` 而非大小比较，降级回移动版也能触发重装；远端同路径内容可能被多次覆盖，消费端必须做内容指纹校验

## 2026-11-27 网络：git push 被代理挡时用 GitHub API 兜底
- 现象：`git push` 持续 502/schannel 断连，但 `gh`（走 api.github.com）能通
- 兜底链路：
  - 发 release：`gh release create vX.Y.Z apk文件` 不受 git push 影响
  - 改单文件（如 update.json）：`gh api -X PUT repos/OWNER/REPO/contents/FILE -f content=<base64> -f sha=<旧sha>` 直接提交（先 `gh api .../contents/FILE --jq .sha` 取旧 sha）
  - 注意：API 提交会让本地落后远端一笔，之后 git push 会报 non-fast-forward，需先 pull --rebase
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
