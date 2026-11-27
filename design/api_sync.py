#!/usr/bin/env python3
"""API 推送后安全对齐本地：先确认远端可达且 SHA 落盘，再本地对齐，杜绝『fetch 失败 reset 却执行』砸工作区。
用法: python design/api_sync.py <pushed_remote_sha> <local_head_sha>
"""
import sys, subprocess, json, urllib.request, pathlib

REMOTE_SHA = sys.argv[1] if len(sys.argv) > 1 else ""
LOCAL_SHA = sys.argv[2] if len(sys.argv) > 2 else ""
REPO = "MoYan1638/yansui-ai"
STATE = pathlib.Path(__file__).parent / ".last_pushed_sha"

# 记住最近一次成功推送的远端 SHA（api_push.py PUSHED 行）
if REMOTE_SHA:
    STATE.write_text(REMOTE_SHA)
elif STATE.exists():
    REMOTE_SHA = STATE.read_text().strip()

if not REMOTE_SHA or not LOCAL_SHA:
    print("用法: api_sync.py <remote_sha> <local_sha>"); sys.exit(1)

try:
    req = urllib.request.Request(f"https://api.github.com/repos/{REPO}/commits/{REMOTE_SHA}",
                                 headers={"User-Agent": "api-sync"})
    with urllib.request.urlopen(req, timeout=20) as r:
        assert json.load(r)["sha"].startswith(REMOTE_SHA[:7])
    print(f"远端确认 OK: {REMOTE_SHA[:9]}")
except Exception as e:
    print(f"远端暂不可达（{e}），本地保持 {LOCAL_SHA[:9]} 不动，下次再对齐")
    sys.exit(0)

# 远端确认无误才对齐：内容一致则直接把分支指过去，绝不碰工作区内容
subprocess.run(["git", "update-ref", "refs/remotes/origin/main", REMOTE_SHA], check=True)
r = subprocess.run(["git", "diff", "--stat", LOCAL_SHA, REMOTE_SHA], capture_output=True, text=True)
if r.returncode == 0 and not r.stdout.strip():
    subprocess.run(["git", "update-ref", "refs/heads/main", LOCAL_SHA], check=True)
    print(f"内容一致，本地已对齐 {LOCAL_SHA[:9]}")
else:
    print("本地与远端内容有差异，保留本地提交，人工确认后再处理")
