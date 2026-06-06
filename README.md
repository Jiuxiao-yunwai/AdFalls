# AdFalls

AdFalls 是一个单列广告信息流 App demo，使用 Kotlin + 传统 XML 布局实现，并接入 Python + FastAPI 后端。当前版本支持服务端广告信息流、随机刷新、搜索、AI 对话式推荐、单条广告 AI 分析、收藏/点赞/曝光行为上报，以及一个轻量网页端曝光管理后台。

## 已实现功能

- 单列广告信息流：基于 RecyclerView 实现列表复用和流畅滚动。
- 多样式广告卡片：支持大图、小图、视频三种卡片样式。
- 频道切换：顶部 Tab 支持精选、电商、本地三个频道，切换时带滑动动画并保持列表位置。
- 下拉刷新与上拉加载：SwipeRefreshLayout 下拉刷新，服务端随机返回新一组广告，滚动到底部自动加载更多。
- 详情页交互：点击卡片进入详情页，返回后列表位置保持。
- 状态同步：点赞、收藏、分享、视频播放/暂停、静音状态在信息流和详情页之间共享，并写入 Room；点赞、收藏和用户行为会同步到后端。
- 信息流视频自动播放：视频广告完全进入列表可视区域后自动播放，完全离屏后自动暂停。
- 视频控件：自动播放时隐藏播放和进度控件，点击视频后显示中央播放/暂停和底部进度，1 秒无操作后淡出；右上角静音按钮常驻并在视频间共享静音状态。
- Media3 播放器资源池：同一时间只保留一个视频处于播放状态，暂停时复用 ExoPlayer 并保留播放进度。
- 埋点统计：曝光、点击、详情查看、点赞、收藏、视频播放等行为会上报后端，后台可按广告查看。
- 标签筛选：点击广告标签后筛选当前频道广告，并用小字提示当前筛选标签。
- AI 对话式搜索：首页左上角提供 AI 搜索入口，后端随机抽取广告候选并交给大模型返回最合适的广告。
- 广告 AI 分析：详情页“让 AI 分析”会返回当前广告的人群、卖点和投放建议，不再返回广告推荐卡片。
- 后台管理：`/admin/exposure` 可查看曝光、点击、详情、点赞、收藏、视频播放、CTR 等指标，并支持表头排序。

## 运行方式

```bash
./gradlew :app:assembleDebug
```

Windows 下：

```powershell
.\gradlew.bat :app:assembleDebug
```

构建产物位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 主要结构

- `app/src/main/java/com/example/adfalls/ui/feed`：信息流首页和 RecyclerView 多类型卡片适配器。
- `app/src/main/java/com/example/adfalls/ui/detail`：广告详情页。
- `app/src/main/java/com/example/adfalls/ui/aichat`：AI 对话式搜索页和聊天消息适配器。
- `app/src/main/java/com/example/adfalls/viewmodel`：首页、详情页、AI 聊天页的页面状态和交互逻辑。
- `app/src/main/java/com/example/adfalls/data/model`：广告频道、卡片类型、广告数据和 AI 聊天消息模型。
- `app/src/main/java/com/example/adfalls/data/local`：Room 数据库、广告 Entity 和 DAO。
- `app/src/main/java/com/example/adfalls/data/remote`：FastAPI 网络客户端、广告分页 FakeRemote、AI Chat 请求/响应 DTO 和模拟服务端响应。
- `app/src/main/java/com/example/adfalls/data/repository`：Room 读写入口、本地 mock 数据初始化、互动状态、统计数据、搜索逻辑和 AI Chat DTO 转换。
- `app/src/main/java/com/example/adfalls/cache`：Media3 ExoPlayer 共享播放器资源复用。
- `res/layout/item_ad_large.xml`：大图广告卡片。
- `res/layout/item_ad_small.xml`：小图广告卡片。
- `res/layout/item_ad_video.xml`：视频广告卡片。

## 当前阶段

当前处于阶段 6：网络和 AI 服务进行中。

已完成阶段 1-5：MVVM 基础结构、Room 数据层、Room Flow + ViewModel StateFlow 状态同步、信息流体验完善，以及 Media3 视频能力。当前阶段已接入 FastAPI 后端，保留 FakeRemote 作为服务不可用时的降级路径。

