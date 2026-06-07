from uuid import uuid4

from sqlalchemy import func, or_
from sqlalchemy.orm import Session

from app.models import Ad, Behavior, Favorite, Like, User
from app.schemas import VALID_BEHAVIOR_TYPES

VALID_CHANNELS = {"featured", "ecommerce", "local"}


def parse_cursor(cursor: str | None) -> int | None:
    if cursor is None or cursor == "":
        return None
    try:
        value = int(cursor)
    except ValueError as exc:
        raise ValueError("cursor 必须是广告 id") from exc
    if value <= 0:
        raise ValueError("cursor 必须大于 0")
    return value


def parse_seen_ad_ids(cursor: str | None) -> list[int]:
    if cursor is None or cursor == "":
        return []
    seen_ids = []
    for raw in cursor.split(","):
        item = raw.strip()
        if not item:
            continue
        try:
            ad_id = int(item)
        except ValueError as exc:
            raise ValueError("cursor 必须是广告 id 列表") from exc
        if ad_id <= 0:
            raise ValueError("cursor 中的广告 id 必须大于 0")
        if ad_id not in seen_ids:
            seen_ids.append(ad_id)
    return seen_ids


def split_csv(value: str | None) -> list[str]:
    if not value:
        return []
    return [item.strip() for item in value.split(",") if item.strip()]


def get_favorite_ad_ids(db: Session, user_id: int | None, ad_ids: list[int]) -> set[int]:
    if not user_id or not ad_ids:
        return set()
    rows = (
        db.query(Favorite.ad_id)
        .filter(Favorite.user_id == user_id, Favorite.ad_id.in_(ad_ids))
        .all()
    )
    return {row[0] for row in rows}


def serialize_ad_summary(ad: Ad, favorite_ad_ids: set[int] | None = None) -> dict:
    favorite_ad_ids = favorite_ad_ids or set()
    return {
        "id": ad.id,
        "title": ad.title,
        "summary": ad.summary,
        "tags": split_csv(ad.tags),
        "coverUrl": ad.cover_url,
        "videoUrl": ad.video_url,
        "type": ad.type,
        "isFavorite": ad.id in favorite_ad_ids,
    }


def serialize_ad_detail(ad: Ad, is_favorite: bool = False) -> dict:
    return {
        "id": ad.id,
        "title": ad.title,
        "content": ad.content,
        "summary": ad.summary,
        "tags": split_csv(ad.tags),
        "coverUrl": ad.cover_url,
        "imageUrls": split_csv(ad.image_urls),
        "videoUrl": ad.video_url,
        "type": ad.type,
        "targetUrl": ad.target_url,
        "isFavorite": is_favorite,
    }


def paginate_ads(query, cursor: str | None, size: int) -> tuple[list[Ad], str, bool]:
    cursor_id = parse_cursor(cursor)
    if cursor_id is not None:
        query = query.filter(Ad.id < cursor_id)

    rows = query.order_by(Ad.id.desc()).limit(size + 1).all()
    items = rows[:size]
    has_more = len(rows) > size
    next_cursor = str(items[-1].id) if items else ""
    return items, next_cursor, has_more


def build_ad_list_payload(
    db: Session,
    ads: list[Ad],
    next_cursor: str,
    has_more: bool,
    user_id: int | None = None,
    force_favorite: bool = False,
) -> dict:
    favorite_ids = {ad.id for ad in ads} if force_favorite else get_favorite_ad_ids(
        db, user_id, [ad.id for ad in ads]
    )
    return {
        "list": [serialize_ad_summary(ad, favorite_ids) for ad in ads],
        "nextCursor": next_cursor,
        "hasMore": has_more,
    }


def find_ad(db: Session, ad_id: int) -> Ad | None:
    return db.query(Ad).filter(Ad.id == ad_id).first()


def is_favorite(db: Session, user_id: int | None, ad_id: int) -> bool:
    if not user_id:
        return False
    return (
        db.query(Favorite)
        .filter(Favorite.user_id == user_id, Favorite.ad_id == ad_id)
        .first()
        is not None
    )


def search_ads(
    db: Session,
    keyword: str,
    cursor: str | None,
    size: int,
) -> tuple[list[Ad], str, bool]:
    pattern = f"%{keyword}%"
    query = db.query(Ad).filter(
        or_(
            Ad.title.like(pattern),
            Ad.content.like(pattern),
            Ad.summary.like(pattern),
            Ad.tags.like(pattern),
        )
    )
    return paginate_ads(query, cursor, size)


def random_ads(db: Session, size: int = 50) -> list[Ad]:
    return db.query(Ad).order_by(func.random()).limit(size).all()


