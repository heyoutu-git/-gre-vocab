#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OCR 词汇 PDF 兜底解析
====================
适用：文本层损坏的 PDF（字体子集缺 ToUnicode，提取出来是乱码——如 Word 另存 PDF 的
某些 CJK 文档）。策略：pymupdf 把每页渲染成图 -> RapidOCR 离线识别 -> 逐行解析词条。

输入行形如：
    analyze[aenalaiz]vt.（美》分析，分解，解释
    approach[aprauch]n.靠近，接近 ... vt.接洽，
    交涉；着手处理vt.&vi.接近，走近，靠近        <- 续行，并入上一条
    percent[pasent]】n.百分比，百分数            <- 多余括号容忍

输出 JSON 数组（与后端 Vocabulary 对齐）：
    [{"word": "...", "phoneticIpa": "...", "pos": "...", "definition": "..."}]

依赖：pip install pymupdf rapidocr-onnxruntime
用法：python ocr_parse.py "xxx.pdf" --out vocab.json [--dpi 150] [--max-pages N]
"""
import argparse
import io
import json
import re
import sys

try:
    import pymupdf as fitz
except ImportError:
    import fitz

# 词条行：词头 [音标] 词性. 释义...
WORD_RE = re.compile(
    r"^([A-Za-z][A-Za-z\-']{1,})\s*[\[［(（]\s*([^\]］）)]{1,60}?)\s*[\]］）)]?\s*"
    r"((?:(?:vt|vi|n|adj|adv|prep|conj|pron|aux|art|num|int)\s*\.\s*(?:&\s*)?){1,4})(.*)$",
    re.S,
)
POS_RE = re.compile(r"(vt|vi|n|adj|adv|prep|conj|pron|aux|art|num|int)(?=\s*\.)")
SKIP_RE = re.compile(r"^(IELTS|Sublist\s*\d+|MAIDIKEYIXUE|麦迪可医学|Page\s*\d+)$|^[0-9\s]*$")


def parse_lines(lines):
    items = []
    for raw in lines:
        line = raw.strip()
        if not line or SKIP_RE.match(line):
            continue
        m = WORD_RE.match(line)
        if m:
            word = m.group(1).strip()
            ipa = m.group(2).strip()
            pos_raw = m.group(3)
            rest = m.group(4).strip()
            pos = " ".join(p + "." for p in POS_RE.findall(pos_raw))
            pos = pos.replace(". &.", " & ").replace(" & .", " &").strip(" .&")
            # 词头至少 2 个字母、释义非空才算有效词条
            if len(word) >= 2 and (rest or pos):
                items.append({"word": word, "phoneticIpa": ipa, "pos": pos,
                              "definition": rest if rest else pos})
                continue
        # 续行：并入上一词条释义
        if items and not m:
            items[-1]["definition"] += line
    # 同词去重（保留首条）
    seen = set()
    out = []
    for it in items:
        key = it["word"].lower()
        if key in seen:
            continue
        seen.add(key)
        out.append(it)
    return out


def ocr_pdf(path, dpi=150, max_pages=None, log=lambda s: None):
    from rapidocr_onnxruntime import RapidOCR
    ocr = RapidOCR()
    doc = fitz.open(path)
    n = len(doc) if max_pages is None else min(len(doc), max_pages)
    lines = []
    for i in range(n):
        page = doc[i]
        pix = page.get_pixmap(dpi=dpi)
        img_bytes = pix.tobytes("png")
        result, _ = ocr(img_bytes)
        page_lines = [item[1] for item in (result or [])]
        log("page %d/%d -> %d lines" % (i + 1, n, len(page_lines)))
        lines.extend(page_lines)
    doc.close()
    return parse_lines(lines)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("pdf")
    ap.add_argument("--out", default=None, help="输出 JSON 文件（缺省打印 stdout）")
    ap.add_argument("--dpi", type=int, default=150)
    ap.add_argument("--max-pages", type=int, default=None, help="只解析前 N 页（测试用）")
    args = ap.parse_args()

    def log(s):
        print(s, file=sys.stderr)

    items = ocr_pdf(args.pdf, dpi=args.dpi, max_pages=args.max_pages, log=log)
    data = json.dumps(items, ensure_ascii=False, indent=1)
    if args.out:
        with io.open(args.out, "w", encoding="utf-8") as f:
            f.write(data)
        log("total %d words -> %s" % (len(items), args.out))
    else:
        print(data)
    return 0 if items else 2


if __name__ == "__main__":
    sys.exit(main())
