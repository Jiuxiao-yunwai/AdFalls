from fastapi import APIRouter, Depends
from fastapi.responses import JSONResponse
from sqlalchemy.orm import Session

from app import crud
from app.database import get_db
from app.schemas import BehaviorRequest, api_response

router = APIRouter(prefix="/api", tags=["behavior"])


@router.post("/behavior")
def create_behavior(payload: BehaviorRequest, db: Session = Depends(get_db)):
    try:
        crud.record_behavior(
            db,
            user_id=payload.userId,
            ad_id=payload.adId,
            channel=payload.channel,
            behavior_type=payload.behaviorType,
        )
    except ValueError as exc:
        return JSONResponse(status_code=400, content=api_response(400, str(exc), None))

    return api_response(data=True)
