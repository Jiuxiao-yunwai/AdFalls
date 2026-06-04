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

- `AiChatRepository.sendMessage(query, history)` 仍向 ViewModel 返回 `List<AiChatMessage>`，UI 层不感知服务端 DTO。
- Repository 负责构造 `AiChatRequestDto`，其中 `channel` 固定为兼容服务端结构的 `ALL`，`query` 使用本次输入，`history` 只保留 `USER` 和 `ASSISTANT` 消息。
- `AiChatRemoteDataSource.sendMessage(request)` 返回 `AiChatResponseDto`，结构贴近未来服务端 JSON。
- `AiChatRemoteDataSource` 当前使用本地关键词规则模拟模型识别需求维度，并从全部 mock 广告池返回真实推荐 ID；未命中关键词时使用跨维度默认推荐。
- `AiChatResponseDto.toChatMessages()` 将服务端 `messages` 数组转换为多条 `AiChatMessage`，并把 `adIds` 传入 `relatedAdIds`。
- 当前用 `delay(800)` 模拟 AI 服务响应耗时。

## 当前 AI 对话式搜索闭环

- 首页已经提供 AI 对话式搜索入口，AI 会话不再跟随首页当前频道切换。
- Fake AI 服务会根据自然语言需求自动识别维度，并从完整 `1–50` mock 广告池跨精选、电商、本地返回推荐广告 ID。
- 带 `relatedAdIds` 的助手消息会展示可点击的推荐广告卡片。
- 推荐广告卡片会从本地广告池补全标题、品牌、摘要和标签，以接近首页信息流的无图链接列表展示；每条广告都可独立进入 `DetailActivity`。
- 详情页提供“让 AI 分析这个广告”入口，会将广告上下文单独传入 Fake AI；返回内容是广告定位、适合人群和投放建议，不再复述搜索问题。
- 用户和助手消息写入 Room 的 `ai_chat_messages` 表，并通过统一全局会话恢复持续对话；`LOADING` 和 `ERROR` 状态不持久化。
- 当前仍是纯本地模拟远程服务，没有接入真实大模型、真实服务器或 API Key。

当前 DTO 结构：

```text
AiChatRequestDto(
    channel: String, // 当前固定为 ALL，保留服务端 DTO 兼容性
    query: String,
    history: List<AiChatHistoryDto>,
    contextAd: AiChatAdContextDto?
)

AiChatHistoryDto(
    role: String,
    content: String
)

AiChatAdContextDto(
    id: Long,
    title: String,
    brand: String,
    summary: String,
    detail: String,
    tags: List<String>
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

当前本地关键词规则和推荐卡片闭环可继续用于离线演示；后续只需将
`AiChatRemoteDataSource` 替换为 OkHttp `POST /api/ai-search/chat` 实现，并保持现有 DTO
和 Repository 转换边界即可。

当前聊天数据流补充：

```text
AiChatViewModel
    ↓ observe / insert
AiChatRepository
    ↓
Room ai_chat_messages
```

`AiChatRepository` 加载历史推荐消息时，会使用 `relatedAdIds` 从广告池补全无图广告链接所需的信息。Room 版本 5 会将旧的分频道聊天记录迁移到统一的 `global` 会话。
