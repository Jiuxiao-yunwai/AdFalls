import argparse
import base64
import csv
import json
import os
import re
import sys
import time
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

VALID_TYPES = {"IMAGE_LARGE", "IMAGE_SMALL", "VIDEO", "MIXED"}
VALID_CHANNELS = {"featured", "ecommerce", "local"}
FIELDS = [
    "title",
    "content",
    "summary",
    "tags",
    "cover_url",
    "image_urls",
    "video_url",
    "type",
    "channel",
    "target_url",
]

DEFAULT_TEXT_API_BASE_URL = "https://token-plan-cn.xiaomimimo.com/v1"
DEFAULT_TEXT_MODEL = "mimo-v2.5"
DEFAULT_IMAGE_MODEL = "gpt-image-1"


def project_root() -> Path:
    return Path(__file__).resolve().parents[2]


def env(name: str, default: str | None = None) -> str | None:
    value = os.getenv(name)
    return value if value not in (None, "") else default


def normalize_base_url(base_url: str) -> str:
    return base_url.rstrip("/")


def post_json(url: str, api_key: str, payload: dict[str, Any], timeout: int) -> dict[str, Any]:
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = Request(
        url,
        data=body,
        method="POST",
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
    )
    try:
        with urlopen(request, timeout=timeout) as response:
            return json.loads(response.read().decode("utf-8"))
    except HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"API HTTP {exc.code}: {detail}") from exc
    except URLError as exc:
        raise RuntimeError(f"API 连接失败: {exc.reason}") from exc


def extract_json_array(text: str) -> list[dict[str, Any]]:
    cleaned = text.strip()
    if cleaned.startswith("```"):
        cleaned = re.sub(r"^```(?:json)?\s*", "", cleaned)
        cleaned = re.sub(r"\s*```$", "", cleaned)

    try:
        data = json.loads(cleaned)
    except json.JSONDecodeError:
        match = re.search(r"\[[\s\S]*\]", cleaned)
        if not match:
            raise
        data = json.loads(match.group(0))

    if isinstance(data, dict) and isinstance(data.get("ads"), list):
        data = data["ads"]
    if not isinstance(data, list):
        raise ValueError("文本 API 返回内容不是广告数组")
    return data


def build_generation_prompt(count: int, start_index: int = 1) -> str:
    return f"""
请生成 {count} 条中文假广告数据，用于移动端单列广告信息流 App 测试。

只返回 JSON 数组，不要 Markdown，不要解释。每个对象必须包含这些字段：
title, content, summary, tags, type, channel, target_url

硬性要求：
1. tags 是字符串，多个标签用英文逗号分隔，例如 "耳机,降噪,学生"。
2. type 只能是 IMAGE_LARGE、IMAGE_SMALL、VIDEO、MIXED。
3. channel 只能是 featured、ecommerce、local。
4. 广告内容要像真实广告，但不要使用真实品牌名。
5. title 尽量覆盖耳机、手机、咖啡、外卖、课程、旅行、家居、运动、数码、生活服务等主题。
6. content 40 到 90 个中文字符，summary 15 到 35 个中文字符。
7. target_url 使用 https://example.com/ads/ 加英文短横线 slug。
8. 三个 channel 尽量均匀分布。
9. 这是从第 {start_index} 条开始的一批数据，请尽量避免和常见示例重复。

返回示例结构：
[
  {{
    "title": "智能降噪蓝牙耳机",
    "content": "轻量佩戴，主动降噪，适合学习、通勤和长时间在线会议使用。",
    "summary": "适合学习和通勤使用的降噪耳机。",
    "tags": "耳机,降噪,学生",
    "type": "IMAGE_LARGE",
    "channel": "featured",
    "target_url": "https://example.com/ads/noise-cancelling-headphones"
  }}
]
""".strip()


def generate_ads_with_text_api(count: int, timeout: int, start_index: int = 1) -> list[dict[str, Any]]:
    api_key = env("ADFALLS_TEXT_API_KEY")
    if not api_key:
        raise RuntimeError("缺少环境变量 ADFALLS_TEXT_API_KEY")

    base_url = normalize_base_url(env("ADFALLS_TEXT_API_BASE_URL", DEFAULT_TEXT_API_BASE_URL))
    model = env("ADFALLS_TEXT_MODEL", DEFAULT_TEXT_MODEL)
    url = f"{base_url}/chat/completions"
    payload = {
        "model": model,
        "messages": [
            {
                "role": "system",
                "content": "你是广告数据生成助手，必须输出可解析 JSON。",
            },
            {
                "role": "user",
                "content": build_generation_prompt(count, start_index),
            },
        ],
        "temperature": 0.8,
    }
    result = post_json(url, api_key, payload, timeout)
    content = result["choices"][0]["message"]["content"]
    return extract_json_array(content)


