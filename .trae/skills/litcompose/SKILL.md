---
name: litcompose
description: LitCompose（简易音乐播放器）项目的架构说明与开发指南。涵盖 UI/Domain/Data 分层、基础组件、网络层（Retrofit）、Room 数据库、消息传递（AppEventBus）、Media3 播放核心、主要页面功能与工程踩坑记录。适用于在 LitCompose 项目中开发功能、排查问题、生成代码时的项目上下文参考。
---

# LitCompose 项目 Skill 文档

> 本文件是 LitCompose（简易音乐播放器）项目的技能/架构说明文档，用于快速理解项目结构、各层职责、核心链路与开发约定，适合后续功能开发、问题排查时作为参考。

## 1. 项目概览

- 项目名：LitCompose —— 一个简易音乐播放器。
- 应用 ID：`com.example.litcompose`，minSdk 24 / targetSdk 36 / compileSdk 37。
- UI 技术：Jetpack Compose + Material 3 + Navigation Compose。
- 播放核心：Media3 ExoPlayer（含 MediaSession 通知栏控制）。
- 架构：遵循 Android 官方推荐架构，UI / Domain / Data 三层 + 手动依赖注入（无 Hilt）。

### 核心能力

| 能力 | 说明 |
| --- | --- |
| 本地播放 | 扫描系统 MediaStore 音频并播放 |
| 在线搜索 | iTunes 搜索 + COCO 渠道搜索（多搜索引擎，可解析真实播放链接） |
| 收藏 | 我的收藏歌单（跨源按 trackId 去重） |
| 歌单 | 自建歌单、添加/移除歌曲、长按拖拽排序、正序/倒序切换 |
| 歌词/封面 | COCO 渠道批量补全歌词与封面并本地缓存 |
| 断点续播 | 退出/被杀后恢复播放队列与进度 |
| 通知栏 | 前台服务 + MediaSession 通知，可控制播放 |
| 耳机断开 | 蓝牙/有线耳机断开自动暂停（ExoPlayer 内建 + Service 广播兜底） |
| 网页 | 内置 WebView 浏览器 tab |
| 主题 | 深色模式 + 5 种主题主色切换 |

## 2. 技术栈

| 分类 | 库 | 版本要点 |
| --- | --- | --- |
| UI | Jetpack Compose + Material 3 + BOM | `material-icons-extended`（跑马灯/拖拽等图标） |
| 导航 | androidx.navigation:navigation-compose | |
| 播放 | androidx.media3:exoplayer / common / session | MediaSession 通知，另需显式引入 androidx.media |
| 网络 | Retrofit + Moshi（kotlin reflect）+ OkHttp | 双 BaseUrl 实例：itunes / coco |
| 数据库 | Room 2.6.1 + KSP | 5 张表，版本 5 |
| 图片 | Coil 2.7.0（coil-compose） | AsyncImage / SubcomposeAsyncImage |
| 异步 | Kotlin Coroutines / Flow | MutableStateFlow 驱动 UI 状态 |
| 构建 | Gradle Kotlin DSL + Version Catalog | KSP、buildConfig=true（必须开启，否则 BuildConfig 无法引用） |

## 3. 目录结构

```
app/src/main/java/com/example/litcompose/
├── LitComposeApp.kt                 # Application：持有 AppContainer
├── MainActivity.kt                  # singleTask；ThemeController.init + enableEdgeToEdge
├── core/                            # 基础设施
│   ├── AppContainer.kt              # 手动依赖注入容器（所有依赖的组装点）
│   ├── AppEventBus.kt               # 全局事件总线（Snackbar）
│   ├── DispatcherProvider.kt        # IO/Main 调度器抽象
│   ├── ViewModelFactory.kt          # 通用 ViewModel Factory
│   └── ui/
│       ├── BaseViewModel.kt         # StateFlow + Channel effects 基类
│       ├── BaseComposeFragment.kt   # Fragment 混用场景的 Compose 基类
│       └── AppScaffold.kt           # Scaffold + Snackbar 事件消费
├── domain/                          # 纯 Kotlin 领域层
│   ├── model/Track.kt               # Track / LyricLine / RemoteTrackMeta
│   ├── repository/MusicRepository.kt# 仓库接口 + CollectionSummary
│   └── player/PlayerController.kt   # 播放控制器接口 + PlayerState + PlayMode
├── data/                            # 数据层
│   ├── db/                          # Room：5 张表 + DAO + 迁移
│   ├── remote/                      # ItunesApi / CoCoApi / NetworkClient / DTO
│   ├── repository/DefaultMusicRepository.kt
│   └── local/                       # MediaStore / TrackCache / TrackDownloader /
│                                    # LyricsEnricher / LastPlaybackStore
├── player/                          # 播放器实现 + 前台服务
│   ├── Media3PlayerController.kt    # ExoPlayer 实现，全局单例
│   └── MusicPlaybackService.kt      # MediaSession + 媒体通知 + 耳机断开兜底
└── ui/                              # UI 层
    ├── navigation/AppNav.kt         # NavHost + 迷你播放条 overlay
    ├── component/                   # MarqueeText / ReorderableLazyColumn
    ├── player/AppMiniPlayer.kt      # 底部迷你播放条
    ├── screen/                      # main / collection / search / scan /
    │                                # nowplaying / web 各页面 + ViewModel + Route
    ├── sheet/AddToCollectionSheet.kt# 添加到歌单底部弹窗
    └── theme/                       # Theme.kt / ThemeController.kt（主题切换）
```

