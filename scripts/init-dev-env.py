#!/usr/bin/env python3
"""Create local development bindings without logging or committing passwords."""
from pathlib import Path
import secrets

root = Path(__file__).resolve().parent.parent
target = root / ".env"
if target.exists():
    print("保留已有 .env，未覆盖。")
else:
    text = (root / ".env.example").read_text(encoding="utf-8")
    for name in ("DB_PASSWORD", "MYSQL_ROOT_PASSWORD", "APP_DEMO_PASSWORD"):
        text = text.replace(f"{name}=\n", f"{name}={secrets.token_hex(20)}\n")
    # Exclusive creation prevents overwriting another process's configuration.
    with target.open("x", encoding="utf-8") as output:
        target.chmod(0o600)
        output.write(text)
    print("已生成被 Git 忽略的 .env；演示账号密码请在该本地文件中查看。")