def generate_ads_in_batches(count: int, batch_size: int, timeout: int) -> list[dict[str, Any]]:
    raw_ads: list[dict[str, Any]] = []
    current = 1
    while current <= count:
        current_batch_size = min(batch_size, count - current + 1)
        print(f"[TEXT] 正在请求第 {current}-{current + current_batch_size - 1} 条广告...")
        try:
            batch = generate_ads_with_text_api(
                current_batch_size,
                timeout,
                start_index=current,
            )
            raw_ads.extend(batch)
            print(f"[TEXT] 第 {current}-{current + current_batch_size - 1} 条返回 {len(batch)} 条")
        except Exception as exc:
            print(
                f"[WARN] 第 {current}-{current + current_batch_size - 1} 条文本 API 生成失败：{exc}",
                file=sys.stderr,
            )
        current += current_batch_size
    return raw_ads


def fallback_ads(count: int) -> list[dict[str, Any]]:
    templates = [
        ("智能降噪蓝牙耳机", "耳机,降噪,学生", "featured", "IMAGE_LARGE"),
        ("轻薄长续航手机", "手机,数码,长续航", "ecommerce", "IMAGE_SMALL"),
        ("精品咖啡周末体验课", "咖啡,课程,周末", "local", "MIXED"),
        ("附近外卖满减套餐", "外卖,本地,优惠", "local", "IMAGE_LARGE"),
        ("高效学习在线课程", "课程,学习,学生", "featured", "IMAGE_SMALL"),
        ("周边短途旅行计划", "旅行,周末,城市", "featured", "MIXED"),
        ("家用空气清洁设备", "家居,健康,空气", "ecommerce", "IMAGE_LARGE"),
        ("轻量运动训练装备", "运动,健身,装备", "ecommerce", "IMAGE_SMALL"),
        ("社区摄影入门课程", "课程,摄影,本地", "local", "MIXED"),
        ("便携办公收纳套装", "办公,收纳,效率", "featured", "IMAGE_LARGE"),
    ]
    ads = []
    for index in range(count):
        title, tags, channel, ad_type = templates[index % len(templates)]
        slug = re.sub(r"[^a-z0-9-]+", "-", f"ad-{index + 1:03d}".lower()).strip("-")
        ads.append(
            {
                "title": title,
                "content": f"{title}主打日常实用体验，适合学习、通勤、办公或周末休闲场景，提供清晰明确的功能亮点和友好的入门价格。",
                "summary": f"{title}，适合日常使用的测试广告。",
                "tags": tags,
                "type": ad_type,
                "channel": channel,
                "target_url": f"https://example.com/ads/{slug}",
            }
        )
    return ads


def image_prompt(ad: dict[str, str]) -> str:
    return f"""
生成一张适合移动端广告信息流的商品宣传图。
主题：{ad["title"]}
内容：{ad["summary"]}
风格：干净、现代、商业广告摄影风，适合 App 信息流封面。
画面：主体明确，背景简洁，光线柔和。
要求：不要出现真实品牌 Logo，不要出现文字，不要出现水印，不要出现乱码。
比例：横向封面图。
""".strip()


def validate_and_complete_ads(raw_ads: list[dict[str, Any]], count: int) -> list[dict[str, str]]:
    ads = []
    for raw in raw_ads:
        index = len(ads) + 1
        if index > count:
            break
        title = str(raw.get("title", "")).strip()
        content = str(raw.get("content", "")).strip()
        summary = str(raw.get("summary", "")).strip()
        tags = str(raw.get("tags", "")).replace("，", ",").strip()
        ad_type = str(raw.get("type", "IMAGE_LARGE")).strip()
        channel = str(raw.get("channel", "featured")).strip()
        target_url = str(raw.get("target_url", "")).strip()

        if not title or not content or not summary or not tags:
            print(f"[WARN] 第 {index} 条广告字段不完整，已跳过", file=sys.stderr)
            continue
        if ad_type not in VALID_TYPES:
            print(f"[WARN] 第 {index} 条广告 type={ad_type} 不合法，改为 IMAGE_LARGE", file=sys.stderr)
            ad_type = "IMAGE_LARGE"
        if channel not in VALID_CHANNELS:
            print(f"[WARN] 第 {index} 条广告 channel={channel} 不合法，改为 featured", file=sys.stderr)
            channel = "featured"
        if not target_url:
            target_url = f"https://example.com/ads/ad-{index:03d}"

        cover_path = f"materials/images/ad_{index:03d}_cover.png"
        ads.append(
            {
                "title": title,
                "content": content,
                "summary": summary,
                "tags": tags,
                "cover_url": cover_path,
                "image_urls": cover_path,
                "video_url": "",
                "type": ad_type,
                "channel": channel,
                "target_url": target_url,
            }
        )
        print(f"[AD {index:03d}/{count:03d}] {title} | {channel} | {ad_type}")
    return ads


