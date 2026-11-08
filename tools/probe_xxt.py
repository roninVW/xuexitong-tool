#!/usr/bin/env python3
# -*- coding: utf-8 -*-
r"""学习通作业接口探针 (M0 验证用)。

用途：在写 Android 代码之前，先用你自己的 Cookie 验证「课程 / 作业 / 考试 /
状态 / 截止时间」能不能拿到，并把真实 HTML 存成单测 fixture。

零第三方依赖（只用标准库），Python 3.8+。

用法示例
--------
  # 1) 只验证登录态与课程列表
  python probe_xxt.py --cookie "_uid=...; _d=...; uf=..." --courses

  # 2) 拉全部课程的作业/考试列表（不抓详情，快）
  python probe_xxt.py --cookie "..." --tasks

  # 3) 顺带补详情（截止时间/分数），最多 20 条
  python probe_xxt.py --cookie "..." --tasks --detail --limit 20

  # 4) 只验证一门课，并把 HTML 存下来做 fixture
  python probe_xxt.py --cookie "..." --tasks --course 高等数学 --dump .\probe_out

  # 5) 离线自检解析器（不需要网络，也不需要 Cookie）
  python probe_xxt.py --selftest

安全提醒：--cookie 的值等价于你的登录态，不要贴到聊天/Issue/截图里。
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta
from html import unescape

# ---------------------------------------------------------------- 常量

MOOC = "https://mooc1-api.chaoxing.com"
REFERER = "https://mooc1.chaoxing.com/"

UA = (
    "Mozilla/5.0 (Linux; Android 14; Pixel 6) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
)

STATUS_LABEL = {
    1: "未交",
    2: "已过期",
    3: "待批阅",
    4: "已完成",
}
STATUS_ENUM = {
    1: "TODO",
    2: "EXPIRED",
    3: "SUBMITTED",
    4: "GRADED",
}

# ---------------------------------------------------------------- 小工具


def say(*args):
    print(*args)


# Windows 控制台常是 GBK，遇到 ✅ 这类字符会直接抛 UnicodeEncodeError。
# 这里只把 errors 改成 replace（保留原编码，中文仍正常显示），不影响重定向到文件。
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(errors="replace")
    except Exception:  # 老 Python 或非常规流
        pass


def decode_body(raw: bytes, content_type: str = "") -> str:
    """学习通多数页面是 UTF-8，少数泛雅老页面是 GBK。"""
    m = re.search(r"charset=([\w-]+)", content_type or "", re.I)
    if m:
        enc = m.group(1).lower()
        try:
            return raw.decode(enc, "replace")
        except LookupError:
            pass
    for enc in ("utf-8", "gb18030"):
        try:
            return raw.decode(enc)
        except UnicodeDecodeError:
            continue
    return raw.decode("utf-8", "replace")


def strip_tags(html: str) -> str:
    text = re.sub(r"<(script|style)\b.*?</\1>", " ", html, flags=re.S | re.I)
    text = re.sub(r"<[^>]+>", " ", text)
    text = unescape(text)
    return re.sub(r"\s+", " ", text).strip()


def attr(attrs: str, name: str):
    m = re.search(r'\b%s\s*=\s*"([^"]*)"' % re.escape(name), attrs, re.I)
    if m:
        return unescape(m.group(1)).strip()
    m = re.search(r"\b%s\s*=\s*'([^']*)'" % re.escape(name), attrs, re.I)
    if m:
        return unescape(m.group(1)).strip()
    m = re.search(r"\b%s\s*=\s*([^\s>]+)" % re.escape(name), attrs, re.I)
    return m.group(1).strip() if m else None


def normalize_url(path: str) -> str:
    p = (path or "").strip()
    if not p:
        return ""
    if p.startswith("http://") or p.startswith("https://"):
        return p
    if p.startswith("//"):
        return "https:" + p
    if p.startswith("/"):
        return MOOC + p
    return MOOC + "/" + p


# ---------------------------------------------------------------- HTTP


class ProbeError(Exception):
    pass


def http_get(url: str, cookie: str, referer: str = REFERER, timeout: int = 25) -> str:
    req = urllib.request.Request(url)
    req.add_header("User-Agent", UA)
    req.add_header("Accept", "*/*")
    req.add_header("Accept-Language", "zh-CN,zh;q=0.9")
    # 不让服务端 gzip，标准库不自动解压
    req.add_header("Accept-Encoding", "identity")
    if referer:
        req.add_header("Referer", referer)
    if cookie:
        req.add_header("Cookie", cookie)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read()
            return decode_body(raw, resp.headers.get("Content-Type", ""))
    except urllib.error.HTTPError as e:
        raise ProbeError("HTTP %s  %s" % (e.code, url)) from None
    except urllib.error.URLError as e:
        raise ProbeError("网络错误 %s  %s" % (e.reason, url)) from None
    except TimeoutError:
        raise ProbeError("超时  %s" % url) from None


# ---------------------------------------------------------------- 课程


def extract_json_object(text: str):
    start, end = text.find("{"), text.rfind("}")
    if start < 0 or end <= start:
        return None
    try:
        return json.loads(text[start : end + 1])
    except json.JSONDecodeError:
        return None


def fetch_courses(cookie: str, timeout: int = 25):
    url = MOOC + "/mycourse/backclazzdata?view=json&getTchClazzType=1&mcode="
    text = http_get(url, cookie, referer="https://i.chaoxing.com", timeout=timeout)
    obj = extract_json_object(text)
    if obj is None:
        head = strip_tags(text)[:120]
        raise ProbeError(
            "课程接口返回的不是 JSON（多半是 Cookie 失效或不全）。开头：%r" % head
        )
    if obj.get("result") != 1:
        raise ProbeError(
            "课程接口 result=%r msg=%r —— 登录态无效"
            % (obj.get("result"), obj.get("msg"))
        )

    out, seen = [], set()
    for ch in obj.get("channelList") or []:
        content = ch.get("content")
        if isinstance(content, str):
            content = extract_json_object(content)
        if not isinstance(content, dict):
            continue
        cpi = content.get("cpi") or ch.get("cpi") or ""
        course_data = (content.get("course") or {}).get("data") or []
        if course_data:
            class_id = content.get("id") or ch.get("key") or ""
            for c in course_data:
                cid = str(c.get("id") or "")
                if not cid:
                    continue
                item = (cid, str(class_id), c.get("name") or "", str(cpi))
                if item[:2] not in seen:
                    seen.add(item[:2])
                    out.append(item)
        else:
            cid = str(content.get("id") or "")
            for c in content.get("clazz") or []:
                if not cid:
                    continue
                item = (cid, str(c.get("id") or ""), content.get("name") or "", str(cpi))
                if item[:2] not in seen:
                    seen.add(item[:2])
                    out.append(item)
    return out


# ---------------------------------------------------------------- 列表解析

DT_RE = re.compile(r"<dt\b([^>]*)>(.*?)</dt>", re.S | re.I)
TR_RE = re.compile(r"<tr\b([^>]*)>(.*?)</tr>", re.S | re.I)
TD_RE = re.compile(r"<td\b[^>]*>(.*?)</td>", re.S | re.I)
H3_RE = re.compile(r"<h3\b[^>]*>(.*?)</h3>", re.S | re.I)
P_RE = re.compile(r"<p\b[^>]*>(.*?)</p>", re.S | re.I)
B_RE = re.compile(r"<b\b[^>]*>(.*?)</b>", re.S | re.I)

TIME_RE = re.compile(
    r"(\d{4}\s*[-/年]\s*\d{1,2}\s*[-/月]\s*\d{1,2}\s*日?"
    r"(?:\s*\d{1,2}\s*[:：]\s*\d{1,2}(?:\s*[:：]\s*\d{1,2})?)?)"
)


def parse_time(text: str):
    """宽松解析学习通的时间文案，返回 datetime 或 None（按设备本地时区理解）。"""
    if not text:
        return None
    m = TIME_RE.search(text)
    if not m:
        return None
    s = re.sub(r"\s+", " ", m.group(1)).strip()
    s = s.replace("年", "-").replace("月", "-").replace("日", " ")
    s = s.replace("/", "-").replace("：", ":")
    for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%d %H:%M", "%Y-%m-%d"):
        try:
            return datetime.strptime(s.strip(), fmt)
        except ValueError:
            continue
    return None


def infer_status(text: str) -> int:
    t = text or ""
    if "已完成" in t or "已批阅" in t:
        return 4
    # 「待批阅」「等待教师批阅」「批阅中」都是同一态
    if "待批阅" in t or "批阅中" in t or ("批阅" in t and "待" in t):
        return 3
    if "过期" in t:
        return 2
    return 1


def parse_work_list(html: str):
    items = []
    for attrs, inner in DT_RE.findall(html):
        data = attr(attrs, "data")
        if not data:
            continue
        h3 = H3_RE.search(inner)
        title = strip_tags(h3.group(1)) if h3 else ""
        if not title:
            continue
        p = P_RE.search(inner)
        b = B_RE.search(inner)
        action = strip_tags(b.group(1)) if b else ""
        raw_status = attr(attrs, "status")
        try:
            code = int(raw_status) if raw_status else infer_status(action)
        except ValueError:
            code = infer_status(action)
        items.append(
            {
                "title": title,
                "status": code,
                "status_text": action or STATUS_LABEL.get(code, ""),
                "publish_text": strip_tags(p.group(1)) if p else "",
                "publish_at": parse_time(p.group(1)) if p else None,
                "url": normalize_url(data),
            }
        )
    return items


def parse_exam_list(html: str):
    items = []
    for attrs, inner in TR_RE.findall(html):
        data = attr(attrs, "data")
        if not data:
            continue
        cells = [strip_tags(td) for td in TD_RE.findall(inner)]
        title = (attr(attrs, "title") or (cells[0] if cells else "")).strip()
        if not title:
            continue
        status_text = cells[2] if len(cells) > 2 else ""
        raw_status = attr(attrs, "status")
        try:
            code = int(raw_status) if raw_status else infer_status(status_text)
        except ValueError:
            code = infer_status(status_text)
        score = cells[1] if len(cells) > 1 else ""
        if score in ("—", "-", "--"):
            score = ""
        items.append(
            {
                "title": title,
                "status": code,
                "status_text": status_text,
                "score": score if code == 4 else "",
                "url": normalize_url(data),
            }
        )
    return items


# ---------------------------------------------------------------- 详情解析


def parse_detail(html: str):
    text = strip_tags(html)
    out = {"start_at": None, "due_at": None, "submit_at": None, "score": ""}

    def grab(keywords):
        for kw in keywords:
            m = re.search(re.escape(kw) + r"\s*[:：]?\s*" + TIME_RE.pattern, text)
            if m:
                dt = parse_time(m.group(1))
                if dt:
                    return dt
        return None

    out["start_at"] = grab(["开始时间", "考试时间"])
    out["due_at"] = grab(["截止时间", "结束时间"])
    out["submit_at"] = grab(["提交时间"])

    m = re.search(r"(?:本次成绩|得分|成绩)\s*[:：]?\s*(\d+(?:\.\d+)?)\s*分?", text)
    if m:
        out["score"] = m.group(1)
    return out


# ---------------------------------------------------------------- 输出


def human_left(dt, now=None):
    if not dt:
        return "截止未知"
    now = now or datetime.now()
    delta = dt - now
    secs = int(delta.total_seconds())
    if secs <= 0:
        days = abs(secs) // 86400
        return "已过期%s" % (" %d 天" % days if days else "")
    d, rem = divmod(secs, 86400)
    h, rem = divmod(rem, 3600)
    m = rem // 60
    if d:
        return "剩 %d天%d时" % (d, h)
    if h:
        return "剩 %d时%d分" % (h, m)
    return "剩 %d分" % m


def print_courses(courses):
    say("[课程] %d 门" % len(courses))
    for i, (cid, clsid, name, cpi) in enumerate(courses, 1):
        say("  %2d. %-32s courseId=%-10s classId=%-10s cpi=%s" % (i, name, cid, clsid, cpi))


# ---------------------------------------------------------------- 主流程


def run(args):
    cookie = args.cookie or ""
    if args.cookie_file:
        with open(args.cookie_file, "r", encoding="utf-8") as f:
            cookie = f.read().strip()
    if not cookie and not args.selftest:
        say("错误：需要 --cookie 或 --cookie-file（登录 i.chaoxing.com 后从浏览器复制）")
        return 2

    say("[会话] 检查登录态 …")
    courses = fetch_courses(cookie, args.timeout)
    say("[会话] backclazzdata result=1  ✅ 已登录")
    if args.course:
        courses = [c for c in courses if args.course in c[2]]
        if not courses:
            say("没有课程名包含 %r" % args.course)
            return 1
    print_courses(courses)
    if args.courses and not args.tasks:
        return 0

    dump_dir = args.dump
    if dump_dir:
        os.makedirs(dump_dir, exist_ok=True)

    totals = {"work": 0, "exam": 0}
    dist = {1: 0, 2: 0, 3: 0, 4: 0}
    need_detail = []

    for cid, clsid, name, cpi in courses:
        for kind, path in (("work", "workList"), ("exam", "examList")):
            url = "%s/phone/moocAnalysis/%s?courseId=%s&classId=%s" % (MOOC, path, cid, clsid)
            try:
                html = http_get(url, cookie, timeout=args.timeout)
            except ProbeError as e:
                say("  [失败] %s %s：%s" % (name, path, e))
                continue
            if dump_dir:
                fn = os.path.join(dump_dir, "%s_%s_%s.html" % (cid, clsid, kind))
                with open(fn, "w", encoding="utf-8") as f:
                    f.write(html)
            items = parse_work_list(html) if kind == "work" else parse_exam_list(html)
            totals[kind] += len(items)
            counts = {k: 0 for k in dist}
            for it in items:
                counts[it["status"]] = counts.get(it["status"], 0) + 1
                dist[it["status"]] = dist.get(it["status"], 0) + 1
            say(
                "[%s] %s  %s: %d 条  (未交 %d / 已过期 %d / 待批阅 %d / 已完成 %d)"
                % (
                    "作业" if kind == "work" else "考试",
                    name,
                    path,
                    len(items),
                    counts[1],
                    counts[2],
                    counts[3],
                    counts[4],
                )
            )
            for it in items:
                flag = {1: "未交  ", 2: "已过期", 3: "待批阅", 4: "已完成"}[it["status"]]
                extra = ""
                if kind == "exam" and it.get("score"):
                    extra = "  得分 %s" % it["score"]
                say("   [%s] %s%s" % (flag, it["title"][:40], extra))
                if kind == "work" and it["status"] in (1, 2) and it.get("url"):
                    need_detail.append((name, it))
            time.sleep(args.delay)

    say(
        "[汇总] 作业 %d / 考试 %d ；未交 %d ；待批阅 %d ；已完成 %d ；已过期 %d"
        % (totals["work"], totals["exam"], dist[1], dist[3], dist[4], dist[2])
    )

    if not args.detail:
        if need_detail:
            say("提示：加 --detail 可补截止时间（当前有 %d 条未交/过期需要补）" % len(need_detail))
        return 0

    limit = args.limit or 20
    todo = need_detail[:limit]
    say("[详情] 抓取 %d/%d 条 …" % (len(todo), len(need_detail)))
    ok = 0
    for name, it in todo:
        try:
            html = http_get(it["url"], cookie, timeout=args.timeout)
        except ProbeError as e:
            say("   [失败] %s：%s" % (it["title"][:30], e))
            continue
        d = parse_detail(html)
        ok += 1
        it["due_at"] = d["due_at"]
        say(
            "   %-30s 截止 %s  (%s)%s"
            % (
                it["title"][:30],
                d["due_at"].strftime("%Y-%m-%d %H:%M") if d["due_at"] else "未找到",
                human_left(d["due_at"]),
                ("  成绩 %s" % d["score"]) if d["score"] else "",
            )
        )
        time.sleep(args.delay)
    say("[详情] 成功 %d/%d" % (ok, len(todo)))

    if args.json:
        payload = [
            {"course": name, "title": it["title"], "status": STATUS_ENUM.get(it["status"]),
             "url": it["url"],
             "due_at": it.get("due_at").isoformat() if it.get("due_at") else None}
            for name, it in need_detail
        ]
        with open(args.json, "w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False, indent=2)
        say("[导出] %s" % args.json)
    return 0


# ---------------------------------------------------------------- 自检

SAMPLE_WORK = """
<div class="workList"><dl>
  <dt data="/work/phone/task-work?courseId=1001&amp;classId=2001&amp;workId=3001" status="1">
    <h3>第七章 微分方程作业</h3>
    <p>发布时间：2026-11-01 08:00</p>
    <b>未交</b>
  </dt>
  <dt data="/work/phone/task-work?workId=3002" status="3">
    <h3>第六章 定积分</h3><p>发布时间：2026-10-20 09:30</p><b>待批阅</b>
  </dt>
  <dt data="/work/phone/task-work?workId=3003" status="2">
    <h3>第五章 不定积分</h3><p>发布时间：2026-10-01 10:00</p><b>已过期</b>
  </dt>
  <dt data="/work/phone/task-work?workId=3004" status="4">
    <h3>第四章 中值定理</h3><p>发布时间：2026-09-15 14:00</p><b>已完成</b>
  </dt>
