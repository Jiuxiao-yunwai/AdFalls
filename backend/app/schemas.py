from typing import Any

from pydantic import BaseModel, Field

VALID_BEHAVIOR_TYPES = {
    "EXPOSURE",
    "CLICK",
    "DETAIL_VIEW",
    "LIKE",
    "UNLIKE",
    "FAVORITE",
    "UNFAVORITE",
    "VIDEO_PLAY",
    "SEARCH",
}


def api_response(code: int = 200, message: str = "success", data: Any = None) -> dict:
    return {
        "code": code,
        "message": message,
        "data": {} if data is None and code == 200 else data,
    }


class LoginRequest(BaseModel):
    username: str = Field(..., min_length=1)
    password: str = Field(..., min_length=1)


class BehaviorRequest(BaseModel):
    userId: int
    adId: int | None = None
    channel: str | None = None
    behaviorType: str


class ChatSearchRequest(BaseModel):
    userId: int | None = None
    message: str = Field(..., min_length=1)
    cursor: str | None = None
    size: int = Field(..., ge=1, le=50)


class AdAnalysisRequest(BaseModel):
    userId: int | None = None
    adId: int
    message: str = Field(..., min_length=1)