下一步可继续完善认证、图片/视频素材托管、服务端排序分页，以及更严格的 AI 响应校验。

阶段进度见：

- `docs/项目管理/进度.md`

技术和开发约定见：

- `docs/README.md`
- `docs/项目管理/开发路线.md`
- `docs/架构设计/技术.md`
- `docs/架构设计/架构方案.md`
- `docs/架构设计/数据与状态设计.md`
- `docs/架构设计/模块说明.md`
- `docs/阶段设计/阶段1-MVVM基础结构.md`
- `docs/阶段设计/阶段2-Room数据层.md`

## 后续可接入方向

- 将 `AdRepository` 替换为真实网络请求，例如 OkHttp。
- 将 `AiChatRemoteDataSource` 替换为真实 `POST /api/ai-search/chat` 请求，并把响应解析为 `AiChatResponseDto`。
- 将本地 `summary` 和 `tags` 替换为云端大模型生成结果。
- 将曝光和点击统计上报到服务端。

## 简易后端服务

本仓库同时提供一个 Python 3 + FastAPI + SQLite + SQLAlchemy 的简易广告信息流后端服务，后端代码位于 `backend/`。服务启动时会自动创建 SQLite 数据库 `adfalls.db`，并在广告表为空时优先读取项目根目录的 `data/ads.json` 初始化广告数据；如果没有数据文件，会使用内置测试广告兜底。`materials/` 已挂载为静态目录，后续图片可以通过 `/materials/...` 访问。

### 后端接口

```text
GET    /api/ads/feed
GET    /api/ads/{id}
GET    /api/ads/search
POST   /api/ai/chat-search
POST   /api/ai/ad-analysis
POST   /api/behavior
POST   /api/user/login
GET    /api/user/{userId}/favorites
POST   /api/user/{userId}/favorites/{adId}
DELETE /api/user/{userId}/favorites/{adId}
POST   /api/user/{userId}/likes/{adId}
DELETE /api/user/{userId}/likes/{adId}
GET    /api/admin/ads/metrics
GET    /admin/exposure
```

所有接口统一返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

### 安装依赖

建议使用虚拟环境：

```powershell
cd backend
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

### 启动服务

如果要启用 AI 对话式广告推荐，先复制 `backend/.env.example` 为 `backend/.env`，填入你的 API Key：

```text
ADFALLS_TEXT_API_KEY=你的 API Key
ADFALLS_TEXT_API_BASE_URL=https://token-plan-cn.xiaomimimo.com/v1
ADFALLS_TEXT_MODEL=mimo-v2.5
ADFALLS_TEXT_API_TIMEOUT=60
```

```powershell
cd backend
.\.venv\Scripts\Activate.ps1
python -B -m uvicorn app.main:app --reload
```

启动后可访问：

```text
http://127.0.0.1:8000/docs
```

### 主要接口测试示例

```powershell
curl "http://127.0.0.1:8000/api/ads/feed?channel=featured&cursor=&size=10&userId=1"
curl "http://127.0.0.1:8000/api/ads/1?userId=1"
curl "http://127.0.0.1:8000/api/ads/search?keyword=耳机&cursor=&size=10&userId=1"

curl -X POST "http://127.0.0.1:8000/api/user/login" `
  -H "Content-Type: application/json" `
  -d "{\"username\":\"test\",\"password\":\"123456\"}"

curl -X POST "http://127.0.0.1:8000/api/ai/chat-search" `
  -H "Content-Type: application/json" `
  -d "{\"userId\":1,\"message\":\"我想找适合学生用的平价耳机\",\"cursor\":\"\",\"size\":10}"

curl -X POST "http://127.0.0.1:8000/api/ai/ad-analysis" `
  -H "Content-Type: application/json" `
  -d "{\"userId\":1,\"adId\":1,\"message\":\"请分析这条广告的目标人群和核心卖点\"}"

