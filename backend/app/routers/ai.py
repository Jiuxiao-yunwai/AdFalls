import logging

from fastapi import APIRouter, Depends
from fastapi.responses import JSONResponse
from sqlalchemy.orm import Session

from app import crud
from app.ai_recommender import analyze_ad_with_ai, recommend_ad_ids_with_ai
from app.database import get_db
from app.schemas import AdAnalysisRequest, ChatSearchRequest, api_response

router = APIRouter(prefix="/api/ai", tags=["ai"])
logger = logging.getLogger(__name__)


@router.post("/chat-search")
def chat_search(payload: ChatSearchRequest, db: Session = Depends(get_db)):
    if payload.userId is not None:
        crud.record_behavior(db, user_id=payload.userId, behavior_type="SEARCH")

    candidates = crud.random_ads(db, size=50)
    try:
        reply, ad_ids = recommend_ad_ids_with_ai(payload.message, candidates)
        ads = crud.ads_by_ids_preserving_order(db, ad_ids)[:3]
    except Exception as exc:
        logger.warning("AI chat search failed, falling back to keyword search: %s", exc)
        keyword = crud.extract_keyword(payload.message)
        try:
            ads, _, _ = crud.search_ads(db, keyword=keyword, cursor=None, size=3)
        except ValueError as exc:
            return JSONResponse(status_code=400, content=api_response(400, str(exc), None))
        reply = "AI 暂时不可用，已先按关键词为你找到相关广告。"

    if len(ads) < 3:
        existing_ids = {ad.id for ad in ads}
        ads.extend([ad for ad in candidates if ad.id not in existing_ids][: 3 - len(ads)])

    favorite_ids = crud.get_favorite_ad_ids(db, payload.userId, [ad.id for ad in ads])
    return api_response(
        data={
            "reply": reply,
            "ads": [crud.serialize_ad_summary(ad, favorite_ids) for ad in ads],
            "nextCursor": "",
            "hasMore": False,
        }
    )


@router.post("/ad-analysis")
def ad_analysis(payload: AdAnalysisRequest, db: Session = Depends(get_db)):
    ad = crud.find_ad(db, payload.adId)
    if ad is None:
        return JSONResponse(status_code=404, content=api_response(404, "广告不存在", None))

    if payload.userId is not None:
        crud.record_behavior(
            db,
            user_id=payload.userId,
            ad_id=ad.id,
            channel=ad.channel,
            behavior_type="DETAIL_VIEW",
        )

    try:
        reply = analyze_ad_with_ai(payload.message, ad)
    except Exception as exc:
        logger.warning("AI ad analysis failed, using local fallback: %s", exc)
        reply = build_local_ad_analysis(ad)

    return api_response(data={"reply": reply})


def build_local_ad_analysis(ad) -> str:
    tags = "、".join(crud.split_csv(ad.tags)) or "该品类"
    return (
        f"这条广告更适合关注{tags}的用户。\n\n"
        f"核心卖点是：{ad.summary}\n\n"
        f"从信息流表现看，标题“{ad.title}”比较适合作为第一眼吸引点，"
        "内容需要快速说明使用场景、优惠或体验价值，让用户在滑动时能马上判断是否相关。\n\n"
        "优化建议：可以把目标人群、具体场景和一个明确行动点放在前半句，减少泛泛描述。"
    )
