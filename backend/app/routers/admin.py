from fastapi import APIRouter, Depends, Query
from fastapi.responses import HTMLResponse, JSONResponse
from sqlalchemy import case, func
from sqlalchemy.orm import Session

from app import crud
from app.database import get_db
from app.models import Ad, Behavior, Favorite, Like
from app.schemas import api_response

router = APIRouter(tags=["admin"])


@router.get("/admin/exposure", response_class=HTMLResponse)
def exposure_dashboard() -> HTMLResponse:
    return HTMLResponse(EXPOSURE_DASHBOARD_HTML)


@router.get("/api/admin/ads/metrics")
def ad_metrics(
    channel: str | None = Query(None),
    keyword: str | None = Query(None),
    cursor: str | None = Query(None),
    size: int = Query(50, ge=1, le=200),
    db: Session = Depends(get_db),
):
    if channel is not None and channel not in crud.VALID_CHANNELS:
        return JSONResponse(status_code=400, content=api_response(400, "channel 不合法", None))

    try:
        cursor_id = crud.parse_cursor(cursor)
    except ValueError as exc:
        return JSONResponse(status_code=400, content=api_response(400, str(exc), None))

    behavior_stats = (
        db.query(
            Behavior.ad_id.label("ad_id"),
            func.sum(case((Behavior.behavior_type == "EXPOSURE", 1), else_=0)).label("exposures"),
            func.sum(case((Behavior.behavior_type == "CLICK", 1), else_=0)).label("clicks"),
            func.sum(case((Behavior.behavior_type == "DETAIL_VIEW", 1), else_=0)).label("detail_views"),
            func.sum(case((Behavior.behavior_type == "LIKE", 1), else_=0)).label("like_events"),
            func.sum(case((Behavior.behavior_type == "UNLIKE", 1), else_=0)).label("unlike_events"),
            func.sum(case((Behavior.behavior_type == "FAVORITE", 1), else_=0)).label("favorite_events"),
            func.sum(case((Behavior.behavior_type == "UNFAVORITE", 1), else_=0)).label("unfavorite_events"),
            func.sum(case((Behavior.behavior_type == "VIDEO_PLAY", 1), else_=0)).label("video_plays"),
            func.max(Behavior.created_at).label("last_behavior_at"),
        )
        .filter(Behavior.ad_id.isnot(None))
        .group_by(Behavior.ad_id)
        .subquery()
    )
    favorite_stats = (
        db.query(
            Favorite.ad_id.label("ad_id"),
            func.count(Favorite.id).label("current_favorites"),
        )
        .group_by(Favorite.ad_id)
        .subquery()
    )
    like_stats = (
        db.query(
            Like.ad_id.label("ad_id"),
            func.count(Like.id).label("current_likes"),
        )
        .group_by(Like.ad_id)
        .subquery()
    )

    query = (
        db.query(
            Ad,
            func.coalesce(behavior_stats.c.exposures, 0),
            func.coalesce(behavior_stats.c.clicks, 0),
            func.coalesce(behavior_stats.c.detail_views, 0),
            func.coalesce(behavior_stats.c.like_events, 0),
            func.coalesce(behavior_stats.c.unlike_events, 0),
            func.coalesce(behavior_stats.c.favorite_events, 0),
            func.coalesce(behavior_stats.c.unfavorite_events, 0),
            func.coalesce(behavior_stats.c.video_plays, 0),
            func.coalesce(like_stats.c.current_likes, 0),
            func.coalesce(favorite_stats.c.current_favorites, 0),
            behavior_stats.c.last_behavior_at,
        )
        .outerjoin(behavior_stats, behavior_stats.c.ad_id == Ad.id)
        .outerjoin(like_stats, like_stats.c.ad_id == Ad.id)
        .outerjoin(favorite_stats, favorite_stats.c.ad_id == Ad.id)
    )

    if channel is not None:
        query = query.filter(Ad.channel == channel)
    if cursor_id is not None:
        query = query.filter(Ad.id < cursor_id)
    if keyword:
        pattern = f"%{keyword.strip()}%"
        query = query.filter(
            Ad.title.like(pattern)
            | Ad.content.like(pattern)
            | Ad.summary.like(pattern)
            | Ad.tags.like(pattern)
        )

    rows = query.order_by(Ad.id.desc()).limit(size + 1).all()
    page_rows = rows[:size]
    items = [serialize_metric_row(row) for row in page_rows]
    return api_response(
        data={
            "list": items,
            "nextCursor": str(page_rows[-1][0].id) if page_rows else "",
            "hasMore": len(rows) > size,
        }
    )


