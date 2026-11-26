# -*- coding: utf-8 -*-
"""用 GitHub Git Data API 推送提交（绕过 git 端点连不上的问题）。
用法：python design/api_push.py <repo> <branch> <commit-message>
只推送 HEAD 与 upstream 之间的差异文件。
"""
import base64
import json
import subprocess
import sys
import urllib.error
import urllib.request

REPO = sys.argv[1] if len(sys.argv) > 1 else "MoYan1638/yansui-ai"
BRANCH = sys.argv[2] if len(sys.argv) > 2 else "main"
MSG = sys.argv[3] if len(sys.argv) > 3 else "update"

token = subprocess.check_output(["gh", "auth", "token"], text=True).strip()
API = "https://api.github.com"


def call(method, path, payload=None):
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(f"{API}{path}", data=data, method=method)
    req.add_header("Authorization", f"token {token}")
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("Content-Type", "application/json")
    req.add_header("User-Agent", "yansui-api-push")
    try:
        with urllib.request.urlopen(req, timeout=90) as r:
            body = r.read()
            return json.loads(body) if body else {}
    except urllib.error.HTTPError as e:
        detail = e.read().decode(errors="replace")[:600]
        raise SystemExit(f"[{method} {path}] {e.code}: {detail}")


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


# 远端当前 HEAD
ref = call("GET", f"/repos/{REPO}/git/ref/heads/{BRANCH}")
base_commit_sha = ref["object"]["sha"]
base_tree_sha = call("GET", f"/repos/{REPO}/git/commits/{base_commit_sha}")["tree"]["sha"]
print("remote head", base_commit_sha)

local_tree = git("rev-parse", "HEAD^{tree}")
if local_tree == base_tree_sha:
    print("远端已是最新，无需推送")
    sys.exit(0)

# 差异：对比远端树与本地 HEAD 树的 blob 哈希（不依赖本地存在远端 commit 对象）
remote_files = {}


def walk_tree(sha):
    t = call("GET", f"/repos/{REPO}/git/trees/{sha}?recursive=1")
    for item in t["tree"]:
        if item["type"] == "blob":
            remote_files[item["path"]] = item["sha"]
    if t.get("truncated"):
        raise SystemExit("远端树过大被截断，请手动处理")


walk_tree(base_tree_sha)

local_files = {}
for line in git("ls-tree", "-r", "HEAD").splitlines():
    meta, path = line.split("\t", 1)
    mode, typ, sha = meta.split()
    local_files[path] = sha

changed = [p for p, s in local_files.items() if remote_files.get(p) != s]
deleted = [p for p in remote_files if p not in local_files]
print(f"changed {len(changed)}, deleted {len(deleted)}")

entries = []
for path in changed:
    content = subprocess.check_output(["git", "cat-file", "blob", f"HEAD:{path}"])
    blob = call("POST", f"/repos/{REPO}/git/blobs",
                {"content": base64.b64encode(content).decode(), "encoding": "base64"})
    entries.append({"path": path, "mode": "100644", "type": "blob", "sha": blob["sha"]})
    print("blob", path, blob["sha"][:8])
for path in deleted:
    entries.append({"path": path, "mode": "100644", "type": "blob", "sha": None})
    print("delete", path)

tree = call("POST", f"/repos/{REPO}/git/trees",
            {"base_tree": base_tree_sha, "tree": entries})
commit = call("POST", f"/repos/{REPO}/git/commits",
              {"message": MSG, "tree": tree["sha"], "parents": [base_commit_sha]})
call("PATCH", f"/repos/{REPO}/git/refs/heads/{BRANCH}", {"sha": commit["sha"]})
print("PUSHED", commit["sha"][:10], commit["html_url"])
print("local head", git("rev-parse", "HEAD")[:10])