## 4. 分层架构（UI / Domain / Data）

数据流遵循单向数据流：`UI → ViewModel(Intent) → Repository → (Room/Retrofit/MediaStore) → StateFlow → UI`。

```
┌────────────────────── UI 层 ──────────────────────┐
│ Screen(Compose) ← collectAsState ← ViewModel.state │
│      │                                      ↑      │
│      └── 用户操作 → ViewModel 方法 → Repository ──┘  │
└────────────────────────────────────────────────────┘
                          │
             ┌────────────┴─────────────┐
             ▼                          ▼
      Domain 接口（Repository / PlayerController）
             ▲
             │
      Data 实现（Room DAO / Retrofit API / MediaStore / SharedPreferences）
```

### 分层职责

- **UI 层**：Screen（Compose）+ Route（参数解析、ViewModel 创建）+ ViewModel。UI 只依赖 `ViewModel.state`，不直接触碰数据源。
- **Domain 层**：`Track`、`LyricLine` 等纯模型；`MusicRepository` 接口；`PlayerController` 接口与 `PlayerState`/`PlayMode`。不依赖 Android SDK 以外的具体实现。
- **Data 层**：`DefaultMusicRepository` 组装 MediaStore、iTunes/COCO API、TrackCache、Room DAO；`Media3PlayerController` 是 `PlayerController` 的 ExoPlayer 实现。

### ViewModel 基类（BaseViewModel）

- `state: StateFlow<S>`：`updateState(reducer)` 修改。
- `effects: Flow<E>`：一次性事件（`emitEffect`），经 Channel(BUFFERED) 发送。
- `launchIO / launchMain`：按 DispatcherProvider 分发协程。

## 5. 依赖注入（AppContainer）

- `LitComposeApp.onCreate()` 调用 `AppContainer.create(this)` 创建单例容器，`MainActivity` 及各 Screen 通过 `LocalContext` 拿 `(application as LitComposeApp).appContainer` 获取依赖。
- AppContainer 组装：Room 数据库（含迁移）、4 个 DAO、MediaStoreDataSource、iTunes/COCO API、TrackCache、LyricsEnricher、MusicRepository、TrackDownloader、PlayerController（含 LastPlaybackStore）。
- ViewModel 创建：统一 `ViewModelFactory(creator)`，由各 Route 提供 creator lambda。

## 6. 基础组件

| 组件 | 文件 | 说明 |
| --- | --- | --- |
| BaseViewModel | core/ui/BaseViewModel.kt | StateFlow 状态 + Channel 一次性事件 + IO/Main 协程帮助方法 |
| BaseComposeFragment | core/ui/BaseComposeFragment.kt | Fragment 中嵌入 ComposeView，`DisposeOnViewTreeLifecycleDestroyed` |
| AppScaffold | core/ui/AppScaffold.kt | Scaffold + SnackbarHost，自动消费 `AppEvent.ShowSnackbar` |
| ViewModelFactory | core/ViewModelFactory.kt | 通用 `ViewModelProvider.Factory` 实现 |
| MarqueeText | ui/component/MarqueeText.kt | 单行跑马灯：文本超宽才创建无限动画（按距离算时长），否则 Ellipsis；支持 style/color |
| ReorderableLazyColumn | ui/component/ReorderableLazyColumn.kt | 长按拖拽排序列表：`detectDragGesturesAfterLongPress` + 让位动画 + 拖起阴影；`onMove(from,to)` 回调 |
| AppMiniPlayer | ui/player/AppMiniPlayer.kt | 底部迷你播放条：封面/标题/上一曲/播放暂停/下一曲 |
| AddToCollectionSheet | ui/sheet/AddToCollectionSheet.kt | 添加到歌单的 ModalBottomSheet（支持新建歌单） |

## 7. 网络架构

- **客户端**：`NetworkClient`（object）用 Retrofit + Moshi（KotlinJsonAdapterFactory）+ OkHttp 构建两个 API 实例：
  - `ItunesApi`：baseUrl `https://itunes.apple.com/`，DEBUG 下挂 HttpLoggingInterceptor(BASIC)。
  - `CoCoApi`：baseUrl `https://cocodownloader.markqq.com/`，拦截器统一加 `Referer` 与浏览器 UA（防盗链）。
