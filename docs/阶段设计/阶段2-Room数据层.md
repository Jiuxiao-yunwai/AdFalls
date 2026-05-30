# 阶段 2：Room 数据层

阶段 2 接入 Room，但不改 UI 样式，不接 OkHttp，不接真实 AI 服务，也不引入 Compose。

## 目标

- 新增 Room 本地数据库层。
- 保留现有 mock 数据生成逻辑。
- 首次启动时把 50 条固定 mock 数据写入 Room。
- 后续列表、详情和互动状态从 Room 读写。
- 继续保持 Activity 和 ViewModel 不直接访问 Room、DAO、Entity。

## 数据流

```text
Activity
    ↓
ViewModel
    ↓
AdRepository
    ↓
AdDao / Room
```

## 本地数据层

`data/local` 包含：

- `AppDatabase`：Room 数据库入口。
- `AdEntity`：广告表实体。
- `AdDao`：广告查询、插入和状态更新接口。

`AdEntity.tags` 暂时用逗号分隔字符串保存，Repository 对外仍转换成 `List<String>`。

## Repository 改造

`AdRepository` 现在负责：

- 初始化 Room。
- 首次启动写入 50 条固定 mock 广告。
- 从 Room 读取频道列表和详情。
- 基于 Room 数据执行搜索。
- 维护当前频道已展示 ID 和已请求过 ID。
- 下拉刷新或首次进入频道时，从未请求过的数据中取下一页。
- 上滑加载时从未请求过的数据中追加下一页。
- 当前频道没有未请求数据时，加载失败并由前端展示“到底了”。
- 点赞、收藏、分享、点击、曝光写入 Room。
- 视频播放、暂停、静音状态写入 Room。

## 临时约定

本阶段仍沿用手动 `sync()` 刷新页面。为了不在阶段 2 扩大 ViewModel 和 UI 改动，Room 当前启用同步 DAO 查询和 `allowMainThreadQueries()`。

阶段 3 再把列表页和详情页改为观察同一份 Room 数据，例如 Flow 或 LiveData，并移除手动同步。
