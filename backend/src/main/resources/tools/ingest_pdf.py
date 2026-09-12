#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
GRE 词汇 PDF 解析与导入工具
================================
把《美国学生用的GRE重要词汇及例句表》PDF 解析为结构化词汇，自动识别：
    word(单词) / phonetic(Barron's ASCII 音标) / phonetic_ipa(标准 IPA) /
    pos(词性) / inflection(变形) / definition(释义) / example(例句)

输出：
    1) 默认打印统计到 stderr，结构化 JSON 数组打印到 stdout；
    2) --out vocab.json  写到文件；
    3) --post http://.../api/admin/import --token <JWT>  直接 POST 到后端批量导入接口。

依赖：pymupdf  (本机已装于 managed venv 的 pkgs 目录，或 pip install pymupdf)
用法示例：
    python tools/ingest_pdf.py "美国学生用的GRE重要词汇及例句表.pdf" --out vocab.json
    python tools/ingest_pdf.py "xxx.pdf" --post http://127.0.0.1:8080/gre-vocab/api/admin/import --token $TOKEN
"""
import argparse
import json
import re
import sys

try:
    import pymupdf as fitz  # PyMuPDF >=1.24 推荐用法
except ImportError:
    import fitz  # 旧版兼容


# ---------------------------------------------------------------------------
# Barron's ASCII 音标 -> 标准 IPA 的启发式映射（用户已知可能不完美）
# ---------------------------------------------------------------------------
def barron_to_ipa(barron: str) -> str:
    if not barron:
        return ""
    s = barron.strip()

    # 用不可见控制字符占位，避免多字符替换后被后续单字符表破坏
    # 例如 "ei" -> "eɪ"，如果直接替换，e 会被单字符表再转成 ɛ，得到 "ɛɪ"
    PH = {
        "R:": "\x00",   # ɔːr (Barron's R 已含 r，: 是冗余长度)
        "dY": "\x01",   # dʒ (Y 单表会映射成 j，必须整体替换)
        "i:": "\x02",   # iː
        "u:": "\x03",   # uː
        "ai": "\x04",   # aɪ
        "ei": "\x05",   # eɪ
        "au": "\x06",   # aʊ
        "ou": "\x07",   # aʊ
        "oi": "\x08",   # ɔɪ
        "oy": "\x09",   # ɔɪ
    }
    RESTORE = {
        "\x00": "ɔːr",
        "\x01": "dʒ",
        "\x02": "iː",
        "\x03": "uː",
        "\x04": "aɪ",
        "\x05": "eɪ",
        "\x06": "aʊ",
        "\x07": "aʊ",
        "\x08": "ɔɪ",
        "\x09": "ɔɪ",
    }
    for k, v in PH.items():
        s = s.replace(k, v)

    table = {
        "5": "ˈ", ":": "ː",
        "9": "ˌ", ",": "ˌ", "`": "ˈ",  # Barron's ASCII 重音标记
        "A": "æ", "E": "ə", "I": "aɪ", "O": "oʊ", "U": "uː",
        "Q": "ʌ",  # Barron's ASCII 用 Q 表示 /ʌ/
        "B": "ɑ",  # Barron's ASCII 用 B 表示 /ɑ/，配合 ':' 成长音 /ɑː/ (如 barb [bB:d])
        "Z": "ɛ",  # Barron's ASCII 用 Z 表示 /ɛ/ (如 deference, defection)
        "F": "ʃ", "N": "ŋ", "R": "ɔːr",
        "C": "tʃ", "J": "dʒ", "V": "ʒ",
        "T": "θ", "D": "ð", "W": "w", "Y": "j", "H": "h",
        "a": "ə", "e": "ɛ", "i": "ɪ", "o": "ɒ", "u": "ʊ",
    }
    s = "".join(table.get(ch, ch) for ch in s)

    for k, v in RESTORE.items():
        s = s.replace(k, v)
    return s


# ---------------------------------------------------------------------------
# 解析
# ---------------------------------------------------------------------------
HEAD_RE = re.compile(r"^([A-Za-z][A-Za-z\-']*)\s+\[([^\]]+)\]")
POS_RE = re.compile(r"^(v|adj|n|adv|ad|prep|conj|pron|art|vt|vi|int|abbr)\b\.?\s*(.*)$", re.I)
INFL_RE = re.compile(r"^\[([^\]]+)\]\s*(.*)$")
# PDF 页脚/水印，需跳过，否则会被误当成例句或词性行
FOOTER_RE = re.compile(r"满分网|manfen\.net|www\.|http", re.I)


def parse_pdf(path: str):
    doc = fitz.open(path)
    text = "\n".join(page.get_text("text") for page in doc)
    doc.close()

    lines = text.splitlines()
    entries = []
    i = 0
    n = len(lines)
    while i < n:
        m = HEAD_RE.match(lines[i].strip())
        if not m:
            i += 1
            continue
        word = m.group(1)
        phonetic = m.group(2).strip()
        j = i + 1
        pos = inflection = definition = None

        # 跳过单词行与词性行之间的页脚/空行
        while j < n and (not lines[j].strip() or FOOTER_RE.search(lines[j])):
            j += 1
        mm = POS_RE.match(lines[j].strip()) if j < n else None
        if mm:
            pos = mm.group(1).lower()
            rest = mm.group(2)
            im = INFL_RE.match(rest)
            if im:
                inflection = im.group(1).strip()
                definition = im.group(2).strip()
            else:
                definition = rest.strip()
            j += 1

        # 例句：从词性行之后开始，跳过页脚/空行；收集直到下一条 head。
        # PDF 中页脚（满分网/manfen.net）后常有一个空行，随后才是例句，
        # 因此不能以空行作为结束条件，必须以下一个单词 head 作为边界。
        ex_lines = []
        while j < n and not HEAD_RE.match(lines[j].strip()):
            line = lines[j].strip()
            if line and not FOOTER_RE.search(lines[j]):
                ex_lines.append(line)
            j += 1
        example = " ".join(ex_lines).strip()
        entries.append({
            "word": word,
            "phonetic": phonetic,
            "phoneticIpa": barron_to_ipa(phonetic),
            "pos": pos or "",
            "inflection": inflection or "",
            "definition": definition or "",
            "example": example,
        })
        i = j
    return entries


def main():
    ap = argparse.ArgumentParser(description="GRE 词汇 PDF 解析")
    ap.add_argument("pdf", help="PDF 文件路径")
    ap.add_argument("--out", help="输出 JSON 文件路径")
    ap.add_argument("--post", help="直接 POST 到后端导入接口 URL")
    ap.add_argument("--token", help="管理员 JWT（配合 --post）")
    ap.add_argument("--batch", type=int, default=50, help="POST 时每课时词数（默认 50）")
    args = ap.parse_args()

    entries = parse_pdf(args.pdf)
    if not entries:
        print("未解析到任何词条，请检查 PDF 格式。", file=sys.stderr)
        sys.exit(1)

    print(f"解析完成：共 {len(entries)} 条词汇", file=sys.stderr)
    miss = [e for e in entries if not e["pos"] or not e["definition"]]
    if miss:
        print(f"警告：{len(miss)} 条缺少词性或释义（示例：{miss[0]['word']}）", file=sys.stderr)

    if args.post:
        import urllib.request
        payload = json.dumps({"batchSize": args.batch, "vocabularies": entries}).encode("utf-8")
        req = urllib.request.Request(args.post, data=payload, method="POST")
        req.add_header("Content-Type", "application/json")
        if args.token:
            req.add_header("Authorization", "Bearer " + args.token)
        try:
            with urllib.request.urlopen(req, timeout=120) as resp:
                body = resp.read().decode("utf-8")
            print("导入响应：", body, file=sys.stderr)
        except Exception as e:
            print("POST 失败：", e, file=sys.stderr)
            sys.exit(2)
    elif args.out:
        with open(args.out, "w", encoding="utf-8") as f:
            json.dump(entries, f, ensure_ascii=False, indent=1)
        print(f"已写入 {args.out}", file=sys.stderr)
    else:
        print(json.dumps(entries, ensure_ascii=False))


if __name__ == "__main__":
    main()