</dl></div>
"""

SAMPLE_EXAM = """
<table><tbody>
  <tr data="/exam/phone/task-exam?examId=1" status="4" title="期中考试">
    <td>期中考试</td><td>92</td><td>已完成</td>
  </tr>
  <tr data="/exam/phone/task-exam?examId=2" status="1" title="单元测验一">
    <td>单元测验一</td><td>—</td><td>未交</td>
  </tr>
</tbody></table>
"""

SAMPLE_DETAIL = """
<div class="detail">
  <p>开始时间：2026-11-01 08:00</p>
  <p>截止时间：2026-11-15 23:59</p>
  <p>本次成绩：88 分</p>
</div>
"""


def selftest():
    fails = []

    def check(cond, msg):
        if cond:
            say("  ✅ %s" % msg)
        else:
            say("  ❌ %s" % msg)
            fails.append(msg)

    say("[自检] 作业列表解析")
    works = parse_work_list(SAMPLE_WORK)
    check(len(works) == 4, "解析出 4 条（实际 %d）" % len(works))
    check([w["status"] for w in works] == [1, 3, 2, 4], "状态映射 1/3/2/4 正确")
    check(works[0]["title"] == "第七章 微分方程作业", "标题提取正确")
    check(works[0]["url"].startswith(MOOC + "/work/phone/task-work?"), "相对 URL 补全正确")
    check("&classId=2001" in works[0]["url"], "HTML 实体 &amp; 已还原")
    check(works[0]["publish_at"] == datetime(2026, 11, 1, 8, 0), "发布时间解析正确")

    say("[自检] 考试列表解析")
    exams = parse_exam_list(SAMPLE_EXAM)
    check(len(exams) == 2, "解析出 2 条（实际 %d）" % len(exams))
    check(exams[0]["score"] == "92", "已完成考试带分数")
    check(exams[1]["score"] == "", "未交考试不显示分数（— 被过滤）")
    check(exams[1]["title"] == "单元测验一", "title 属性优先取标题")

    say("[自检] 详情解析")
    d = parse_detail(SAMPLE_DETAIL)
    check(d["start_at"] == datetime(2026, 11, 1, 8, 0), "开始时间")
    check(d["due_at"] == datetime(2026, 11, 15, 23, 59), "截止时间")
    check(d["score"] == "88", "成绩")

    say("[自检] 状态兜底推断")
    check(infer_status("等待教师批阅") == 3, "「等待教师批阅」→ 待批阅")
    check(infer_status("已过期") == 2, "「已过期」→ 已过期")
    check(infer_status("未交") == 1, "「未交」→ 未交")

    say("")
    if fails:
        say("自检失败 %d 项" % len(fails))
        return 1
    say("自检全部通过 ✅（解析器可用，接下来用真实 Cookie 跑 --courses）")
    return 0


# ---------------------------------------------------------------- 入口


def main(argv=None):
    p = argparse.ArgumentParser(
        description="学习通作业接口探针 (M0)", formatter_class=argparse.RawDescriptionHelpFormatter
    )
    p.add_argument("--cookie", help='浏览器复制的 Cookie，形如 "_uid=..; _d=..; uf=.."')
    p.add_argument("--cookie-file", help="从文件读 Cookie（一行）")
    p.add_argument("--courses", action="store_true", help="只看课程列表")
    p.add_argument("--tasks", action="store_true", help="拉作业/考试列表")
    p.add_argument("--detail", action="store_true", help="补详情（截止时间/分数）")
    p.add_argument("--limit", type=int, default=20, help="补详情的最大条数，默认 20")
    p.add_argument("--course", help="只处理课程名包含该关键字的课程")
    p.add_argument("--dump", help="把原始 HTML 存到该目录（做 fixture 用）")
    p.add_argument("--json", help="把未交/过期条目导出为 JSON")
    p.add_argument("--delay", type=float, default=0.4, help="请求间隔秒，默认 0.4")
    p.add_argument("--timeout", type=int, default=25, help="单请求超时秒，默认 25")
    p.add_argument("--selftest", action="store_true", help="离线自检解析器")
    args = p.parse_args(argv)

    if args.selftest:
        return selftest()
    try:
        return run(args)
    except ProbeError as e:
        say("[错误] %s" % e)
        return 1
    except KeyboardInterrupt:
        say("\n已中断")
        return 130


if __name__ == "__main__":
    sys.exit(main())
