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

- `MainActivity.kt`：信息流首页、频道切换、搜索、刷新加载、曝光统计。
- `DetailActivity.kt`：广告详情页和互动操作。
- `AdAdapter.kt`：RecyclerView 多类型卡片适配器。
- `AdRepository.kt`：本地 mock 数据、互动状态、统计数据和搜索逻辑。
- `VideoPlaybackPool.kt`：模拟视频播放器资源复用。
- `res/layout/item_ad_large.xml`：大图广告卡片。
- `res/layout/item_ad_small.xml`：小图广告卡片。
- `res/layout/item_ad_video.xml`：视频广告卡片。

## 后续可接入方向

- 将 `AdRepository` 替换为真实网络请求，例如 OkHttp。
- 将本地 `summary` 和 `tags` 替换为云端大模型生成结果。
- 将视频卡片的模拟状态替换为 Media3/ExoPlayer 播放器。
- 将曝光和点击统计上报到服务端。
