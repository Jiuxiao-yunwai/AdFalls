# AdFalls

AdFalls 是一个单列广告信息流 App demo，使用 Kotlin + 传统 XML 布局实现。当前版本以本地 mock 数据模拟广告推荐、AI 摘要、智能标签、对话式搜索、互动状态和埋点统计。

## 已实现功能

- 单列广告信息流：基于 RecyclerView 实现列表复用和流畅滚动。
- 多样式广告卡片：支持大图、小图、视频三种卡片样式。
- 频道切换：顶部 Tab 支持精选、电商、本地三个频道，切换时刷新对应数据并保持列表位置。
- 下拉刷新与上拉加载：SwipeRefreshLayout 下拉刷新，滚动到底部自动加载更多 mock 数据。
- 详情页交互：点击卡片进入详情页，返回后列表位置保持。
- 状态同步：点赞、收藏、分享、视频播放/暂停、静音状态在信息流和详情页之间共享。
- 模拟播放器资源池：同一时间只保留一个视频处于播放状态。
- 埋点统计：本地模拟曝光、点击、点赞、分享等统计数据，并在卡片和详情页展示。
- AI 能力降级：广告摘要、智能标签和自然语言搜索先由本地数据模拟，后续可替换为云端大模型接口。

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
- `app/src/main/java/com/example/adfalls/viewmodel`：首页和详情页的页面状态、交互逻辑。
- `app/src/main/java/com/example/adfalls/data/model`：广告频道、卡片类型和广告数据模型。
- `app/src/main/java/com/example/adfalls/data/local`：Room 数据库、广告 Entity 和 DAO。
- `app/src/main/java/com/example/adfalls/data/repository`：Room 读写入口、本地 mock 数据初始化、互动状态、统计数据和搜索逻辑。
- `app/src/main/java/com/example/adfalls/cache`：模拟视频播放器资源复用。
- `res/layout/item_ad_large.xml`：大图广告卡片。
- `res/layout/item_ad_small.xml`：小图广告卡片。
- `res/layout/item_ad_video.xml`：视频广告卡片。

## 当前阶段

当前项目已完成阶段 3：Room Flow + ViewModel StateFlow 自动状态同步，下一步进入网络和 AI 服务的后续阶段。

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
- 将本地 `summary` 和 `tags` 替换为云端大模型生成结果。
- 将视频卡片的模拟状态替换为 Media3/ExoPlayer 播放器。
- 将曝光和点击统计上报到服务端。
