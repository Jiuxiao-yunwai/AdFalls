# 阶段 6：网络和 AI 服务

阶段 6 先采用本地模拟远程服务，不强依赖真实服务器。

## 设计目标

- 保持现有 Room + Flow + ViewModel 状态同步不变。
- 新增 `data/remote` 层，让 Repository 以“请求远程服务”的方式获取广告数据。
- 当前没有服务器时，使用 `FakeAdRemoteDataSource` 从本地 Room 广告池读取数据。
- AI 对话式搜索先使用 `AiChatRemoteDataSource` 返回模拟服务端 DTO。
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

AI 对话式搜索：

```text
AiChatActivity
    ↓
AiChatViewModel
    ↓
AiChatRepository
    ↓
AiChatRemoteDataSource
    ↓
AiChatResponseDto
    ↓
AiChatMessage
```

## FakeRemote 行为

- `fetchAdPage(channel, requestedIds, pageSize)`：模拟频道分页广告接口。
- `searchAds(channel, query)`：预留搜索接口，当前从 Room 广告池匹配标题、品牌、摘要和标签。
- `delay(450)`：模拟网络请求耗时。

## AI Chat FakeRemote 行为

- `AiChatRepository.sendMessage(channel, query, history)` 仍向 ViewModel 返回 `List<AiChatMessage>`，UI 层不感知服务端 DTO。
- Repository 负责构造 `AiChatRequestDto`，其中 `channel` 使用频道枚举名，`query` 使用本次输入，`history` 只保留 `USER` 和 `ASSISTANT` 消息。
- `AiChatRemoteDataSource.sendMessage(request)` 返回 `AiChatResponseDto`，结构贴近未来服务端 JSON。
- `AiChatResponseDto.toChatMessages()` 将服务端 `messages` 数组转换为多条 `AiChatMessage`，并把 `adIds` 传入 `relatedAdIds`。
- 当前用 `delay(800)` 模拟 AI 服务响应耗时。

当前 DTO 结构：

```text
AiChatRequestDto(
    channel: String,
    query: String,
    history: List<AiChatHistoryDto>
)

AiChatHistoryDto(
    role: String,
    content: String
)

AiChatResponseDto(
    messages: List<AiChatMessageDto>
)

AiChatMessageDto(
    type: String,
    content: String,
    adIds: List<Long>
)
```

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

AI 对话式搜索后续替换点：

```text
AiChatRemoteDataSource
    ↓
OkHttp POST /api/ai-search/chat
    ↓
AiChatResponseDto
    ↓
AiChatRepository.toChatMessages()
```

替换后，`AiChatActivity` 和 `AiChatViewModel` 仍只处理 `AiChatMessage`。
