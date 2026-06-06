import json
import os
import re
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from app.models import Ad

DEFAULT_TEXT_API_BASE_URL = "https://token-plan-cn.xiaomimimo.com/v1"
DEFAULT_TEXT_MODEL = "mimo-v2.5"
DEFAULT_TIMEOUT_SECONDS = 60


def recommend_ad_ids_with_ai(message: str, candidates: list[Ad]) -> tuple[str, list[int]]:
    load_env_file()
    api_key = os.getenv("ADFALLS_TEXT_API_KEY")
    if not api_key:
        raise RuntimeError("缺少环境变量 ADFALLS_TEXT_API_KEY")

    base_url = os.getenv("ADFALLS_TEXT_API_BASE_URL", DEFAULT_TEXT_API_BASE_URL).rstrip("/")
    model = os.getenv("ADFALLS_TEXT_MODEL", DEFAULT_TEXT_MODEL)
    timeout = int(os.getenv("ADFALLS_TEXT_API_TIMEOUT", str(DEFAULT_TIMEOUT_SECONDS)))
    payload = {
        "model": model,
        "messages": [
            {
                "role": "system",
                "content": (
                    "你是广告推荐排序助手。你只能从用户提供的候选广告中选择。"
                    "必须返回严格 JSON，不要 Markdown，不要解释。"
                ),
            },
            {
                "role": "user",
                "content": build_prompt(message, candidates),
            },
        ],
        "temperature": 0.2,
    }
    response = post_json(f"{base_url}/chat/completions", api_key, payload, timeout)
    content = response["choices"][0]["message"]["content"]
    result = parse_model_json(content)
    reply = str(result.get("reply") or "已为你找到最相关的广告。")
    ad_ids = normalize_ids(result.get("adIds") or result.get("ids"), candidates)
    if not ad_ids:
        raise RuntimeError("AI 未返回有效广告 id")
    return reply, ad_ids[:3]


def analyze_ad_with_ai(message: str, ad: Ad) -> str:
    load_env_file()
    api_key = os.getenv("ADFALLS_TEXT_API_KEY")
    if not api_key:
        raise RuntimeError("缺少环境变量 ADFALLS_TEXT_API_KEY")

    base_url = os.getenv("ADFALLS_TEXT_API_BASE_URL", DEFAULT_TEXT_API_BASE_URL).rstrip("/")
    model = os.getenv("ADFALLS_TEXT_MODEL", DEFAULT_TEXT_MODEL)
    timeout = int(os.getenv("ADFALLS_TEXT_API_TIMEOUT", str(DEFAULT_TIMEOUT_SECONDS)))
    payload = {
        "model": model,
        "messages": [
            {
                "role": "system",
                "content": (
                    "你是广告策略分析助手。你只分析用户给出的这一条广告，"
                    "不要推荐其他广告，不要返回广告 id，不要输出 Markdown。"
                ),
            },
            {
                "role": "user",
                "content": build_analysis_prompt(message, ad),
            },
        ],
        "temperature": 0.4,
    }
    response = post_json(f"{base_url}/chat/completions", api_key, payload, timeout)
    content = response["choices"][0]["message"]["content"].strip()
    if content.startswith("```"):
        try:
            result = parse_model_json(content)
            content = str(result.get("reply") or result.get("analysis") or "").strip()
        except Exception:
            content = re.sub(r"^```(?:json)?\s*", "", content)
            content = re.sub(r"\s*```$", "", content).strip()
    if not content:
        raise RuntimeError("AI 未返回有效分析内容")
    return content


def build_prompt(message: str, candidates: list[Ad]) -> str:
    candidate_lines = "\n".join(
        f"- id={ad.id}; 标题={ad.title}; 内容={ad.content}; 摘要={ad.summary}; 标签={ad.tags}"
        for ad in candidates
    )
    return f"""
用户需求：
{message}

候选广告如下：
{candidate_lines}

请从候选广告中选出最适合用户需求的 3 个广告 id，按匹配度从高到低排序。

返回严格 JSON，格式必须是：
{{
  "reply": "一句自然的中文回复，告诉用户已找到最合适的推荐",
  "adIds": [1, 2, 3]
}}

要求：
1. adIds 只能包含候选广告中存在的 id。
2. 必须返回 3 个 id；如果相关性都不强，也从候选中选相对最合适的 3 个。
3. 不要返回任何候选广告之外的 id。
""".strip()


def build_analysis_prompt(message: str, ad: Ad) -> str:
    return f"""
用户问题：
{message}

当前广告：
id={ad.id}
标题={ad.title}
内容={ad.content}
摘要={ad.summary}
标签={ad.tags}
类型={ad.type}
频道={ad.channel}
落地页={ad.target_url}

请只分析这条广告，输出自然中文，包含：
1. 适合的人群
2. 核心卖点
3. 信息流里吸引用户的原因
4. 可以改进的文案或投放建议

不要推荐其他广告，不要返回广告 id。
""".strip()


def post_json(url: str, api_key: str, payload: dict[str, Any], timeout: int) -> dict[str, Any]:
    request = Request(
        url,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
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
        raise RuntimeError(f"AI API HTTP {exc.code}: {detail}") from exc
    except URLError as exc:
        raise RuntimeError(f"AI API 连接失败: {exc.reason}") from exc


def parse_model_json(content: str) -> dict[str, Any]:
    cleaned = content.strip()
    if cleaned.startswith("```"):
        cleaned = re.sub(r"^```(?:json)?\s*", "", cleaned)
        cleaned = re.sub(r"\s*```$", "", cleaned)
    try:
        value = json.loads(cleaned)
    except json.JSONDecodeError:
        match = re.search(r"\{[\s\S]*\}", cleaned)
        if not match:
            raise
        value = json.loads(match.group(0))
    if not isinstance(value, dict):
        raise ValueError("AI 返回内容不是 JSON 对象")
    return value


def normalize_ids(value: Any, candidates: list[Ad]) -> list[int]:
    candidate_ids = {ad.id for ad in candidates}
    ids = []
    raw_values = value if isinstance(value, list) else []
    for raw in raw_values:
        try:
            ad_id = int(raw)
        except (TypeError, ValueError):
            continue
        if ad_id in candidate_ids and ad_id not in ids:
            ids.append(ad_id)
    return ids


def load_env_file() -> None:
    env_path = os.path.join(os.path.dirname(os.path.dirname(__file__)), ".env")
    if not os.path.exists(env_path):
        return
    with open(env_path, "r", encoding="utf-8") as file:
        for line in file:
            stripped = line.strip()
            if not stripped or stripped.startswith("#") or "=" not in stripped:
                continue
            key, value = stripped.split("=", 1)
            os.environ[key.strip()] = value.strip().strip('"').strip("'")