def serialize_metric_row(row) -> dict:
    (
        ad,
        exposures,
        clicks,
        detail_views,
        like_events,
        unlike_events,
        favorite_events,
        unfavorite_events,
        video_plays,
        current_likes,
        current_favorites,
        last_behavior_at,
    ) = row
    exposure_count = int(exposures or 0)
    click_count = int(clicks or 0)
    ctr = round(click_count / exposure_count, 4) if exposure_count > 0 else 0
    return {
        "id": ad.id,
        "title": ad.title,
        "channel": ad.channel,
        "type": ad.type,
        "summary": ad.summary,
        "tags": crud.split_csv(ad.tags),
        "coverUrl": ad.cover_url,
        "exposures": exposure_count,
        "clicks": click_count,
        "detailViews": int(detail_views or 0),
        "likeEvents": int(like_events or 0),
        "unlikeEvents": int(unlike_events or 0),
        "favoriteEvents": int(favorite_events or 0),
        "unfavoriteEvents": int(unfavorite_events or 0),
        "videoPlays": int(video_plays or 0),
        "currentLikes": int(current_likes or 0),
        "currentFavorites": int(current_favorites or 0),
        "ctr": ctr,
        "lastBehaviorAt": last_behavior_at.isoformat() if last_behavior_at else None,
    }


