# -*- coding: utf-8 -*-
"""收集构建产物：把 app/build/outputs/apk/<variant>/*.apk 复制为 dist/卡点-<versionName>.apk"""
import os
import re
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
GRADLE = os.path.join(ROOT, "app", "build.gradle")


def read_version():
    with open(GRADLE, encoding="utf-8") as f:
        m = re.search(r'versionName\s+"([^"]+)"', f.read())
    if not m:
        raise SystemExit("在 app/build.gradle 中找不到 versionName")
    return m.group(1)


def main():
    variant = sys.argv[1] if len(sys.argv) > 1 else "debug"
    src_dir = os.path.join(ROOT, "app", "build", "outputs", "apk", variant)
    if not os.path.isdir(src_dir):
        raise SystemExit(f"找不到构建产物目录: {src_dir}")
    apks = [f for f in os.listdir(src_dir) if f.endswith(".apk")]
    if not apks:
        raise SystemExit(f"{src_dir} 下没有 APK")

    dist = os.path.join(ROOT, "dist")
    os.makedirs(dist, exist_ok=True)
    version = read_version()
    dest = os.path.join(dist, f"卡点-{version}.apk")
    shutil.copy2(os.path.join(src_dir, apks[0]), dest)
    size = os.path.getsize(dest)
    print(f"产物: {dest}  ({size:,d} 字节, {size / 1048576:.2f} MiB)")


if __name__ == "__main__":
    main()
