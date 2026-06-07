from fastapi import APIRouter, Depends, Query
from fastapi.responses import JSONResponse
from sqlalchemy.orm import Session

from app import crud
from app.database import get_db
from app.schemas import LoginRequest, api_response

router = APIRouter(prefix="/api/user", tags=["user"])


@router.post("/login")
def login(payload: LoginRequest, db: Session = Depends(get_db)):
    try:
        user = crud.login_or_create_user(db, payload.username, payload.password)
    except ValueError as exc:
        return JSONResponse(status_code=400, content=api_response(400, str(exc), None))

    return api_response(
        data={
            "userId": user.id,
            "username": user.username,
            "token": user.token,
        }
    )


@router.get("/{userId}/favorites")
def get_favorites(
    userId: int,
    cursor: str | None = Query(None),
    size: int = Query(..., ge=1, le=50),
    db: Session = Depends(get_db),
):
    if crud.find_user(db, userId) is None:
        return JSONResponse(status_code=404, content=api_response(404, "用户不存在", None))

    try:
        ads, next_cursor, has_more = crud.list_favorite_ads(db, userId, cursor, size)
    except ValueError as exc:
        return JSONResponse(status_code=400, content=api_response(400, str(exc), None))

    return api_response(
        data=crud.build_ad_list_payload(
            db,
            ads,
            next_cursor,
            has_more,
            user_id=userId,
            force_favorite=True,
        )
    )


@router.post("/{userId}/favorites/{adId}")
def favorite_ad(userId: int, adId: int, db: Session = Depends(get_db)):
    if crud.find_user(db, userId) is None:
        return JSONResponse(status_code=404, content=api_response(404, "用户不存在", None))
    if crud.find_ad(db, adId) is None:
        return JSONResponse(status_code=404, content=api_response(404, "广告不存在", None))

    crud.add_favorite(db, userId, adId)
    return api_response(data=True)


@router.delete("/{userId}/favorites/{adId}")
def unfavorite_ad(userId: int, adId: int, db: Session = Depends(get_db)):
    if crud.find_user(db, userId) is None:
        return JSONResponse(status_code=404, content=api_response(404, "用户不存在", None))
    if crud.find_ad(db, adId) is None:
        return JSONResponse(status_code=404, content=api_response(404, "广告不存在", None))

    crud.remove_favorite(db, userId, adId)
    return api_response(data=True)


@router.post("/{userId}/likes/{adId}")
def like_ad(userId: int, adId: int, db: Session = Depends(get_db)):
    if crud.find_user(db, userId) is None:
        return JSONResponse(status_code=404, content=api_response(404, "用户不存在", None))
    if crud.find_ad(db, adId) is None:
        return JSONResponse(status_code=404, content=api_response(404, "广告不存在", None))

    crud.add_like(db, userId, adId)
    return api_response(data=True)


@router.delete("/{userId}/likes/{adId}")
def unlike_ad(userId: int, adId: int, db: Session = Depends(get_db)):
    if crud.find_user(db, userId) is None:
        return JSONResponse(status_code=404, content=api_response(404, "用户不存在", None))
    if crud.find_ad(db, adId) is None:
        return JSONResponse(status_code=404, content=api_response(404, "广告不存在", None))

    crud.remove_like(db, userId, adId)
    return api_response(data=True)
