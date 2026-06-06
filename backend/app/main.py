from pathlib import Path

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.database import init_database
from app.routers import admin, ads, ai, behavior, user
from app.schemas import api_response

app = FastAPI(title="AdFalls Backend", version="0.1.0")
app.mount(
    "/materials",
    StaticFiles(directory=str(Path(__file__).resolve().parents[2] / "materials"), check_dir=False),
    name="materials",
)


@app.on_event("startup")
def on_startup() -> None:
    init_database()


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(
    request: Request,
    exc: RequestValidationError,
) -> JSONResponse:
    return JSONResponse(
        status_code=400,
        content=api_response(400, "请求参数错误", exc.errors()),
    )


@app.exception_handler(StarletteHTTPException)
async def http_exception_handler(
    request: Request,
    exc: StarletteHTTPException,
) -> JSONResponse:
    return JSONResponse(
        status_code=exc.status_code,
        content=api_response(exc.status_code, str(exc.detail), None),
    )


@app.get("/")
def root():
    return api_response(data={"name": "AdFalls Backend", "status": "running"})


app.include_router(ads.router)
app.include_router(ai.router)
app.include_router(behavior.router)
app.include_router(user.router)
app.include_router(admin.router)