def generate_image(prompt: str, output_path: Path, timeout: int) -> bool:
    api_key = env("ADFALLS_IMAGE_API_KEY")
    base_url = env("ADFALLS_IMAGE_API_BASE_URL")
    if not api_key or not base_url:
        return False

    model = env("ADFALLS_IMAGE_MODEL", DEFAULT_IMAGE_MODEL)
    url = f"{normalize_base_url(base_url)}/images/generations"
    payload = {
        "model": model,
        "prompt": prompt,
        "size": env("ADFALLS_IMAGE_SIZE", "1536x1024"),
        "n": 1,
    }
    try:
        result = post_json(url, api_key, payload, timeout)
        item = result["data"][0]
        if "b64_json" in item:
            output_path.write_bytes(base64.b64decode(item["b64_json"]))
            return True
        if "url" in item:
            print(f"[WARN] 图片 API 返回 URL，未下载：{item['url']}", file=sys.stderr)
            return False
    except Exception as exc:
        print(f"[WARN] 图片生成失败，使用占位路径：{exc}", file=sys.stderr)
    return False


def write_outputs(root: Path, ads: list[dict[str, str]], prompts: list[dict[str, str]]) -> None:
    data_dir = root / "data"
    images_dir = root / "materials" / "images"
    videos_dir = root / "materials" / "videos"
    data_dir.mkdir(parents=True, exist_ok=True)
    images_dir.mkdir(parents=True, exist_ok=True)
    videos_dir.mkdir(parents=True, exist_ok=True)

    (data_dir / "ads.json").write_text(
        json.dumps(ads, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    (data_dir / "image_prompts.json").write_text(
        json.dumps(prompts, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    with (data_dir / "ads.csv").open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(ads)


def main() -> int:
    parser = argparse.ArgumentParser(description="生成 AdFalls 测试广告数据")
    parser.add_argument("--count", type=int, default=15, help="广告数量，默认 15")
    parser.add_argument(
        "--root",
        type=Path,
        default=project_root(),
        help="输出根目录，默认项目根目录",
    )
    parser.add_argument(
        "--use-fallback",
        action="store_true",
        help="不调用文本 API，直接使用本地规则生成",
    )
    parser.add_argument(
        "--batch-size",
        type=int,
        default=10,
        help="文本 API 每批生成数量，默认 10；设为 1 可逐条请求",
    )
    parser.add_argument(
        "--generate-images",
        action="store_true",
        help="尝试调用图片 API 生成封面图；默认只生成图片路径和提示词",
    )
    parser.add_argument("--timeout", type=int, default=60, help="API 超时时间，默认 60 秒")
    args = parser.parse_args()

    if args.count <= 0:
        print("[ERROR] --count 必须大于 0", file=sys.stderr)
        return 1
    if args.batch_size <= 0:
        print("[ERROR] --batch-size 必须大于 0", file=sys.stderr)
        return 1

    if args.use_fallback:
        raw_ads = fallback_ads(args.count)
    else:
        raw_ads = generate_ads_in_batches(args.count, args.batch_size, args.timeout)
        if len(raw_ads) < args.count:
            missing_count = args.count - len(raw_ads)
            print(
                f"[WARN] 文本 API 只生成 {len(raw_ads)} 条，剩余 {missing_count} 条使用本地兜底数据",
                file=sys.stderr,
            )
            raw_ads.extend(fallback_ads(missing_count))

    ads = validate_and_complete_ads(raw_ads, args.count)
    prompts = [{"cover_url": ad["cover_url"], "prompt": image_prompt(ad)} for ad in ads]

    if args.generate_images:
        images_dir = args.root / "materials" / "images"
        images_dir.mkdir(parents=True, exist_ok=True)
        for item in prompts:
            output_path = args.root / item["cover_url"]
            ok = generate_image(item["prompt"], output_path, args.timeout)
            status = "OK" if ok else "SKIP"
            print(f"[{status}] {item['cover_url']}")
            time.sleep(0.2)

    write_outputs(args.root, ads, prompts)
    print(f"生成完成：{len(ads)} 条广告")
    print(f"- {args.root / 'data' / 'ads.json'}")
    print(f"- {args.root / 'data' / 'ads.csv'}")
    print(f"- {args.root / 'data' / 'image_prompts.json'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
