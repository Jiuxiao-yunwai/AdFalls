# MVVM 阶段 1 设计

阶段 1 只做基础结构改造，不接 Room、OkHttp、真实 AI 服务，也不引入 Compose。

## 设计目标

- 保持现有 XML + RecyclerView 页面和交互不变。
- 将频道、搜索词、加载状态和广告互动入口从 Activity 下沉到 ViewModel。
- 保留内存 `AdRepository` 作为临时数据源，为阶段 2 接 Room 预留 Repository 边界。
- 保留 `VideoPlaybackPool` 作为视频播放、暂停、静音状态的模拟缓存层。

## 数据流

```text
MainActivity / DetailActivity
        ↓
FeedViewModel / DetailViewModel
        ↓
AdRepository
        ↓
内存 Mock 数据
```

视频状态：

```text
ViewModel -> VideoPlaybackPool -> AdRepository -> UI 重新渲染
```

## 页面职责

### 首页

`MainActivity` 只处理：

- 初始化 Tab、搜索框、下拉刷新、RecyclerView。
- 监听点击、搜索、刷新、滚动。
- 调用 `FeedViewModel`。
- 根据 `FeedViewModel.ads` 渲染列表。
- 保存 RecyclerView 滚动位置，这属于纯 UI 状态。

`FeedViewModel` 负责：

- 保存 `activeChannel`、`searchText`、`loadingMore`。
- 维护当前广告列表 `ads`。
- 处理频道切换、搜索、刷新、加载更多。
- 处理点击、曝光、点赞、收藏、分享、视频播放和静音。

### 详情页

`DetailActivity` 只处理：

- 初始化返回、点赞、收藏、分享、视频按钮。
- 调用 `DetailViewModel`。
- 根据 `DetailViewModel.ad` 渲染详情。

`DetailViewModel` 负责：

- 根据 `adId` 从 Repository 获取详情。
- 处理点赞、收藏、分享。
- 处理视频播放、暂停、播放切换和静音切换。

## 状态同步

- 列表页和详情页仍共享同一个内存 `AdRepository`。
- 详情页修改点赞、收藏、分享、视频状态后，首页 `onResume()` 会调用 `FeedViewModel.sync()` 刷新当前列表数据。
- `FeedViewModel` 每次从 Repository 读取时都会生成新的 List，保证 `ListAdapter` 能执行 Diff 更新。

## 后续衔接

- 阶段 2 可以在 `data/repository` 后面接入 Room，不需要再让 Activity 直接感知数据来源。
- 阶段 3 可以把当前手动 `sync()` 改为观察 Room 或 Flow，以实现自动状态同步。