EXPOSURE_DASHBOARD_HTML = """
<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>AdFalls 广告曝光管理</title>
  <style>
    :root {
      color-scheme: light;
      --bg: #f4f6f8;
      --panel: #ffffff;
      --text: #17202a;
      --muted: #657080;
      --line: #dbe1e8;
      --accent: #1f7a68;
      --accent-soft: #e5f4f0;
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Microsoft YaHei", sans-serif;
      background: var(--bg);
      color: var(--text);
    }
    header {
      padding: 22px 28px 14px;
      background: var(--panel);
      border-bottom: 1px solid var(--line);
    }
    h1 { margin: 0; font-size: 22px; }
    .subtitle { margin-top: 6px; color: var(--muted); font-size: 13px; }
    main { padding: 18px 28px 28px; }
    .toolbar {
      display: flex;
      gap: 10px;
      flex-wrap: wrap;
      align-items: center;
      margin-bottom: 14px;
    }
    select, input, button {
      height: 36px;
      border: 1px solid var(--line);
      border-radius: 6px;
      background: #fff;
      color: var(--text);
      padding: 0 10px;
      font-size: 14px;
    }
    button {
      cursor: pointer;
      background: var(--accent);
      border-color: var(--accent);
      color: white;
      font-weight: 600;
    }
    .table-wrap {
      width: 100%;
      max-width: calc(100vw - 56px);
      overflow-x: auto;
      overflow-y: visible;
      -webkit-overflow-scrolling: touch;
      overscroll-behavior-x: contain;
      touch-action: pan-x pan-y;
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: 8px;
      scrollbar-gutter: stable;
    }
    .table-wrap::-webkit-scrollbar {
      height: 10px;
    }
    .table-wrap::-webkit-scrollbar-thumb {
      background: #b8c3cf;
      border-radius: 999px;
    }
    .table-wrap::-webkit-scrollbar-track {
      background: #eef2f5;
      border-radius: 999px;
    }
    table {
      width: max-content;
      border-collapse: collapse;
      min-width: 1760px;
    }
    th, td {
      padding: 11px 12px;
      border-bottom: 1px solid var(--line);
      text-align: left;
      vertical-align: top;
      font-size: 13px;
      white-space: nowrap;
    }
    th {
      position: sticky;
      top: 0;
      background: #fbfcfd;
      color: var(--muted);
      font-weight: 700;
      z-index: 1;
    }
    th.sortable {
      cursor: pointer;
      user-select: none;
    }
    th.sortable:hover {
      color: var(--accent);
      background: #f2f8f6;
    }
    th.sortable::after {
      content: "↕";
      margin-left: 6px;
      color: #9aa6b2;
      font-size: 11px;
    }
    th.sortable.sort-asc::after {
      content: "↑";
      color: var(--accent);
    }
    th.sortable.sort-desc::after {
      content: "↓";
      color: var(--accent);
    }
    td.title {
      white-space: normal;
      min-width: 240px;
      max-width: 360px;
      font-weight: 650;
    }
    .tag {
      display: inline-block;
      margin: 2px 4px 0 0;
      padding: 2px 6px;
      border-radius: 999px;
      background: var(--accent-soft);
      color: var(--accent);
      font-size: 12px;
    }
    .muted { color: var(--muted); }
    .summary {
      white-space: normal;
      min-width: 260px;
      max-width: 420px;
      color: var(--muted);
    }
    .status {
      margin: 10px 0 0;
      color: var(--muted);
      font-size: 13px;
    }
    .load-more {
      margin-top: 12px;
      display: none;
    }
  </style>
</head>
<body>
  <header>
    <h1>广告曝光管理</h1>
    <div class="subtitle">按广告聚合曝光、点击、详情查看、点赞、收藏、取消收藏和视频播放数据。</div>
  </header>
  <main>
    <div class="toolbar">
      <select id="channel">
        <option value="">全部频道</option>
        <option value="featured">featured</option>
        <option value="ecommerce">ecommerce</option>
        <option value="local">local</option>
      </select>
      <input id="keyword" type="search" placeholder="搜索标题 / 摘要 / 标签" />
      <button id="reload">刷新</button>
    </div>
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th class="sortable sort-desc" data-sort="id">ID</th>
            <th class="sortable" data-sort="title">广告</th>
            <th class="sortable" data-sort="channel">频道</th>
            <th class="sortable" data-sort="type">类型</th>
            <th class="sortable" data-sort="exposures">曝光</th>
            <th class="sortable" data-sort="clicks">点击</th>
            <th class="sortable" data-sort="ctr">CTR</th>
            <th class="sortable" data-sort="detailViews">详情</th>
            <th class="sortable" data-sort="likeEvents">点赞事件</th>
            <th class="sortable" data-sort="unlikeEvents">取消点赞</th>
            <th class="sortable" data-sort="currentLikes">当前点赞</th>
            <th class="sortable" data-sort="favoriteEvents">收藏事件</th>
            <th class="sortable" data-sort="unfavoriteEvents">取消收藏</th>
            <th class="sortable" data-sort="currentFavorites">当前收藏</th>
            <th class="sortable" data-sort="videoPlays">视频播放</th>
            <th class="sortable" data-sort="lastBehaviorAt">最后行为</th>
            <th class="sortable" data-sort="summary">摘要</th>
          </tr>
        </thead>
        <tbody id="rows"></tbody>
      </table>
    </div>
    <button id="loadMore" class="load-more">加载更多</button>
    <div id="status" class="status"></div>
  </main>
  <script>
    const rowsEl = document.getElementById("rows");
    const statusEl = document.getElementById("status");
    const loadMoreButton = document.getElementById("loadMore");
    const sortableHeaders = Array.from(document.querySelectorAll("th.sortable"));
    const numericSortKeys = new Set([
      "id", "exposures", "clicks", "ctr", "detailViews",
      "likeEvents", "unlikeEvents", "currentLikes",
      "favoriteEvents", "unfavoriteEvents", "currentFavorites", "videoPlays"
    ]);
    let cursor = "";
    let hasMore = false;
    let allItems = [];
    let sortKey = "id";
    let sortDirection = "desc";

    function esc(value) {
      return String(value ?? "").replace(/[&<>"']/g, ch => ({
        "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
      }[ch]));
    }

    function qs(reset) {
      const params = new URLSearchParams();
      params.set("size", "50");
      const channel = document.getElementById("channel").value;
      const keyword = document.getElementById("keyword").value.trim();
      if (channel) params.set("channel", channel);
      if (keyword) params.set("keyword", keyword);
      if (!reset && cursor) params.set("cursor", cursor);
      return params.toString();
    }

    function sortItems(items) {
      return [...items].sort((a, b) => {
        const left = a[sortKey];
        const right = b[sortKey];
        let result;
        if (numericSortKeys.has(sortKey)) {
          result = Number(left || 0) - Number(right || 0);
        } else if (sortKey === "lastBehaviorAt") {
          result = Date.parse(left || "1970-01-01T00:00:00") - Date.parse(right || "1970-01-01T00:00:00");
        } else {
          result = String(left || "").localeCompare(String(right || ""), "zh-CN");
        }
        return sortDirection === "asc" ? result : -result;
      });
    }

    function updateSortIndicators() {
      sortableHeaders.forEach(header => {
        header.classList.remove("sort-asc", "sort-desc");
        if (header.dataset.sort === sortKey) {
          header.classList.add(sortDirection === "asc" ? "sort-asc" : "sort-desc");
        }
      });
    }

    function render() {
      updateSortIndicators();
      const items = sortItems(allItems);
      rowsEl.innerHTML = items.map(item => `
        <tr>
          <td>${item.id}</td>
          <td class="title">
            ${esc(item.title)}
            <div>${item.tags.map(tag => `<span class="tag">#${esc(tag)}</span>`).join("")}</div>
          </td>
          <td>${esc(item.channel)}</td>
          <td>${esc(item.type)}</td>
          <td>${item.exposures}</td>
          <td>${item.clicks}</td>
          <td>${(item.ctr * 100).toFixed(2)}%</td>
          <td>${item.detailViews}</td>
          <td>${item.likeEvents}</td>
          <td>${item.unlikeEvents}</td>
          <td>${item.currentLikes}</td>
          <td>${item.favoriteEvents}</td>
          <td>${item.unfavoriteEvents}</td>
          <td>${item.currentFavorites}</td>
          <td>${item.videoPlays}</td>
          <td class="muted">${esc(item.lastBehaviorAt || "-")}</td>
          <td class="summary">${esc(item.summary)}</td>
        </tr>
      `).join("");
    }

    async function load(reset = true) {
      if (reset) {
        cursor = "";
        allItems = [];
      }
      statusEl.textContent = "加载中...";
      const response = await fetch(`/api/admin/ads/metrics?${qs(reset)}`);
      const body = await response.json();
      if (body.code !== 200) {
        statusEl.textContent = body.message || "加载失败";
        return;
      }
      allItems = allItems.concat(body.data.list || []);
      render();
      cursor = body.data.nextCursor || "";
      hasMore = body.data.hasMore;
      loadMoreButton.style.display = hasMore ? "inline-block" : "none";
      statusEl.textContent = `已加载 ${allItems.length} 条${hasMore ? "，还有更多" : "，没有更多了"}。当前按 ${sortKey} ${sortDirection === "asc" ? "升序" : "降序"} 排列。`;
    }

    sortableHeaders.forEach(header => {
      header.addEventListener("click", () => {
        const nextKey = header.dataset.sort;
        if (sortKey === nextKey) {
          sortDirection = sortDirection === "asc" ? "desc" : "asc";
        } else {
          sortKey = nextKey;
          sortDirection = numericSortKeys.has(nextKey) || nextKey === "lastBehaviorAt" ? "desc" : "asc";
        }
        render();
        statusEl.textContent = `已加载 ${allItems.length} 条，当前按 ${sortKey} ${sortDirection === "asc" ? "升序" : "降序"} 排列。`;
      });
    });
    document.getElementById("reload").addEventListener("click", () => load(true));
    document.getElementById("channel").addEventListener("change", () => load(true));
    document.getElementById("keyword").addEventListener("keydown", event => {
      if (event.key === "Enter") load(true);
    });
    loadMoreButton.addEventListener("click", () => load(false));
    load(true);
  </script>
</body>
</html>
"""
