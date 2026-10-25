#!/usr/bin/env python3
"""把 PWA 源（kadian/）同步到 Android 工程的 assets/public/。
排除：tools/、README、*.md、start.bat（这些不需要进 APK）。"""
import os
import shutil
import sys

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PWA = os.path.join(HERE, '..', 'kadian')
DST = os.path.join(HERE, 'app', 'src', 'main', 'assets', 'public')

EXCLUDE_DIRS = {'tools', '.workbuddy', '__pycache__'}
EXCLUDE_FILES = {'README.md', 'start.bat'}
EXCLUDE_EXT = {'.md', '.py', '.pyc'}


def ignore(_dir, names):
    drop = set()
    for n in names:
        p = os.path.join(_dir, n)
        if os.path.isdir(p) and n in EXCLUDE_DIRS:
            drop.add(n)
        elif os.path.isfile(p) and (n in EXCLUDE_FILES or os.path.splitext(n)[1] in EXCLUDE_EXT):
            drop.add(n)
    return drop


def main():
    if not os.path.isdir(os.path.join(PWA, 'assets', 'js')):
        print('PWA 源目录不存在：', PWA)
        sys.exit(1)
    if os.path.isdir(DST):
        shutil.rmtree(DST)
    os.makedirs(DST)
    for item in ['index.html', 'manifest.webmanifest', 'assets']:
        src = os.path.join(PWA, item)
        if not os.path.exists(src):
            continue
        if os.path.isdir(src):
            shutil.copytree(src, os.path.join(DST, item), ignore=ignore)
        else:
            shutil.copy2(src, os.path.join(DST, item))
    # 统计
    n = sum(len(f) for _, _, f in os.walk(DST))
    size = sum(os.path.getsize(os.path.join(r, f))
               for r, _, fs in os.walk(DST) for f in fs)
    print('synced %d files, %.1f KB -> %s' % (n, size / 1024, DST))


if __name__ == '__main__':
    main()
