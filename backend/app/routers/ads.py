from fastapi import APIRouter, Depends, Query
from fastapi.responses import JSONResponse
from sqlalchemy.orm import Session

from app import crud
from app.database import get_db
from app.schemas import api_response

router = APIRouter(prefix="/api/ads", tags=["ads"])


@router.get("/feed")
def get_ads_feed(
    channel: str = Query(..., min_length=1),
    cursor: str | None = Query(None),
    size: int = Query(..., ge=1, le=50),
    userId: int | None = Query(None),
    db: Session = Depends(get_db),
):
    if channel not in crud.VALID_CHANNELS:
        return JSONResponse(
            status_code=400,
            content=api_response(400, "channel 不合法", None),
        )

    try:
        ads, next_cursor, has_more = crud.random_feed_ads(db, channel, cursor, size)
    except ValueError as exc:
        return JSONResponse(status_code=400, content=api_response(400, str(exc), None))

    return api_response(
        data=crud.build_ad_list_payload(db, ads, next_cursor, has_more, userId)
    )


@router.get("/search")
def search_ads(
    keyword: str = Query(..., min_length=1),
    cursor: str | None = Query(None),
    size: int = Query(..., ge=1, le=50),
    userId: int | None = Query(None),
    db: Session = Depends(get_db),
):
    try:
        ads, next_cursor, has_more = crud.search_ads(db, keyword, cursor, size)
    except ValueError as exc:
        return JSONResponse(status_code=400, content=api_response(400, str(exc), None))

    if userId is not None:
        try:
            crud.record_behavior(db, user_id=userId, behavior_type="SEARCH")
        except ValueError:
            pass

    return api_response(
        data=crud.build_ad_list_payload(db, ads, next_cursor, has_more, userId)
    )


@router.get("/{id}")
def get_ad_detail(
    id: int,
    userId: int | None = Query(None),
    db: Session = Depends(get_db),
):
    ad = crud.find_ad(db, id)
    if ad is None:
        return JSONResponse(
            status_code=404,
            content=api_response(404, "广告不存在", None),
        )

    return api_response(
        data=crud.serialize_ad_detail(ad, crud.is_favorite(db, userId, ad.id))
    )
