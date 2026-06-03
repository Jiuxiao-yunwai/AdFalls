# 阶段 6：网络和 AI 服务

阶段 6 先采用本地模拟远程服务，不强依赖真实服务器。

## 设计目标

- 保持现有 Room + Flow + ViewModel 状态同步不变。
- 新增 `data/remote` 层，让 Repository 以“请求远程服务”的方式获取广告数据。
- 当前没有服务器时，使用 `FakeAdRemoteDataSource` 从本地 Room 广告池读取数据。
- 后续有服务器时，将 FakeRemote 替换为 OkHttpRemote 即可。

## 当前数据流

```text
MainActivity
    ↓
FeedViewModel
    ↓
AdRepository
    ↓
FakeAdRemoteDataSource
    ↓
Room 广告池
```

## FakeRemote 行为

- `fetchAdPage(channel, requestedIds, pageSize)`：模拟频道分页广告接口。
- `searchAds(channel, query)`：预留搜索接口，当前从 Room 广告池匹配标题、品牌、摘要和标签。
- `delay(450)`：模拟网络请求耗时。

## 为什么这样做

真实网络请求需要后端服务器。在当前没有服务器的情况下，FakeRemote 可以保留网络层结构和调用方式，让项目仍然体现：

- 数据获取方式设计。
- Repository 统一调度数据源。
- 本地缓存和状态同步。
- 后续替换真实接口的扩展点。

## 后续替换方案

```text
FakeAdRemoteDataSource
    ↓
OkHttpAdRemoteDataSource
    ↓
真实广告接口 / AI 服务接口
```

替换后，Repository 仍负责把远程返回数据写入 Room，页面继续观察 Room Flow。