- **接口**：
  - `ItunesApi`：按关键词搜索歌曲。
  - `CoCoApi`：COCO 渠道的搜索（`CoCoProviders` 多搜索引擎）、歌曲解析（真实播放链接）、歌词/封面获取。
- **Repository 封装**（DefaultMusicRepository）：
  - `searchRemoteTracks`（iTunes）/ `searchCoCo(query, provider)`。
  - `resolveRemoteTracks`：批量解析真实播放链接，失败过滤。
  - `resolveTrackForRetry`：播放失败强制重解析（本地缓存优先），失败返回 null。
  - `fetchLyrics`：远程歌曲歌词（空列表 = 无歌词或本地歌曲）。
- **BuildConfig 注意**：`buildFeatures { buildConfig = true }` 必须显式开启，否则 `NetworkClient` 引用 `BuildConfig` 编译失败。

## 8. 数据库（Room）

- 数据库名 `litcompose.db`，当前版本 **5**，`exportSchema = false`，`@TypeConverters(Converters)`（RemoteTrackMeta ↔ JSON）。

### 表结构

| 表 | 实体 | 说明 |
| --- | --- | --- |
| favorite_tracks | FavoriteTrackEntity | 我的收藏（trackId 主键） |
| tracks | TrackEntity | 远程歌曲解析结果缓存（断点续播/缓存命中用） |
| collections | CollectionEntity | 自建歌单（name + createdAtMs） |
| collection_tracks | CollectionTrackCrossRef | 歌单↔歌曲关联：collectionId + trackId + addedAtMs + **position**（排序） |
| lyrics_cache | LyricsCacheEntity | 歌词/封面补全缓存：linesJson + artworkPath + updatedAtMs |

### 迁移历史

| 迁移 | 内容 |
| --- | --- |
| 2 → 3 | tracks 表加 `remote` 列 |
| 3 → 4 | 新建 lyrics_cache 表 |
| 4 → 5 | collection_tracks 加 `position` 列（默认 0），歌单内手动排序 |

- 新增迁移必须同时注册到 `AppContainer` 的 `databaseBuilder.addMigrations(...)`。

### DAO 要点

- `CollectionDao.observeTracksInCollection` 排序：`ORDER BY ct.position ASC, ct.addedAtMs DESC`。
- `CollectionDao.updateTrackPosition(collectionId, trackId, position)`：写排序。
- `LyricsCacheDao`：按 trackId 读写歌词/封面缓存。

## 9. 消息传递架构

- **全局事件总线**：`AppEventBus`（core/AppEventBus.kt），`MutableSharedFlow<AppEvent>`（容量 64，DROP_OLDEST）。
  - 事件类型：`AppEvent.ShowSnackbar(message)`。
  - `AppScaffold` 用 `LaunchedEffect` 收集 events 并弹 Snackbar；Repository/ViewModel 通过 `eventBus.tryEmit(...)` 发提示。
- **ViewModel 一次性事件**：`BaseViewModel.effects`（Channel BUFFERED），用于 UI 单次消费（如跳转、Toast），与 `state` 区分。
- **跨层状态**：播放状态通过 `PlayerController.state: StateFlow<PlayerState>` 全局共享，UI 各页面 `collectAsState` 订阅。

## 10. 播放核心

- **PlayerController 接口**（domain/player/PlayerController.kt）：
  - `state: StateFlow<PlayerState>`（current / isPlaying / positionMs / durationMs / queue / queueTitle / playMode …）。
  - `play / playQueue(tracks, startIndex, queueTitle) / togglePlayPause / seekTo / skipTo / skipToPrevious / skipToNext / setPlayMode(LIST_LOOP|SINGLE_LOOP|SHUFFLE) / release`。
- **Media3PlayerController**（player/Media3PlayerController.kt，全局单例）：
  - MediaItem 的 `localConfiguration.tag` 携带 `Track`，切歌时从 tag 恢复当前曲目。
  - 进度轮询：播放中 500ms / 暂停 1000ms；播放中每 3s 落盘一次进度。
  - 断点续播：`LastPlaybackStore`（SharedPreferences JSON）保存队列/索引/进度，启动时恢复（暂停态）。
  - 远程 403 重试：`onPlayerError` → 强制重解析新链接 → `replaceMediaItem` 续播（限制 2 次）。
  - 后台缓存：播放成功后 `cacheTrack` 幂等缓存到 MediaStore（流式写盘 + 并发信号量 2，防 OOM）。
  - 耳机断开：`setHandleAudioBecomingNoisy(true)`。
  - 播放/暂停时自动启动 `MusicPlaybackService`（前台服务）。
