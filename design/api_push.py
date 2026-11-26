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

# 差异文件（相对远端 head）
status = git("diff", "--name-status", base_commit_sha, "HEAD")
entries = []
for line in status.splitlines():
    parts = line.split("\t")
    st, path = parts[0], parts[-1]
    if st.startswith("D"):
        entries.append({"path": path, "mode": "100644", "type": "blob", "sha": None})
        continue
    with open(path, "rb") as f:
        content = f.read()
    blob = call("POST", f"/repos/{REPO}/git/blobs",
                {"content": base64.b64encode(content).decode(), "encoding": "base64"})
    entries.append({"path": path, "mode": "100644", "type": "blob", "sha": blob["sha"]})
    print("blob", path, blob["sha"][:8])

tree = call("POST", f"/repos/{REPO}/git/trees",
            {"base_tree": base_tree_sha, "tree": entries})
commit = call("POST", f"/repos/{REPO}/git/commits",
              {"message": MSG, "tree": tree["sha"], "parents": [base_commit_sha]})
call("PATCH", f"/repos/{REPO}/git/refs/heads/{BRANCH}", {"sha": commit["sha"]})
print("PUSHED", commit["sha"][:10], commit["html_url"])
print("local head", git("rev-parse", "HEAD")[:10])