def random_feed_ads(
    db: Session,
    channel: str,
    cursor: str | None,
    size: int,
) -> tuple[list[Ad], str, bool]:
    seen_ids = parse_seen_ad_ids(cursor)
    query = db.query(Ad).filter(Ad.channel == channel)
    if seen_ids:
        query = query.filter(Ad.id.notin_(seen_ids))

    remaining_count = query.count()
    ads = query.order_by(func.random()).limit(size).all()
    next_seen_ids = seen_ids + [ad.id for ad in ads if ad.id not in seen_ids]
    next_cursor = ",".join(str(ad_id) for ad_id in next_seen_ids)
    has_more = remaining_count > len(ads)
    return ads, next_cursor, has_more


def ads_by_ids_preserving_order(db: Session, ad_ids: list[int]) -> list[Ad]:
    if not ad_ids:
        return []
    rows = db.query(Ad).filter(Ad.id.in_(ad_ids)).all()
    by_id = {ad.id: ad for ad in rows}
    return [by_id[ad_id] for ad_id in ad_ids if ad_id in by_id]


def extract_keyword(message: str) -> str:
    for keyword in ("耳机", "手机", "咖啡"):
        if keyword in message:
            return keyword
    return message.strip()


def record_behavior(
    db: Session,
    user_id: int,
    behavior_type: str,
    ad_id: int | None = None,
    channel: str | None = None,
) -> Behavior:
    if behavior_type not in VALID_BEHAVIOR_TYPES:
        raise ValueError("behaviorType 不合法")

    behavior = Behavior(
        user_id=user_id,
        ad_id=ad_id,
        channel=channel,
        behavior_type=behavior_type,
    )
    db.add(behavior)
    db.commit()
    db.refresh(behavior)
    return behavior


def login_or_create_user(db: Session, username: str, password: str) -> User:
    user = db.query(User).filter(User.username == username).first()
    if user is not None:
        if user.password != password:
            raise ValueError("密码错误")
        user.token = str(uuid4())
        db.commit()
        db.refresh(user)
        return user

    user = User(username=username, password=password, token=str(uuid4()))
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


def find_user(db: Session, user_id: int) -> User | None:
    return db.query(User).filter(User.id == user_id).first()


def list_favorite_ads(
    db: Session,
    user_id: int,
    cursor: str | None,
    size: int,
) -> tuple[list[Ad], str, bool]:
    cursor_id = parse_cursor(cursor)
    query = (
        db.query(Ad)
        .join(Favorite, Favorite.ad_id == Ad.id)
        .filter(Favorite.user_id == user_id)
    )
    if cursor_id is not None:
        query = query.filter(Ad.id < cursor_id)

    rows = query.order_by(Ad.id.desc()).limit(size + 1).all()
    items = rows[:size]
    has_more = len(rows) > size
    next_cursor = str(items[-1].id) if items else ""
    return items, next_cursor, has_more


def add_favorite(db: Session, user_id: int, ad_id: int) -> None:
    favorite = (
        db.query(Favorite)
        .filter(Favorite.user_id == user_id, Favorite.ad_id == ad_id)
        .first()
    )
    if favorite is None:
        db.add(Favorite(user_id=user_id, ad_id=ad_id))
        db.commit()
        record_behavior(db, user_id=user_id, ad_id=ad_id, behavior_type="FAVORITE")


def remove_favorite(db: Session, user_id: int, ad_id: int) -> None:
    favorite = (
        db.query(Favorite)
        .filter(Favorite.user_id == user_id, Favorite.ad_id == ad_id)
        .first()
    )
    if favorite is not None:
        db.delete(favorite)
        db.commit()
        record_behavior(db, user_id=user_id, ad_id=ad_id, behavior_type="UNFAVORITE")


def add_like(db: Session, user_id: int, ad_id: int) -> None:
    like = (
        db.query(Like)
        .filter(Like.user_id == user_id, Like.ad_id == ad_id)
        .first()
    )
    if like is None:
        db.add(Like(user_id=user_id, ad_id=ad_id))
        db.commit()
        record_behavior(db, user_id=user_id, ad_id=ad_id, behavior_type="LIKE")


def remove_like(db: Session, user_id: int, ad_id: int) -> None:
    like = (
        db.query(Like)
        .filter(Like.user_id == user_id, Like.ad_id == ad_id)
        .first()
    )
    if like is not None:
        db.delete(like)
        db.commit()
        record_behavior(db, user_id=user_id, ad_id=ad_id, behavior_type="UNLIKE")