- **MusicPlaybackService**（player/MusicPlaybackService.kt）：
  - `MediaSession` 复用全局 ExoPlayer 实例；构建 MediaStyle 通知（上一曲/播放暂停/下一曲），`ACTION_PLAY` 媒体按钮走 MediaSession 回调。
  - 动态注册 `ACTION_AUDIO_BECOMING_NOISY` 接收器兜底暂停并打日志。
- **MainActivity**：`launchMode="singleTask"`，保证通知栏点击复用同一实例（否则退出需两次）。

## 11. 主要页面功能

### 主页（MainTabsScreen）
- 底部 4 个 tab（纯图标，64dp）：**主页(我的歌单) / 搜索 / 扫描 / 网页**。
- `rememberSaveableStateHolder` 保持各 tab 内部状态（搜索词、网页地址等）。
- tab 0~2 且有播放任务时，底部显示 AppMiniPlayer（网页 tab 不显示，避免遮挡）。

### 我的歌单（CollectionsScreen）
- 歌单列表（收藏 + 自建），点击进入详情；TopBar 提供：设置、新建歌单（对话框）、一键"获取歌词"（批量补全歌词与封面，带进度对话框）。

### 歌单详情（CollectionDetailScreen）
- 歌曲列表：歌名跑马灯（大号） + 歌手（小号次要色），点击播放；歌名/歌手视觉区分。
- 长按拖拽排序 + 标题行 SwapVert 按钮切换正序/倒序（DB position 持久化）。
- "播放全部"；每行 ⋮ 菜单：添加到其他歌单 / 从歌单移除 / 收藏切换。

### 搜索（SearchScreen）
- 搜索框（一键清空）→ 结果列表（跑马灯）；FilterChip 切换搜索引擎（CoCoProviders）。
- 歌曲操作：播放、下载（TrackDownloader）、添加到歌单。

### 扫描本地（ScanLocalScreen）
- 申请音频权限（Android 13+ `READ_MEDIA_AUDIO`），扫描 MediaStore 本地歌曲。
- 支持单曲操作与批量加入歌单（多选）。

### 播放详情（NowPlayingScreen）
- 专辑封面（Coil SubcomposeAsyncImage：**加载中/加载失败显示黑胶唱片占位**）。
- 黑胶唱片占位：径向渐变 + 同心圆纹路 + 主色中心标签 + 高光弧；**仅播放时旋转**（基于帧的 30°/s，暂停即停、恢复无缝续转）。
- 歌词面板（滚动高亮当前行）、播放控制（上一曲/播放暂停/下一曲、进度条、播放模式切换：列表/单曲/随机）。

### 网页（WebBrowserScreen）
- AndroidView 内嵌 WebView，默认地址 `https://cocodownloader.markqq.com/?src=www.jspoo.com`。
- 36dp 高 BasicTextField 地址栏（可跳转）、前进/后退、加载进度条；离开时 stopLoading + destroy。

## 12. 主题系统

- `ThemeController`（object 单例）：`darkTheme` / `accent` 两个 `mutableStateOf`（private set）+ SharedPreferences 持久化；`init(context)`、`setDark`、`setAccentColor`。
- `LitComposeTheme` 无参：读 ThemeController 状态，`(dark?Dark:Light).copy(primary = accent.dark/light)` 动态换主色。
- 5 种主色：AccentColor 枚举（薄荷 / 紫 / 蓝 / 橙 / 粉），在歌单页设置底部弹窗切换。

## 13. 工程约定与踩坑记录

- **buildConfig**：必须开启 `buildFeatures { buildConfig = true }`，否则 `BuildConfig` 无法引用。
- **ViewModelFactory**：构造函数参数名不能与 `ViewModelProvider.Factory.create` 同名（如也叫 `create`），否则 Kotlin 解析歧义引发无限递归 StackOverflowError。
- **MediaItem 携带 Track**：通过 `setTag(track)`，切歌时从 tag 恢复，避免状态丢失。
- **上一曲/下一曲**：手动 `(index ± 1 + count) % count` 取模实现列表循环，不能依赖 hasPrevious/hasNext。
- **断点续播兼容**：`Track.artworkUrl` 默认 null（Moshi 不写出 null 键），反序列化需默认值，否则历史存档解析失败。
- **大批量下载防 OOM**：流式 8KB 分块写盘 + 并发信号量限制（2），歌单播放全部时不会堆爆。
- **蓝牙断开暂停**：ExoPlayer 内建 `handleAudioBecomingNoisy` + Service 动态注册广播双保险；仅在音频正走耳机输出时生效。
- **通知栏**：`ContextCompat.startForegroundService` 幂等启动服务；`singleTask` 避免任务栈叠加。
- **歌词/封面批量补全**：COCO 渠道匹配，结果缓存 lyrics_cache；`"[]"` 表示匹配成功但接口无歌词（避免反复请求）。
