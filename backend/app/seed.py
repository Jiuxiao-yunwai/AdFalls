from datetime import datetime, timedelta
import json
from pathlib import Path

from sqlalchemy.orm import Session

from app.models import Ad


def seed_ads(db: Session) -> None:
    if db.query(Ad).first() is not None:
        return

    generated_ads = load_generated_ads()
    if generated_ads:
        for index, ad_data in enumerate(generated_ads):
            db.add(Ad(**ad_data, created_at=datetime.utcnow() - timedelta(minutes=index)))
        db.commit()
        return

    now = datetime.utcnow()
    ads = [
        {
            "title": "智能降噪蓝牙耳机",
            "content": "轻量佩戴，主动降噪，适合学生学习、通勤和自习室使用。",
            "summary": "适合学习和通勤使用的降噪耳机。",
            "tags": "耳机,降噪,学生",
            "cover_url": "https://example.com/images/headphones-cover.jpg",
            "image_urls": "https://example.com/images/headphones-1.jpg,https://example.com/images/headphones-2.jpg",
            "video_url": None,
            "type": "IMAGE_LARGE",
            "channel": "featured",
            "target_url": "https://example.com/ads/headphones",
        },
        {
            "title": "轻薄长续航学生手机",
            "content": "大电池、高清屏幕和流畅系统，满足学习、拍照和娱乐需求。",
            "summary": "一款适合学生日常使用的长续航手机。",
            "tags": "手机,学生,长续航",
            "cover_url": "https://example.com/images/phone-cover.jpg",
            "image_urls": "https://example.com/images/phone-1.jpg,https://example.com/images/phone-2.jpg",
            "video_url": None,
            "type": "IMAGE_SMALL",
            "channel": "featured",
            "target_url": "https://example.com/ads/phone",
        },
        {
            "title": "精品咖啡周末体验课",
            "content": "学习手冲咖啡基础，了解豆种、研磨、萃取和拉花技巧。",
            "summary": "适合咖啡爱好者的周末体验课程。",
            "tags": "咖啡,课程,周末",
            "cover_url": "https://example.com/images/coffee-class-cover.jpg",
            "image_urls": "https://example.com/images/coffee-class-1.jpg,https://example.com/images/coffee-class-2.jpg",
            "video_url": "https://example.com/videos/coffee-class.mp4",
            "type": "VIDEO",
            "channel": "featured",
            "target_url": "https://example.com/ads/coffee-class",
        },
        {
            "title": "城市短途旅行套餐",
            "content": "精选周边目的地，包含交通、住宿和当地特色体验。",
            "summary": "适合周末出发的轻松旅行套餐。",
            "tags": "旅行,周末,城市",
            "cover_url": "https://example.com/images/travel-cover.jpg",
            "image_urls": "https://example.com/images/travel-1.jpg,https://example.com/images/travel-2.jpg",
            "video_url": None,
            "type": "MIXED",
            "channel": "featured",
            "target_url": "https://example.com/ads/travel",
        },
        {
            "title": "高效学习在线课程",
            "content": "覆盖时间管理、笔记方法和考试复习技巧，帮助提升学习效率。",
            "summary": "为学生设计的学习效率提升课程。",
            "tags": "课程,学习,学生",
            "cover_url": "https://example.com/images/study-course-cover.jpg",
            "image_urls": "https://example.com/images/study-course-1.jpg,https://example.com/images/study-course-2.jpg",
            "video_url": None,
            "type": "IMAGE_LARGE",
            "channel": "featured",
            "target_url": "https://example.com/ads/study-course",
        },
        {
            "title": "电商爆款无线耳机",
            "content": "低延迟、快充、稳定连接，适合运动、游戏和在线会议。",
            "summary": "高性价比无线耳机限时优惠。",
            "tags": "耳机,电商,优惠",
            "cover_url": "https://example.com/images/ecommerce-earbuds-cover.jpg",
            "image_urls": "https://example.com/images/ecommerce-earbuds-1.jpg,https://example.com/images/ecommerce-earbuds-2.jpg",
            "video_url": None,
            "type": "IMAGE_LARGE",
            "channel": "ecommerce",
            "target_url": "https://example.com/ads/ecommerce-earbuds",
        },
        {
            "title": "旗舰拍照手机新品",
            "content": "高像素主摄、夜景算法和大容量存储，适合记录旅行和生活。",
            "summary": "主打影像体验的旗舰手机新品。",
            "tags": "手机,拍照,新品",
            "cover_url": "https://example.com/images/camera-phone-cover.jpg",
            "image_urls": "https://example.com/images/camera-phone-1.jpg,https://example.com/images/camera-phone-2.jpg",
            "video_url": "https://example.com/videos/camera-phone.mp4",
            "type": "VIDEO",
            "channel": "ecommerce",
            "target_url": "https://example.com/ads/camera-phone",
        },
        {
            "title": "办公室咖啡机套装",
            "content": "自动研磨、稳定萃取，搭配精选咖啡豆，提升办公区体验。",
            "summary": "适合办公室采购的咖啡机套装。",
            "tags": "咖啡,电商,办公室",
            "cover_url": "https://example.com/images/coffee-machine-cover.jpg",
            "image_urls": "https://example.com/images/coffee-machine-1.jpg,https://example.com/images/coffee-machine-2.jpg",
            "video_url": None,
            "type": "IMAGE_SMALL",
            "channel": "ecommerce",
            "target_url": "https://example.com/ads/coffee-machine",
        },
        {
            "title": "热门网课课程礼包",
            "content": "包含编程、英语和设计课程，适合学生和职场新人。",
            "summary": "多门在线课程组合优惠。",
            "tags": "课程,电商,学习",
            "cover_url": "https://example.com/images/course-bundle-cover.jpg",
            "image_urls": "https://example.com/images/course-bundle-1.jpg,https://example.com/images/course-bundle-2.jpg",
            "video_url": None,
            "type": "MIXED",
            "channel": "ecommerce",
            "target_url": "https://example.com/ads/course-bundle",
        },
        {
            "title": "旅行收纳箱限时折扣",
            "content": "轻便耐用，分区清晰，适合短途旅行和出差携带。",
            "summary": "旅行和出差都适用的收纳箱。",
            "tags": "旅行,电商,收纳",
            "cover_url": "https://example.com/images/travel-case-cover.jpg",
            "image_urls": "https://example.com/images/travel-case-1.jpg,https://example.com/images/travel-case-2.jpg",
            "video_url": None,
            "type": "IMAGE_LARGE",
            "channel": "ecommerce",
            "target_url": "https://example.com/ads/travel-case",
        },
        {
            "title": "本地咖啡馆早餐套餐",
            "content": "精品咖啡搭配可颂和三明治，工作日前两小时享受优惠。",
            "summary": "本地咖啡馆推出早餐优惠套餐。",
            "tags": "咖啡,本地,早餐",
            "cover_url": "https://example.com/images/local-cafe-cover.jpg",
            "image_urls": "https://example.com/images/local-cafe-1.jpg,https://example.com/images/local-cafe-2.jpg",
            "video_url": None,
            "type": "IMAGE_LARGE",
            "channel": "local",
            "target_url": "https://example.com/ads/local-cafe",
        },
        {
            "title": "附近外卖满减活动",
            "content": "覆盖快餐、轻食和夜宵，多家商户参与满减和免配送费活动。",
            "summary": "本地外卖商户联合满减优惠。",
            "tags": "外卖,本地,优惠",
            "cover_url": "https://example.com/images/takeout-cover.jpg",
            "image_urls": "https://example.com/images/takeout-1.jpg,https://example.com/images/takeout-2.jpg",
            "video_url": None,
            "type": "IMAGE_SMALL",
            "channel": "local",
            "target_url": "https://example.com/ads/takeout",
        },
        {
            "title": "周边亲子旅行一日游",
            "content": "包含自然公园、手作课堂和本地特色餐饮，适合家庭周末出行。",
            "summary": "适合家庭参加的本地一日游。",
            "tags": "旅行,本地,亲子",
            "cover_url": "https://example.com/images/local-trip-cover.jpg",
            "image_urls": "https://example.com/images/local-trip-1.jpg,https://example.com/images/local-trip-2.jpg",
            "video_url": "https://example.com/videos/local-trip.mp4",
            "type": "VIDEO",
            "channel": "local",
            "target_url": "https://example.com/ads/local-trip",
        },
        {
            "title": "社区编程入门课程",
            "content": "面向零基础学生，讲解 Python 基础、项目练习和学习路径。",
            "summary": "本地社区提供的编程入门课程。",
            "tags": "课程,本地,Python",
            "cover_url": "https://example.com/images/local-python-cover.jpg",
            "image_urls": "https://example.com/images/local-python-1.jpg,https://example.com/images/local-python-2.jpg",
            "video_url": None,
            "type": "MIXED",
            "channel": "local",
            "target_url": "https://example.com/ads/local-python",
        },
        {
            "title": "手机维修到店优惠",
            "content": "屏幕、电池和系统检测服务，预约到店可享受配件折扣。",
            "summary": "本地手机维修门店预约优惠。",
            "tags": "手机,本地,维修",
            "cover_url": "https://example.com/images/local-phone-repair-cover.jpg",
            "image_urls": "https://example.com/images/local-phone-repair-1.jpg,https://example.com/images/local-phone-repair-2.jpg",
            "video_url": None,
            "type": "IMAGE_LARGE",
            "channel": "local",
            "target_url": "https://example.com/ads/local-phone-repair",
        },
    ]

    for index, ad_data in enumerate(ads):
        db.add(Ad(**ad_data, created_at=now - timedelta(minutes=index)))
    db.commit()


def load_generated_ads() -> list[dict]:
    ads_json = Path(__file__).resolve().parents[2] / "data" / "ads.json"
    if not ads_json.exists():
        return []

    raw_ads = json.loads(ads_json.read_text(encoding="utf-8"))
    ads = []
    for raw in raw_ads:
        ads.append(
            {
                "title": raw.get("title", ""),
                "content": raw.get("content", ""),
                "summary": raw.get("summary", ""),
                "tags": raw.get("tags", ""),
                "cover_url": raw.get("cover_url", ""),
                "image_urls": raw.get("image_urls", raw.get("cover_url", "")),
                "video_url": raw.get("video_url") or None,
                "type": raw.get("type", "IMAGE_LARGE"),
                "channel": raw.get("channel", "featured"),
                "target_url": raw.get("target_url", ""),
            }
        )
    return ads