curl -X POST "http://127.0.0.1:8000/api/behavior" `
  -H "Content-Type: application/json" `
  -d "{\"userId\":1,\"adId\":1,\"channel\":\"featured\",\"behaviorType\":\"CLICK\"}"

curl -X POST "http://127.0.0.1:8000/api/user/1/favorites/1"
curl "http://127.0.0.1:8000/api/user/1/favorites?cursor=&size=10"
curl -X DELETE "http://127.0.0.1:8000/api/user/1/favorites/1"
curl -X POST "http://127.0.0.1:8000/api/user/1/likes/1"
curl -X DELETE "http://127.0.0.1:8000/api/user/1/likes/1"
```

### Android 客户端接入

Android 客户端默认通过 `BuildConfig.ADFALLS_API_BASE_URL` 调用后端：

```text
http://10.0.2.2:8000
```

这是 Android 模拟器访问宿主机本地服务的地址。先启动后端：

```powershell
cd backend
.\.venv\Scripts\Activate.ps1
python -B -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

如果使用真机调试，需要把 [app/build.gradle.kts](/D:/Users/Jiuxiao/Documents/MyProjects/AndroidStudioProjects/AdFalls/app/build.gradle.kts) 里的 `ADFALLS_API_BASE_URL` 改成电脑的局域网地址，例如：

```kotlin
buildConfigField("String", "ADFALLS_API_BASE_URL", "\"http://192.168.1.8:8000\"")
```

信息流接口使用随机分页：`cursor` 记录当前加载会话已见过的广告 id。首次请求和下拉刷新传空 cursor，加载更多传上一次返回的 `nextCursor`，同一轮加载不会重复返回已见广告。

### 广告曝光管理

后端提供一个轻量管理页，用于按广告查看曝光、点击、详情查看、点赞事件、取消点赞、当前点赞、收藏事件、取消收藏、当前收藏、视频播放和 CTR：

```text
http://127.0.0.1:8000/admin/exposure
```

页面数据来自：

```text
GET /api/admin/ads/metrics
```

管理页支持点击表头排序。默认按 ID 倒序，数值列如曝光、点击、CTR、点赞和收藏默认按降序切换。

可选参数：

```text
channel: featured / ecommerce / local
keyword: 标题、内容、摘要、标签模糊搜索
cursor: 上一次返回的 nextCursor
size: 1-200，默认 50
```

示例：

```powershell
curl "http://127.0.0.1:8000/api/admin/ads/metrics?channel=featured&size=50"
```

### 生成测试广告数据

脚本位置：

```text
backend/scripts/generate_ads_data.py
```

文本 API 使用环境变量配置，API Key 不写入代码：

```powershell
cd backend
.\.venv\Scripts\Activate.ps1

$env:ADFALLS_TEXT_API_KEY="你的 API Key"
$env:ADFALLS_TEXT_API_BASE_URL="https://token-plan-cn.xiaomimimo.com/v1"
$env:ADFALLS_TEXT_MODEL="mimo-v2.5"

python -B scripts/generate_ads_data.py --count 15
```

如果接口响应较慢，可以降低每批生成数量，并拉长超时时间：

```powershell
python -B scripts/generate_ads_data.py --count 50 --batch-size 5 --timeout 180
```

需要逐条请求并逐条看到进度时：

```powershell
python -B scripts/generate_ads_data.py --count 50 --batch-size 1 --timeout 180
```

输出文件位于项目根目录：

```text
data/
  ads.csv
  ads.json
  image_prompts.json

materials/
  images/
  videos/
```

如果暂时不调用文本 API，可使用本地兜底数据：

```powershell
python -B scripts/generate_ads_data.py --count 15 --use-fallback
```

图片生成默认关闭。脚本会先写入本地相对路径，例如 `materials/images/ad_001_cover.png`，并把图片提示词保存到 `data/image_prompts.json`。如后续接入图片 API，可配置：

```powershell
$env:ADFALLS_IMAGE_API_KEY="你的图片 API Key"
$env:ADFALLS_IMAGE_API_BASE_URL="你的图片 API Base URL"
$env:ADFALLS_IMAGE_MODEL="你的图片模型名"

python -B scripts/generate_ads_data.py --count 15 --generate-images
```
