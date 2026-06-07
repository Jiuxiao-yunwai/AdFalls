import os
from collections.abc import Generator
from pathlib import Path

from sqlalchemy import create_engine
from sqlalchemy.orm import Session, declarative_base, sessionmaker

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_DATABASE_PATH = REPO_ROOT / "adfalls.db"
DATABASE_URL = os.getenv("ADFALLS_DATABASE_URL", f"sqlite:///{DEFAULT_DATABASE_PATH}")

engine = create_engine(
    DATABASE_URL,
    connect_args={"check_same_thread": False},
)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()


def get_db() -> Generator[Session, None, None]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def init_database() -> None:
    from app import models
    from app.seed import seed_ads

    Base.metadata.create_all(bind=engine)
    db = SessionLocal()
    try:
        seed_ads(db)
    finally:
        db.close()
