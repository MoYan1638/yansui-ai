# 言岁AI · 经验教训备忘

## 2026-11-27 网络：git push 被代理挡时用 GitHub API 兜底
- 现象：`git push` 持续 502/schannel 断连，但 `gh`（走 api.github.com）能通
- 兜底链路：
  - 发 release：`gh release create vX.Y.Z apk文件` 不受 git push 影响
  - 改单文件（如 update.json）：`gh api -X PUT repos/OWNER/REPO/contents/FILE -f content=<base64> -f sha=<旧sha>` 直接提交（先 `gh api .../contents/FILE --jq .sha` 取旧 sha）
  - 注意：API 提交会让本地落后远端一笔，之后 git push 会报 non-fast-forward，需先 pull --rebase
  - git blobs/tree API 重建整个提交的方案在 Windows 命令行会超长且触发安全策略拦截，不要走

## 2026-11-27 安全：API Key 加密存储
- Android Keystore 硬件密钥 + AES-256-GCM，密钥不出 TEE，备份/反编译拿不到
- 落盘格式 `enc:v1:<ivB64>:<ctB64>`；无前缀视为旧明文，读时兼容、写时升级（自动迁移）
- core 纯 Kotlin 模块不 import Android：加解密用钩子参数注入（`ModelConfig.load(path, decryptApiKey)`），实现放 App 层
- 教训：系统组件（com.android.providers.downloads）发的广播必须 RECEIVER_EXPORTED，NOT_EXPORTED 在 Android 13+ 会被拦（见 v0.8.15）
