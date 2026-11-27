#!/usr/bin/env python3
"""API 推送后安全确认：校验远端提交可达且 tree 与本地一致；只读不改任何 ref。
远端不可达时不做任何本地动作（旧流程 git reset --hard 在 fetch 失败后会砸工作区，已弃用）。
用法: python design/api_sync.py <pushed_remote_sha>
"""
import sys, subprocess, json, urllib.request, pathlib

REPO = "MoYan1638/yansui-ai"
remote_sha = sys.argv[1] if len(sys.argv) > 1 else ""

if not remote_sha:
    print("用法: api_sync.py <remote_sha>"); sys.exit(1)

try:
    req = urllib.request.Request(f"https://api.github.com/repos/{REPO}/commits/{remote_sha}",
                                 headers={"User-Agent": "api-sync"})
    with urllib.request.urlopen(req, timeout=20) as r:
        commit = json.load(r)
    remote_tree = commit["commit"]["tree"]["sha"]
    local_tree = subprocess.run(["git", "rev-parse", "HEAD^{tree}"],
                                capture_output=True, text=True, check=True).stdout.strip()
    if remote_tree == local_tree:
        print(f"远端确认 OK，tree 一致（{remote_tree[:9]}），本地内容已是最新")
    else:
        print(f"警告：远端 tree {remote_tree[:9]} != 本地 {local_tree[:9]}，请人工核对")
except Exception as e:
    print(f"远端暂不可达（{e}），本地不动，下次再核对")
