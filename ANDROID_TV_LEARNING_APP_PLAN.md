# Android TV 國文影片課學習 App — 完整 UI、資料與開發計畫

> 目標平台：Android TV / Google TV
>
> 本文件是 `mia_chinese` 的產品與實作規格。它包含原始需求，以及本次 review 補強的播放器風險、非同步狀態、內容更新、穩定 ID、生命週期與「繼續上次進度」流程。
>
> **目前狀態（v0.1.11 / versionCode 12）**：Phase 0A、Phase 1、Phase 2 與 MP4 播放核心已完成；Phase 0B 已判定 YouTube No-Go，release 維持 MP4-only。真實目標 TV、媒體搬遷與 PDF／家長功能仍是後續工作。

## 0. Repository 與工作約定

### 0.1 唯一工作 repository

- Git remote：`git@github.com:victorchentw/mia_chinese.git`
- 本機 canonical working tree：`/mnt/ssd/mia_chinese`
- repository 已建立 Android TV 單 module project；目前工作樹以 `v0.1.11` 進行 release hardening。
- 本文件應放在 repository root：`ANDROID_TV_LEARNING_APP_PLAN.md`。
- 每個 Phase 完成一個可編譯、可驗收的 commit；沒有明確要求時不自動 push。

### 0.2 SSH key

依 `/mnt/ssd/vvdoc/wiki/reference/credentials-and-keys.md` 的路徑說明，既有 GitHub SSH key 為：

```text
/mnt/ssd/vvdoc/key/id_rsa
```

使用方式範例：

```bash
GIT_SSH_COMMAND='ssh -i /mnt/ssd/vvdoc/key/id_rsa -o IdentitiesOnly=yes' \
  git clone git@github.com:victorchentw/mia_chinese.git /mnt/ssd/mia_chinese
```

規則：

- 不把 private key、token、密碼或 keystore 複製到 repository。
- 不修改、改名或刪除 `/mnt/ssd/vvdoc` 下既有 key。
- 不在 log、commit 或本文件貼出 key 內容。
- Git 操作使用既有 key；Android signing key 若未另行確認，不列入 MVP。

---

## 1. 產品目標與範圍

將 Notion 課程頁面的教材結構帶到 Android TV：小朋友以電視遙控器選擇出版社、學期、課次與影片，在播放時能依老師指示對紙本課本做筆記，暫停、快轉與倒轉；離開後可以快速回到上一次閱讀／播放的位置。

支援兩種影片來源：

- **MP4**：使用 Media3 / ExoPlayer 原生播放。
- **YouTube**：使用 Android WebView 內嵌 YouTube IFrame Player API 播放。

兩種播放器都實作共同的 `LessonPlayer` 介面，讓課程畫面與遙控器操作不依賴來源類型。

### 1.1 MVP 必須做到

1. 可瀏覽出版社、學期、課次、學習說明與影片段落。
2. 可在 Android TV 實機以 D-pad / OK / Back 操作所有主要流程。
3. MP4 可播放、暫停、續播、左右跳轉 5 秒。
4. YouTube 在通過 Phase 0 實機 Go/No-Go 驗證時才納入 MVP；若不通過，先以 MP4-only 發布。
5. 每支影片保存最後播放秒數、課程位置與內容 revision。
6. 首頁提供明確的「繼續上次學習」卡片及「回到上次進度」快捷入口。
7. 從快捷入口按 OK 後直接開啟正確影片並從上次秒數播放，不要求孩子重新找出版社／課次／段落。
8. 網路、影片下架、URL 失效、不可嵌入與資料同步失敗時，都有可理解的返回流程。

> **MVP 內容交付決策**：Phase 0A–2 以 APK 內建的 `assets/catalog/lessons.json` 作為 catalog baseline；啟動與首次使用不等待、也不依賴遠端 catalog 下載。課程文字與結構可離線瀏覽，但影片本體仍需透過 HTTPS 網路播放。遠端 catalog 更新屬 Phase 3，啟用後必須遵守 10.5 的驗證、atomic replace 與 last-known-good fallback。

### 1.2 第一階段不做

- TV PDF 閱讀器；第一階段只顯示有講義，第二階段再評估 TV 閱讀或 QR code。
- 影片縮圖自動預覽或自動播放。
- 影片下載、YouTube 串流 URL 解析、破解或離線保存 YouTube。
- 搜尋、留言、推薦影片、外部任意瀏覽。
- 需要孩子登入 Google 帳號的流程。
- 複雜的家長管理、每日提醒與多使用者帳號；保留後續擴充點即可。

---

## 2. 使用情境與核心流程

### 2.1 首次使用

```text
啟動 App
  → 載入內建 catalog
  → 顯示首頁
  → 初始焦點在第一個可用出版社／課程
  → 選出版社
  → 選課次
  → 閱讀學習提醒
  → 選影片段落
  → 進入全螢幕播放器
```

### 2.2 續看上次影片

```text
啟動 App
  → 首頁最上方顯示「繼續上次學習」
  → 卡片顯示出版社／課次／段落／上次秒數
  → OK
  → 直接開啟相同影片
  → 從 positionMs 繼續播放
```

快捷入口不得只回到課程詳情頁再讓孩子找一次影片；「繼續播放」的主要 action 必須直達播放器。課程詳情頁仍提供同一進度的次要入口，方便孩子先閱讀提醒。

### 2.3 正常播放與返回

1. 播放頁預設全螢幕。
2. OK 暫停／繼續；Left / Right 前後跳 5 秒。
3. 暫停時可完成紙本課本標注。
4. 按 Back：先 checkpoint，再停止播放器並回到原課程詳情；不顯示多餘確認對話框。
5. 下次從首頁快捷卡或課程影片卡重新進入時，沿用最後位置。

---

## 3. 資訊架構

```text
首頁
├── 繼續上次學習（最近一支影片的進度）
├── 課程版本
│   ├── 翰林版
│   ├── 康軒版
│   └── 南一版
└── 設定
    ├── 資料同步時間 / 手動重新整理
    ├── 影片播放提示
    └── 家長設定（後續）

版本／學期頁
└── 課程卡片清單
    ├── 第一課 夏夜
    ├── 第二課……
    ├── 段考複習講義與影片
    └── ……

課程詳情頁
├── 課名、版本、學期
├── 學習說明與紙本標注提醒
├── 影片段落清單
│   ├── 第一部分影片
│   ├── 第二部分影片
│   └── ……
├── 各段播放進度
└── 課後講義／PDF 狀態

播放頁
├── MP4 PlayerView 或 YouTube WebView
├── 原生播放控制 overlay
├── 課名、段落名稱與目前時間
└── 暫停時的學習提示
```

### 3.1 導覽原則

- 使用單一 Activity + Navigation Compose。
- route 使用穩定 `editionId`、`courseId`、`sectionId`、`videoId`；不可把列表 index 當永久識別值。
- `PlayerLaunch` 由 ViewModel 傳遞完整的穩定來源與起始秒數；不要把整個 JSON 或 URL 塞進 route。
- Back 永遠回上一層；播放器 Back 先存檔，再回課程詳情。
- 內容更新、排序或刪除課程後，原焦點使用 stable ID 恢復；找不到時才 fallback 到相鄰項目或第一個項目。

---

## 4. UI 設計

### 4.1 10-foot 設計原則

- 字大、按鈕大、高對比，適合坐在電視前閱讀。
- 所有可互動元件都可取得 D-pad focus，並有清楚焦點框。
- 一個畫面只呈現一個主要決策。
- 說明文字拆成短段落與卡片，不把完整 Notion 頁面原樣塞進 TV。
- 不自動播放影片預覽，避免孩子失去目前焦點。
- 播放進度與「繼續」入口永遠比影片縮圖優先。

### 4.2 初版視覺規格

| 項目 | 建議 |
| --- | --- |
| 設計基準 | 1920 × 1080，適應 4K |
| 安全邊界 | 左右 72dp、上下 48dp；依目標設備校正 overscan |
| 一般文字 | 24sp |
| 卡片標題 | 30sp |
| 頁面標題 | 40–48sp |
| 最小可聚焦高度 | 72dp |
| 焦點狀態 | 4dp 高亮描邊、輕微放大與陰影 |
| 配色 | 深藍灰背景、白字、單一亮色作焦點與進度色 |
| 動畫 | 短、可中斷；不得在焦點移動時造成位置跳動 |

不要用固定 px；dp / sp 必須依 Android TV density 與實機距離確認。

### 4.3 首頁與快速回到進度

首頁最上方放一張唯一主要的「繼續上次學習」卡片：

```text
┌─────────────────────────────────────────────────────────────┐
│ 國文影片課                           回到上次進度  設定 ⚙   │
│                                                             │
│ 繼續上次學習                                                │
│ ┌───────────────────────────────────────────────────────┐   │
│ │ 翰林版｜第一課 夏夜｜第二部分影片                 ▶   │   │
│ │ 上次看到 12:34 / 28:10                                │   │
│ │ ███████████████░░░░░░░░░░░░░░░░░░                     │   │
│ │ [ 繼續播放 ]                         [ 重新開始 ]     │   │
│ └───────────────────────────────────────────────────────┘   │
│                                                             │
│ 選擇教材版本                                                │
│ [ 翰林版 ]       [ 康軒版 ]       [ 南一版 ]                 │
└─────────────────────────────────────────────────────────────┘
```

規則：

- 有有效 resume record 時，首頁初始焦點放在「繼續播放」卡片；沒有時放在第一個出版社。
- 卡片顯示出版社、學期、課名、段落名、目前秒數／總長度、進度條與來源 icon（YouTube / MP4）。
- `繼續播放` 按 OK 直接進播放器並傳入 `startPositionMs`。
- `重新開始` 是同卡片內的次要 action，將起始點設為 0，但不刪除歷史紀錄，待真正播放後再寫入。
- 首頁 top bar 額外提供條件式的 `PlayCircle` 快捷按鈕：
  - 有活動播放器：`回到目前播放位置`。
  - 沒有活動播放器但有有效紀錄：`回到上次播放位置`。
  - 沒有紀錄：隱藏或 disabled，不顯示假的入口。
- 若上次影片已自然播完，卡片顯示 `已完成・重新觀看`，仍保留快速入口；不得默默把進度清掉。
- 若只有 position、尚不知道 duration，顯示 `上次看到 12:34`，不畫誤導性的百分比。
- 首次啟動不自動播放，孩子按 OK 後才開始。

#### 參考 `/mnt/ssd/mia_vocabulary` 的已驗證行為

此 App 不直接依賴 `mia_vocabulary` 的程式碼，但沿用它已驗證的互動概念：

- Home top bar 依 `activePlayback` 或 `lastSource` 顯示「回到目前播放位置」入口。
- Home 以穩定來源與目前 index／position 恢復快捷入口，而不是要求使用者重新瀏覽清單。
- 課程清單以 `lastTextbookId`、每本教材的最後課次及 `FocusRequester` 恢復展開狀態、scroll 位置與焦點。
- Player restore 只執行一次；後續 DataStore emission 不可覆蓋目前正在播放的 live state。

國文影片版的對應設計為：

```text
lastSource / currentPlayerIndex
  → lastResumeVideoId + VideoProgress.positionMs
lastTextbookId / lastTextbookLessons
  → lastEditionId + lastCourseId + lastSectionId + lastFocusedItemId
```

這些是行為參考，不複製單字播放器的 domain model；影片必須使用 `videoId` 與 `positionMs`。

### 4.4 版本／課程清單頁

- 垂直卡片清單，每張顯示課次、課名、影片數、文字說明數、附件數與完成／進行中狀態。
- 最近閱讀的課程可自動展開，但不得在沒有紀錄時硬套預設 index。
- 影片進度可用課程卡片右側摘要顯示，例如 `已看 1/2` 或 `進行中 12:34`。
- 按 Back 回首頁，保留上次焦點課程。
- 課程更新後，使用 stable `courseId` 找回焦點；若該課被移除，fallback 到同一出版社的相鄰課。

```text
翰林版　115 學年度上學期

▶ 第一課　夏夜                         2 部影片　進行中
  第二課　無心的過錯                     1 部影片
  第三課　母親的教誨                     1 部影片
  語文常識一　標點符號                   1 部影片
```

### 4.5 課程詳情頁

把 Notion 的 heading、說明文字、影片與附件依原始順序轉成學習段落。

```text
← 翰林版
第一課　夏夜

學習提醒
一個禮拜看一次上課影片。黑板上的筆記請抄在課本上，
理解後再記憶，並在下次上課前完成課後考卷。

影片與教材
┌─ 第一部分影片 ────────────────────────────────┐
│ 新詩／題解作者／第一段       進度 12:34/28:10 ▶ │
│ [ 繼續播放 ]                                  │
└────────────────────────────────────────────────┘
┌─ 第二部分影片 ────────────────────────────────┐
│ 第二段／形音義／課後練習題             尚未觀看 ▶ │
└────────────────────────────────────────────────┘

課後練習卷答案
題目卷已寄送紙本講義，不必列印；開啟詳解後直接對答案，
錯題請將詳解訂正在紙本題目卷。

[ 開啟講義 PDF ]（第二階段）
```

規則：

- `sub_header`、`header`、`text` 轉成段落標題或說明卡。
- `video` 轉成可播放卡片，顯示來源、duration（已知時）與 progress。
- 按影片卡的 `繼續播放` 直接從該影片紀錄的位置開始。
- `重新開始` 必須是明確的第二 action，不可因為點擊卡片而悄悄重設進度。
- `pdf` / `file` 第一階段顯示「有講義」及不可播放狀態；第二階段才加入閱讀器或 QR code。

### 4.6 播放頁

- 預設全螢幕，保持螢幕喚醒。
- 使用方向鍵、OK 或媒體鍵時顯示 native control overlay，約 4 秒後自動隱藏。
- 暫停時控制列不隱藏，顯示：`請完成課本標注後按 OK 繼續`。
- overlay 至少顯示課名、段落、來源、目前時間／總長度、進度條、`−5 秒`、播放／暫停、`+5 秒`。
- Back 不顯示確認框：checkpoint → pause/stop → release → 回課程詳情。

### 4.7 設定頁

- 顯示目前 catalog 的 `contentVersion`、最後成功同步時間與 `手動重新整理`。
- 顯示網路／影片畫質提示；第一階段只做提示，不讓孩子在播放中切換複雜畫質。
- 同步中顯示進度，完成或失敗都保留目前可用 catalog。
- 若加入家長設定，放在明確的家長入口；MVP 不要求孩子登入或輸入帳號。
- 設定頁的 Back 回首頁並恢復原焦點；同步不應清除 resume progress。

```text
┌─────────────────────────────────────────────────────────────┐
│ ← 第一課 夏夜／第一部分影片                                 │
│                                                             │
│                     [               ]                       │
│                     [    影片畫面    ]                       │
│                     [               ]                       │
│                                                             │
│  12:34 ━━━━━━━━━━━━━━━●━━━━━━━━━━━━ 28:10                   │
│       ↶ 5 秒       ❚❚ 暫停／播放       5 秒 ↷               │
│       請完成課本標注後按 OK 繼續                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 5. 遙控器與焦點規格

### 5.1 按鍵行為

| 按鍵 | 列表／詳情頁 | 播放中 | 暫停中 |
| --- | --- | --- | --- |
| D-pad 上下左右 | 移動焦點 | 上下顯示／移動控制列；左右立即 seek 5 秒 | 操作控制列 |
| OK / Center | 開啟／確認 | 暫停 | 繼續播放 |
| Left | 移動焦點 | 倒轉 5 秒 | 倒轉 5 秒 |
| Right | 移動焦點 | 快轉 5 秒 | 快轉 5 秒 |
| Back | 回上一頁並保留焦點 | checkpoint、停止、回課程詳情 | checkpoint、停止、回課程詳情 |
| Play/Pause | — | 暫停／播放 | 繼續／暫停 |
| Rewind/Fast Forward | — | 倒轉／快轉 5 秒 | 倒轉／快轉 5 秒 |
| Home | 依系統離開，先 checkpoint | 先 checkpoint；不依賴 onDestroy | 先 checkpoint |

### 5.2 必須明確實作的細節

- Left / Right 在播放頁由原生 `PlayerInputController` 優先攔截，不把焦點交給 YouTube WebView。
- 播放器 host 的 `PlayerInputController` 必須明確處理 `KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE`、`KeyEvent.KEYCODE_MEDIA_PLAY`、`KeyEvent.KEYCODE_MEDIA_PAUSE`、`KeyEvent.KEYCODE_MEDIA_REWIND`、`KeyEvent.KEYCODE_MEDIA_FAST_FORWARD`；`MediaSession` callback 可作為系統媒體鍵入口，但同一事件只能執行一次，避免 pause／resume 或 seek 被重複處理。
- 控制列隱藏時，方向鍵第一次按下仍要執行對應功能並顯示 overlay；不可只亮控制列而漏掉孩子想做的 seek。
- key-down / key-up 不得造成一次按鍵執行兩次；長按要有 debounce / repeat policy。
- 快速連按的 seek 以目前最新 position 為基準，禁止使用過時的 UI position。
- seek 時使用 `max(0, position - 5_000)` 與 duration clamp；duration 未知時允許暫存目標，ready 後再校正。
- seek 後顯示短暫 `<< 5 秒` / `5 秒 >>` feedback，不使用會遮住影片的長時間 Toast。
- 每個頁面至少有一個可見焦點；資料載入／錯誤／空狀態也必須有返回或重試按鈕。
- Lazy list 使用 stable key；不能用 item position 作刪除、rename 或 resume 的唯一依據。
- 選課程、選影片、按快捷入口前先保存 `lastFocusedItemId`。
- 課程播放到結尾後不自動播放下一課；第一版顯示 `播放完成`、`重新播放` 與 `回課程`。

### 5.3 焦點恢復

保存兩類位置，避免混淆：

1. **閱讀／播放位置**：哪一支影片、哪一秒。
2. **目錄焦點位置**：首頁目前展開哪個出版社／課程／影片卡。

播放器的 live index／position 不得被稍晚抵達的設定 Flow 覆蓋。進入 Player 時先取 `PlayerLaunch.startPositionMs`，若沒有明確 launch position 才查詢該影片的 persisted progress，而且只 restore 一次。

---

## 6. 播放進度、最後閱讀位置與續播設計

這一節是 MVP 的核心功能，不是附加功能。

### 6.1 資料分層

#### Per-video progress

每支影片都有一筆可更新的進度：

```kotlin
data class VideoProgress(
    val videoId: String,
    val revision: Int,
    val positionMs: Long,
    val durationMs: Long?,
    val status: ResumeStatus,
    val lastStartedAtMs: Long?,
    val lastCheckpointAtMs: Long,
    val completedAtMs: Long?
)

enum class ResumeStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    STALE
}
```

#### Last resume pointer

另外保存一個全域的最後閱讀指標，讓 Home 不必掃描所有課程：

```kotlin
data class LastResumeTarget(
    val videoId: String,
    val editionId: String,
    val courseId: String,
    val sectionId: String,
    val revision: Int,
    val lastFocusedItemId: String?,
    val updatedAtMs: Long
)
```

`LastResumeTarget` 只指向穩定 ID；實際秒數從 `VideoProgress` 查詢。若需要簡化 MVP，也可以在同一個 Room entity 同時保存 pointer 欄位，但 domain 上仍要區分「影片秒數」與「目錄焦點」。

### 6.2 保存時機

播放 position 在記憶體中即時更新，持久化採節流加立即 checkpoint：

- 每 5–10 秒最多寫一次。
- 播放開始、pause、seek 完成後立即寫入。
- 按 Back、切換影片、切換課程、`onPause`、`onStop`、App 進背景時立即寫入。
- WebView / Media3 發生 error 前先寫入 native layer 的最後快取值。
- 不依賴 `onDestroy()`；process 被殺掉前未必會收到它。
- 所有寫入透過單一 coroutine / `Mutex` / repository queue 序列化，避免舊 position 晚到後覆蓋新 position。
- 一次 checkpoint 的 progress 與 resume pointer 更新必須具有原子語意；MVP 建議同放在 Room transaction 中，pointer 不可在 progress 寫入失敗或 validation／revision check 失敗時先更新。

YouTube 特別規則：

- JavaScript 以固定 interval 回傳 current time；native 保留 `lastKnownPositionMs`。
- pause / Back 時先使用 last known position 完成同步保存；不要臨時呼叫 `getCurrentTime()` 後立刻銷毀 WebView，因為 JS callback 可能來不及返回。
- `snapshotPositionMs()` 是最佳努力的非同步 API；snapshot timeout 時使用最近一次 native cache。
- Back handler 必須執行有上限的 `checkpointAndClose()`：先 snapshot／寫入 Room，最多等待短 timeout，接著才停止、release 並 pop navigation；不可因等待 JS callback 而卡死，也不可在寫入前先切頁。

### 6.3 首次進入、續播與完成

- 使用者只開啟影片但尚未播放：可保存 `NOT_STARTED` 的最後開啟位置，但首頁文案為 `上次開啟`，不假裝已有觀看秒數。
- position 大於 0 且未完成：首頁顯示 `從 mm:ss 繼續`，按快捷入口直接從該秒數開始。
- duration 已知時，起始 position clamp 到 `0..duration`。
- 若 position 已接近結尾，提供 `從 mm:ss 繼續` 與 `重新開始`，不自動把紀錄歸零。
- 只有播放器收到自然 `ENDED` 事件才標記 `COMPLETED`；使用者 seek 到尾端或按 Back 不算完成。
- 完成後再次開啟預設顯示 `重新觀看`；若使用者仍選擇繼續，從最後位置／結尾前安全位置開始。
- 第一版不自動跳下一部，避免孩子尚未抄完筆記就被帶走。

### 6.4 Resume candidate resolver

建立 `ResumeTargetResolver`，每次首頁或快捷入口使用前驗證：

1. `videoId` 是否仍存在於目前 catalog。
2. `editionId`、`courseId`、`sectionId` 是否能解析到同一影片。
3. `revision` 是否與 catalog 相同。
4. `sourceType` / URL / YouTube video ID 是否仍可建立播放器。
5. 進度是否在合法範圍。

解析失敗時：

- 不 crash，不導向空白 Player。
- 顯示 `教材內容已更新，請重新選擇影片`。
- 保留紀錄供診斷，但標記 `STALE` 或清除該 pointer，不影響其他影片進度。
- fallback 到同一課程第一支可用影片，沒有則回課程列表。

### 6.5 課程列表與詳情的進度計算

- 每支影片顯示自己的 `position / duration`，duration 不明時只顯示已知 position。
- 課程摘要顯示 `已完成影片數 / 影片總數`；進行中的影片另外顯示 `進行中`。
- 不用單一課程百分比覆蓋各段影片的精確位置。
- 內容更新只要 `videoId + revision` 不變，URL 變更仍保留進度；若影片內容實際替換，revision 必須增加，進度重設或提示使用者。

### 6.6 與 `mia_vocabulary` 行為對應的驗收

以下行為必須在新 App 可重現：

- 進入 Player 後保存目前 item／video；按 Back 再回來不回到第一個。
- Home 有明確的播放 icon／Continue card 可直接回到最後位置。
- 目錄會恢復最後教材、課程與焦點，而不是只恢復一個不可靠的列表 index。
- 即使 DataStore / Room 更新 Flow 晚到，也不會把目前 live player position 改回舊值。
- App 重啟後，Home 顯示的秒數與上次 checkpoint 一致，允許有一個 checkpoint interval 的誤差。

---

## 7. 播放器架構

### 7.1 共同介面

WebView 控制是非同步的，因此不可使用只回傳同步秒數的介面：

```kotlin
interface LessonPlayer {
    val state: StateFlow<PlayerState>
    val positionMs: StateFlow<Long>
    val durationMs: StateFlow<Long?>
    val events: Flow<PlayerEvent>

    suspend fun load(item: VideoItem, startPositionMs: Long)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun seekBy(deltaMs: Long)
    suspend fun snapshotPositionMs(): Long
    fun release()
}

sealed interface PlayerState {
    data object Idle : PlayerState
    data object Loading : PlayerState
    data class Ready(val durationMs: Long?) : PlayerState
    data object Playing : PlayerState
    data object Paused : PlayerState
    data object Buffering : PlayerState
    data object Ended : PlayerState
    data class Error(val kind: PlayerErrorKind, val retryable: Boolean) : PlayerState
}

enum class PlayerEvent {
    Started,
    Paused,
    Seeked,
    Completed,
    BackRequested,
    SourceChanged
}
```

`PlayerScreen` 只依賴此介面及 `PlayerViewModel`，不直接判斷 MP4 或 YouTube。

### 7.2 Player factory 與 session

- `LessonPlayerFactory` 依 `sourceType` 建立 `Media3LessonPlayer` 或 `YouTubeWebViewLessonPlayer`。
- 一次只允許一個 active player。
- 換影片時：checkpoint → pause → release 舊 player → 建立新 player → load saved position。
- Player session 由 feature ViewModel 管理；Compose 只負責 attach / render，不在 recomposition 中重建播放器。
- `release()` 必須可重複呼叫；所有 callback 使用 generation / session ID 避免 stale callback 改動新 session。

### 7.3 MP4：Media3 / ExoPlayer

- 使用 `androidx.media3:media3-exoplayer`、`media3-ui` 與必要 datasource。
- 由 Media3 直接載入 HTTPS MP4，不在 App 播放前強制做 HTTP `HEAD`。
- 不少 CDN、NAS 或 signed URL 不支援 HEAD，且 HEAD 結果不一定等同 Range GET；預檢查失敗不可誤判影片不可播放。
- Media3 playback error 負責回報實際可播放性；匯入／發布工具另以 `GET Range: bytes=0-...` 做內容檢查。
- 驗證來源是否支援 byte-range、正確 `Content-Type`、HTTPS、足夠長的 signed URL TTL。
- 設定連線／讀取 timeout、buffering UI、可重試一次的網路錯誤與清楚的返回按鈕。
- `seekBy(5_000)` 使用 ExoPlayer current position 並 clamp；不要以 UI 進度當真實位置。
- `PlayerView` 放在 Compose `AndroidView`，控制列由 native overlay 統一繪製。

### 7.4 YouTube：WebView + IFrame Player API

- WebView 載入 App 自帶 HTML，使用 YouTube IFrame Player API。
- 不把 Google 帳號登入、Cookie 或 autoplay 當成必要前提；公開影片的 cookie／同意頁行為、硬體加速與播放手勢限制，必須在最低支援設備實測。MVP 由孩子按 OK 後才呼叫 `playVideo()`，不以關閉 WebView 的 user-gesture 限制作為 workaround。
- URL 使用標準化的 video ID；不在 App 中查 YouTube Data API，也不需要下載串流 URL。
- 建議使用 `WebViewAssetLoader` / `https://appassets.androidplatform.net/` 提供本地頁面，並在 iframe 設定合法 `origin`。
- Android 只透過窄介面的 JS bridge / `evaluateJavascript` 呼叫：
  - `player.playVideo()`
  - `player.pauseVideo()`
  - `player.seekTo(seconds, true)`
  - `player.getCurrentTime()`（非同步）
  - `player.getDuration()`（非同步）
- JavaScript 以 state callback 回報 ready、playing、paused、buffering、ended、error，另以 interval 回傳位置。
- 不依賴 WebView focus；原生 Activity / Compose player host 攔截遙控器。
- `enablejsapi=1`、`playsinline=1`；是否使用 `controls=0` 必須在 Phase 0 依 YouTube 政策與實機行為確認，不以「隱藏控制列」為無條件前提。
- WebView 設定只開啟播放所需能力；限制 navigation、file access、任意外部 URL 與 bridge 暴露的方法。
- YouTube 廣告、登入限制、影片下架、禁止嵌入、年齡／地區限制都可能使 MVP 失敗；不得嘗試繞過。

### 7.5 YouTube non-blocking progress

```text
JS timer / state callback
  → Native position cache
  → StateFlow positionMs
  → 每 5–10 秒 checkpoint Room

Back / pause / onStop
  → 先使用 native cache snapshot
  → 非同步寫入 progress
  → 停止並 release WebView
```

若 `getCurrentTime()` callback 尚未返回，不能讓 Back 永遠卡住；snapshot 設定短 timeout，超時使用最近快取。

---

## 8. YouTube 實機 Go/No-Go Gate

YouTube 是本案最大的技術與產品風險，必須提前在 Phase 0 完成最小 spike，而不是等完整 UI 完成後才發現不能用。

### 8.1 必測情境

- 在最低規格且實際要支援的 Android TV／機上盒上測試；記錄型號、Android TV 版本、Android System WebView／Chromium 版本、硬體加速狀態，以及 cookie／同意頁與 autoplay／user-gesture 行為。
- `onReady`、play、pause、seek、duration、ended、error callback。
- 使用遙控器 OK / Left / Right / Back，不讓焦點跑進網頁；另測試實體 Play/Pause、Rewind、Fast Forward，確認每個媒體鍵事件只執行一次。
- Back 時 position cache 是否能保存，WebView 銷毀後 Home 是否顯示正確秒數。
- App 進背景、回前景、螢幕待機喚醒、網路中斷與恢復。
- 廣告播放期間按鍵與 seek 的行為。
- public、private、unlisted、禁止嵌入、年齡限制、地區限制與已下架影片。
- 長影片、低網速、duration 尚未取得時的 progress UI。
- IFrame 控制列／branding 是否符合預期及可接受的使用條款。

### 8.2 Go/No-Go 決策

**Go** 條件：

- 目標設備可穩定載入可嵌入影片。
- OK、seek、pause、Back、progress checkpoint 在連續操作下都可用。
- 錯誤與限制情境能回到課程詳情，不會卡死 WebView。
- 產品接受 YouTube 的廣告與嵌入限制。

**No-Go** 條件：

- 目標設備 WebView 不穩定、seek / callback 不可靠。
- 目標內容大量禁止嵌入或必須登入。
- 控制列、廣告或條款與兒童課程需求不可接受。

No-Go 時：

1. MVP 先只發布 MP4。
2. 影片匯入報告把 YouTube 標成 `blocked`；MP4-only release policy 不建立可啟動的 YouTube 卡片，若為保留課程結構而顯示，必須是明確的不可用狀態，不讓孩子看到無限 loading 或半成品入口。
3. 是否開啟官方 YouTube App 作為備援，列為產品決策；不可當作預設且不可依賴 App 內進度保存。
4. 重新評估把內容搬到自己控制的 MP4/CDN。

---

## 9. Android lifecycle、MediaSession 與播放資源

- 播放中使用 `FLAG_KEEP_SCREEN_ON`；離開 Player 清除。
- window focus 暫時失去時 pause 並保存；重新取得 focus 不自動播放，除非產品明確決定且原本狀態為 playing。
- `onPause` / `onStop` 先 checkpoint；MVP 不在 Home 或其他頁面繼續播放影片。
- Home、Back 或 route change 會停止並 release WebView / ExoPlayer，避免兩個播放器同時佔用解碼器或網路。
- 使用 AndroidX Media3 `MediaSession` 或等價 native media session 處理 Play/Pause、Rewind、Fast Forward media key。
- Media session 的狀態要與 `LessonPlayer.state` 同步；不可只更新 UI icon。
- audio focus 被其他 App 搶走時 pause，顯示短提示；focus 恢復不應跳到舊 position。
- 網路短斷：顯示 buffering / retry；超過 timeout 顯示可理解錯誤與 `重新嘗試`、`返回課程`。
- process 被系統殺掉時，依靠 checkpoint 及 Room 恢復，不依賴 Activity instance 或 `onDestroy`。
- Android TV 上的 WebView／ExoPlayer 銷毀與重建要受 lifecycle owner 管理；Compose recomposition 不得造成反覆 load。

---

## 10. 課程資料與 Notion 匯入

### 10.1 不讓 TV App 直接依賴 Notion

Notion 是內容來源，不應成為 App 執行時唯一資料庫：

```text
Notion 公開頁 / Notion API
  → 匯入腳本
  → block normalization + 人工校對
  → lessons.json + manifest
  → App 內建 baseline 或 HTTPS 靜態內容
  → App 本機 catalog cache
```

優點：

- Notion API／block 結構改變不會直接使 TV 端無法上課。
- 可在電腦先校對影片順序、段落與失效連結。
- App 離線時仍能瀏覽最近一次有效的課程目錄與文字。
- 影片來源可以從 Notion temporary URL 搬到自己控制的 HTTPS CDN / NAS。

若使用 Notion file block 的下載 URL，不可直接當長期 App URL；匯入流程應下載／搬移到有穩定 TTL 與權限管理的儲存空間。

### 10.2 資料模型

```json
{
  "schemaVersion": 2,
  "contentVersion": "2026-09-01",
  "updatedAt": "2026-09-01T12:00:00Z",
  "editions": [
    {
      "id": "hanlin-g7-115-fall",
      "name": "翰林版",
      "grade": "七年級",
      "semester": "115 學年度上學期",
      "courses": [
        {
          "id": "hanlin-g7-115-fall-lesson-01",
          "title": "第一課　夏夜",
          "instructions": [
            "黑板上的所有筆記都要抄在課本上。",
            "理解後再記憶，並在下次上課前完成課後考卷。"
          ],
          "sections": [
            {
              "id": "hanlin-g7-115-fall-lesson-01-part-01",
              "order": 1,
              "type": "video",
              "title": "第一部分影片",
              "description": "新詩／題解作者／第一段",
              "video": {
                "id": "hanlin-g7-115-fall-lesson-01-part-01-video",
                "revision": 1,
                "sourceType": "youtube",
                "videoId": "XPvJgDZ06A8",
                "url": "https://www.youtube.com/watch?v=XPvJgDZ06A8",
                "durationMs": null
              }
            },
            {
              "id": "hanlin-g7-115-fall-lesson-01-part-02",
              "order": 2,
              "type": "video",
              "title": "第二部分影片",
              "description": "第二段／形音義／課後練習題",
              "video": {
                "id": "hanlin-g7-115-fall-lesson-01-part-02-video",
                "revision": 1,
                "sourceType": "mp4",
                "url": "https://cdn.example.com/hanlin/lesson-01-part-02.mp4",
                "durationMs": null
              }
            },
            {
              "id": "hanlin-g7-115-fall-lesson-01-answer",
              "order": 3,
              "type": "note",
              "title": "課後練習卷答案",
              "description": "題目卷已經寄送紙本講義，不必列印。"
            }
          ]
        }
      ]
    }
  ]
}
```

#### Catalog 與本機進度的界線

- `lessons.json` 只存內容，不存 `positionMs`、`status`、`lastCheckpointAtMs` 等使用者進度；進度一律寫入 Room。
- `video.id` 對應 `video_progress.videoId`，`video.revision` 對應所有本機 progress／resume pointer 的 `revision` 欄位。
- `contentVersion` 是整份 catalog 的更新版本；`video.revision` 是單支影片的進度相容版本，兩者不可互相替代。
- `last_resume_pointer` 只保存可解析的穩定路徑；首頁顯示的秒數必須 join／查詢對應的 `video_progress`。找不到 progress 時顯示未觀看或上次開啟，不可顯示虛假的秒數。
- catalog 的 `updatedAt` 使用 ISO-8601 UTC；本機 `positionMs`、`durationMs` 與時間戳統一使用毫秒。

### 10.3 穩定 ID 與 revision 規則

- `edition.id`、`course.id`、`section.id`、`video.id` 必須全域穩定且唯一。
- resume key 使用 `video.id + revision`，不使用 raw URL、不使用陣列 index。
- URL 變更但影片內容相同：保留 `id` 與 `revision`，進度延續。
- 影片內容被替換：revision +1，進度重設或顯示 `教材已更新，請重新開始`。
- 只重新排序 section：不影響任何 progress。
- 保留 Notion 原始 block ID 及 source URL 供匯入除錯，但不得把 Notion block ID 當成唯一 runtime route，除非其穩定性已確認。
- 缺少 video id、重複 id、section 參照不完整時，匯入失敗並列入報告，不產生可發布 JSON。

### 10.4 匯入與校對規則

- YouTube URL 標準化為 video ID，處理 `watch`、`embed`、`youtu.be`、query string 與重複來源。
- MP4 保留 HTTPS URL，但建議另有 `managedAssetId`；不要把會過期的 Notion signed URL 寫入發布檔。
- 每次同步輸出：總出版社、總課程、總 section、YouTube 數、MP4 數、PDF／file 數、重複 ID、重複影片、失效連結、禁止嵌入清單。
- `header`、`sub_header`、`text`、`video`、`pdf`、`file` 依原始順序轉成 section。
- 先為翰林／康軒／南一產生獨立 JSON，再合併發布，方便人工校對。
- 匯入工具要有 `--dry-run` 與 deterministic output，讓相同輸入不產生無意義 diff。
- 本 repository 的 `tools/notion_import/import_public_catalog.py` 已可讀取公開頁、排除 `page_sort` 中無內容的刪除列，並產生目前 snapshot：翰林 18 課／49 部影片、康軒 19 課／60 部影片、南一 18 課／72 部影片；文字說明與附件另行計數。

### 10.5 遠端 catalog 更新安全機制

遠端更新不可直接覆蓋目前可用資料：

1. HTTPS 下載到暫存檔。
2. 檢查 content type、大小上限、schemaVersion、必填欄位、stable ID 唯一性與所有引用。
3. 驗證 SHA-256 checksum；正式環境可再加 manifest signature。
4. 使用 `ETag` / `If-None-Match` 減少重複下載。
5. 完整驗證成功後才 atomic replace。
6. 保留 last-known-good catalog；新資料損壞、schema 太新或同步中斷時繼續使用舊資料。
7. `minimumAppVersion` 不符合時不套用，顯示可理解的更新提示。
8. 同步成功後才更新 `contentVersion` 與最後同步時間；失敗不可清除現有資料或 progress。
9. Catalog 與 progress 的 transaction 必須分開，內容同步不可誤刪觀看紀錄。

---

## 11. 本機資料、Room 與 repository

### 11.1 Catalog cache

- Phase 0A–2 的 MVP 只把內建 `assets/catalog/lessons.json` 當作啟動 baseline；首次啟動不要求遠端下載，也不因無網路而阻塞首頁。
- Room / file cache 保存最後一次完整且有效的 catalog metadata；影片本體仍需網路。
- 文字課程在無網路時仍可瀏覽。
- Phase 3 若啟用遠端 catalog，下載、驗證與替換遵守 10.5，失敗時繼續使用內建或 last-known-good data。
- repository 對 UI 暴露 immutable model 與 `Flow`，不讓 Compose 直接讀 JSON 檔。

### 11.2 Room schema

建議至少包含：

```text
catalog_metadata(
  id = 1,
  schemaVersion,
  contentVersion,
  updatedAt,
  downloadedAt,
  checksum,
  lastKnownGood = true
)

video_progress(
  videoId,
  revision,
  editionId,
  courseId,
  sectionId,
  positionMs,
  durationMs NULL,
  status,
  lastStartedAtMs NULL,
  lastCheckpointAtMs,
  completedAtMs NULL,
  PRIMARY KEY(videoId, revision)
)

last_resume_pointer(
  id = 1 PRIMARY KEY,
  videoId,
  editionId,
  courseId,
  sectionId,
  revision,
  lastFocusedItemId NULL,
  updatedAtMs
)

last_catalog_location(
  id = 1 PRIMARY KEY,
  editionId NULL,
  courseId NULL,
  sectionId NULL,
  focusedItemId NULL,
  updatedAtMs
)
```

若以 DataStore 保存 pointer，也必須保留 stable ID、revision 與時間戳；Room 適合多支影片進度查詢，DataStore 適合少量設定。不要使用 static mutable singleton 保存 progress。

### 11.3 Domain model 補充

```kotlin
data class VideoItem(
    val id: String,
    val revision: Int,
    val source: VideoSource,
    val title: String,
    val description: String?,
    val durationMs: Long?
)

sealed interface VideoSource {
    data class Mp4(val url: String, val managedAssetId: String? = null) : VideoSource
    data class YouTube(val videoId: String) : VideoSource
}
```

`VideoItem.id` 是內容穩定 ID；`VideoSource` 只描述目前播放方式。Progress 的唯一識別是 `(videoId, revision)`，不可直接以 URL 或列表 index 作 key。

### 11.4 repository API

```kotlin
interface LessonRepository {
    fun observeCatalog(): Flow<Catalog>
    fun observeLastResume(): Flow<ResumeCardModel?>
    suspend fun progress(videoId: String, revision: Int): VideoProgress?
    suspend fun saveCheckpoint(checkpoint: VideoCheckpoint)
    suspend fun markCompleted(videoId: String, revision: Int, durationMs: Long?)
    suspend fun saveCatalogLocation(location: CatalogLocation)
    suspend fun resolveResumeTarget(): ResumeTarget?
}
```

`saveCheckpoint` 必須做 validation、clamp 與 revision check；不能讓不合法 position 寫入資料庫。

---

## 12. 例外處理與兒童使用安全

- 無網路：顯示「目前無法播放影片，請檢查網路」，仍可閱讀課程說明與既有進度。
- YouTube 播放錯誤：指出可能為下架、私人、禁止嵌入、年齡／地區限制，不顯示複雜技術訊息。
- MP4 錯誤：顯示「教材影片連結失效」，提供重試與回課程，記錄 URL / managedAssetId 供管理者修正。
- 不顯示 YouTube 建議影片、搜尋、留言與任意外部導覽。
- WebView 僅允許 App 自帶頁面及必要的 `youtube.com` / `youtube-nocookie.com` 來源；阻擋任意外連與未驗證 bridge 呼叫。
- App 不下載、破解或解析 YouTube 串流網址。
- 播放錯誤不可刪除最後成功保存的 progress；孩子仍可從同一秒數重試。
- 所有錯誤畫面至少有一個可聚焦的 `重新嘗試` 或 `返回課程`。
- 快取／同步錯誤不可把課程列表清成空白；使用 last-known-good data。

---

## 13. Android 技術選型與模組

| 類別 | 選擇 |
| --- | --- |
| 語言 | Kotlin |
| minSdk | Android TV 9 / API 28 起；依目標設備 spike 調整 |
| compile / target | 專案建立時採當期穩定 SDK |
| UI | Jetpack Compose；優先使用穩定的 Compose for TV 元件，必要時自訂 focusable Compose 元件 |
| 導覽 | Navigation Compose |
| MP4 | AndroidX Media3 / ExoPlayer |
| YouTube | Android WebView + YouTube IFrame Player API，Go/No-Go gate |
| JSON | Kotlin Serialization 或 Moshi，二選一並全專案統一 |
| 網路 | OkHttp；同步器可加 Retrofit，但不為簡單 catalog 過度抽象 |
| 本機資料 | Room；少量偏好使用 DataStore |
| 非同步 | Kotlin Coroutines / Flow |
| 圖片 | Coil（只有確實需要課程封面時加入） |
| 測試 | JUnit、coroutines-test、Room test、Compose UI test、目標 TV 實機操作 |

### 13.1 建議模組

Repository 初始可以先用單一 `app` module，功能穩定後再拆分；不要在空 repository 階段為了形式過早建立多 module。目標結構：

```text
app/                    Android TV entry、DI、navigation、manifest
core-model/             Edition、Course、Section、VideoItem、Progress model
core-data/              JSON sync、Room、repository、resume resolver
feature-home/           首頁、Continue card、版本／課程列表
feature-course/         課程詳情、學習提醒、影片段落
feature-player/         LessonPlayer、Media3、YouTube WebView、controls
```

若先維持單 module，package 仍依上述邊界分組。

### 13.2 建議目錄

```text
ANDROID_TV_LEARNING_APP_PLAN.md
README.md
.gitignore
settings.gradle.kts
build.gradle.kts
app/
  src/main/
    AndroidManifest.xml
    assets/catalog/lessons.json
    assets/player/youtube_player.html
    java/.../
      App.kt
      MainActivity.kt
      di/AppContainer.kt
      data/
        model/
        json/
        db/
        repository/
        sync/
      navigation/
      feature/home/
      feature/course/
      feature/player/
      playback/
        LessonPlayer.kt
        Media3LessonPlayer.kt
        YouTubeWebViewLessonPlayer.kt
        PlayerInputController.kt
        PlaybackProgressStore.kt
      ui/theme/
  src/test/
  src/androidTest/
tools/
  notion_import/
  validate_catalog/
```

---

## 14. Manifest、WebView 與權限

新版 manifest 最小化：

- Launcher `MainActivity`, `exported=true`。
- `INTERNET`、必要的 network state；影片與 catalog 都是 HTTPS。
- `PlaybackService` 只有在確定需要背景媒體控制時才加入；若加入則 `exported=false`、`foregroundServiceType=mediaPlayback`。
- 不宣告 location、storage、overlay 或任意 package query。
- WebView 不開啟 universal access from file URLs；採 `WebViewAssetLoader` / HTTPS app origin。
- 若 YouTube 通過 Go gate，Application／Activity 不得關閉 hardware acceleration；並在最低支援設備確認 WebView 影片渲染與解碼表現。
- JavaScript bridge 僅註冊在自帶 player page，方法最小化；收到 message 時驗證事件格式與 session ID。
- NavigationClient 只允許 player page、必要 YouTube domain；外部連結顯示阻擋或交由明確的家長 action。
- `allowBackup` 與 backup rules 需明確決定；不要備份 secret 或 signed URL。
- 播放進度是本機資料，不含內容型 telemetry。

---

## 15. 開發里程碑

### Phase 0A：Repository bootstrap 與內容盤點（1–2 天）

- [x] 建立 Kotlin Android TV project skeleton 於 `/mnt/ssd/mia_chinese`。
- [x] 加入 Gradle wrapper、`.gitignore`、README 與本 plan。
- [x] 確認 remote、branch 與 SSH 操作可用；不把 key 加入 repo。
- [x] 解析 Notion 三個版本的課程、heading、text、video、MP4、YouTube、PDF/file。
- [x] 產生 schema v2 的 `lessons.json`。
- [x] 建立 stable ID、revision、duplicate／invalid reference 報告。
- [ ] 人工校對至少一個出版社的完整課程與影片順序。

**驗收**：能列出總課程、影片來源、失效連結與待確認項目；JSON 可通過 validator。

### Phase 0B：播放器 spike 與 Go/No-Go（1–2 天）

- [x] 以一支可用 MP4 建立最小 Media3 player。
- [x] 以一支 public、可嵌入 YouTube 建立最小 WebView IFrame player。
- [ ] 先選定最低規格的實際支援設備，記錄 Android TV／WebView 版本、硬體加速、cookie／同意頁與 autoplay／user-gesture 結果。（目前只有 API 28 Android TV emulator，尚未定義正式支援機型。）
- [x] 實作 OK、pause、Left/Right 5 秒、Back、position callback。
- [x] 實作並以 `PlayerInputController`／MediaSession 路徑驗證 D-pad 與 Play/Pause、Rewind、Fast Forward media keys；每個按鍵事件不得重複執行。
- [x] 實作 native position cache 與 Back checkpoint。
- [ ] 測試 app 背景／回前景、WebView 銷毀／重建、網路中斷。（程式已加入 lifecycle／destroy handling，但正式設備矩陣尚未完成。）
- [x] 測試至少一支受 YouTube 播放限制的錯誤畫面。
- [x] 將上述設備與測試結果整理成 Go/No-Go 測試矩陣（見 `YOUTUBE_GO_NO_GO.md`）。
- [x] 做出 YouTube Go/No-Go；目前為 No-Go，release 鎖定 MP4-only MVP。

**驗收**：不依賴完整 UI，即可證明兩種來源的核心操作與續播是否可靠。

### Phase 1：MP4-first App shell 與內容瀏覽（約 1 週）

- [x] Splash、Home、版本／課程列表、課程詳情、段落卡片。
- [x] 內建 `assets/catalog/lessons.json` 載入、空狀態、loading、error、retry；MVP 不要求首次啟動遠端同步。
- [x] Compose TV focus、D-pad 導覽、Back stack。
- [x] Media3 MP4 player、控制 overlay、pause／seek／Back。
- [x] course / section / video stable ID route。

**驗收**：遙控器可從首頁選到任一有效 MP4 並返回原課程，焦點不消失。

### Phase 1.5：YouTube 整合（約 3–5 天；僅 Go 時執行）

- [ ] `YouTubeWebViewLessonPlayer` 遵守 player interface。（目前保留為 debug spike screen；正式整合受 No-Go gate 阻擋。）
- [x] JS state callback、native position cache、error mapping。
- [x] WebView allowlist、bridge session guard、外部導覽阻擋。
- [x] YouTube source 與 MP4 共用原生 overlay／遙控器。
- [x] 若實機行為退化，立即回到 MP4-only，不阻塞其他功能。

**驗收**：同一課中 MP4／YouTube 卡片都能以相同按鍵操作；限制影片有清楚錯誤回課程。

### Phase 2：Resume、快速入口與內容持久化（約 3–5 天）

- [x] `video_progress`、`last_resume_pointer`、`last_catalog_location` schema。
- [x] 每 5–10 秒 checkpoint、pause／Back／onStop 立即保存。
- [x] Home Continue card、top bar resume icon、課程詳情 resume button。
- [x] 起始 position clamp、revision validation、stale record handling。
- [x] 目錄展開／焦點／scroll 恢復。
- [x] completed／restart／in-progress 文案與行為。
- [x] 啟動、process recreation 後可直接回最後影片秒數。

**驗收**：播放到 12:34 → Back → 重啟 → Home 顯示同一課同一段的 `12:34` → OK 直接從該位置開始。

### Phase 3：內容維護與 release hardening（選做／後續）

- [x] HTTPS catalog 更新、ETag、checksum、atomic replace；正式 signature manifest 仍待 CDN／部署環境決定。
- [x] last-known-good catalog 與 schema／app version gate。
- [ ] 管理者同步狀態、失效影片清單與手動重新整理。（已有 sync status 與手動同步入口；失效影片清單尚未做。）
- [ ] PDF 閱讀或 QR code。
- [ ] 家長 PIN、每日學習提醒、手機 companion control。
- [ ] 若 YouTube 不穩定，逐步把影片搬到自有 MP4/CDN。

每個 Phase 都要獨立 commit；先讓最小流程 build，再擴充 UI，不能等所有畫面完成才第一次在實機執行。

---

## 16. 測試與 MVP 驗收清單

### 16.1 Domain / data tests

- [x] schema version、必填欄位、stable ID uniqueness。
- [x] YouTube URL normalization：watch、embed、youtu.be。
- [x] MP4 不以 HEAD 結果誤判；Range validator 行為正確。
- [x] course／section／video 引用缺失時安全報錯，不 crash。
- [x] URL 變更同 revision 保留 progress；revision 變更由 stable video ID 保留，revision 變更正確標 stale／重設。
- [x] position clamp、duration 未知、自然 ended 與 seek-to-end 的完成規則。
- [x] checkpoint throttle 與立即 checkpoint 不會以舊值覆蓋新值。
- [x] resume resolver 找不到來源時提供 fallback，不導向空白頁。

### 16.2 Compose / UI tests

- [x] 首次啟動無 resume 時焦點在第一個出版社。
- [x] 有 resume 時 Home 顯示 Continue card，焦點在繼續播放。
- [x] 按 Home Continue card 直接進對應影片，而非只到課程列表。
- [x] 課程詳情顯示正確影片順序、提醒、progress 與 resume action。
- [x] MP4 player OK pause／resume、Left／Right 5 秒、Back 回原課程。
- [x] MP4 player 實體 Play/Pause、Rewind、Fast Forward 與 D-pad 行為一致，且不重複處理事件。
- [x] YouTube player（若 Go）同樣的 native controls 與 Back 行為；目前以 debug spike 驗證，因 No-Go 不納入 release。
- [x] 播放 → pause → 回首頁／Back → progress 正確保存。
- [x] process recreation 後仍能顯示最近影片與秒數。
- [x] completed 顯示重新觀看，不會無提示重設或自動下一部。
- [x] 失效 MP4、禁止嵌入 YouTube、無網路都有重試／返回按鈕。
- [x] 內容更新後 stable ID 可恢復焦點；刪除項目有合理 fallback。
- [x] 所有主要頁面 D-pad 焦點永不消失，錯誤／空狀態有重試或返回 action。

### 16.3 實機驗收重點

- [ ] 目標 Android TV／機上盒上確認 1080p、4K 顯示與安全邊界。（尚未選定正式目標設備。）
- [ ] 真實遙控器測試短按、長按、連按、媒體鍵與 Back。（目前完成 emulator keyevent；真實遙控器待做。）
- [ ] WebView／Media3 佔用、buffering、pause、release 沒有黑畫面或卡死。（YouTube 在 API 28 WebView 已觀察到黑畫面，故維持 No-Go。）
- [ ] 影片播放中切到 Home／待機／回前景後 checkpoint 正確。（程式路徑已覆蓋，目標設備實測待做。）
- [ ] 小朋友不需要成人協助即可完成「選課 → 播放 → 暫停抄筆記 → 返回 → 繼續」。（需正式目標設備與實際使用者驗收。）

### 16.4 MVP acceptance

- [x] Repo 使用 `git@github.com:victorchentw/mia_chinese.git`，沒有敏感 key 進入 Git。
- [ ] Notion 匯入資料經人工校對，課程與影片順序正確。（目前完成 validator／數量盤點；完整人工校對待做。）
- [x] Home 有快速回到上一次閱讀／播放進度的明確 UI。
- [x] 無網路首次啟動仍可載入內建 catalog；遠端同步失敗不清除 last-known-good catalog 或 progress。
- [x] 最後影片、最後課程、最後 section、最後秒數均以 stable ID 保存。
- [x] MP4 可播放、暫停、前後 5 秒、Back 保存。
- [x] YouTube 通過 Go gate 時可使用；目前未通過，MVP 明確為 MP4-only，release 不提供 YouTube 播放入口。
- [x] 遠端 catalog 失敗不破壞 last-known-good data 與既有進度。
- [x] 無網路、失效 URL、YouTube 限制與資料損壞都有易懂錯誤 UI。
- [x] `./gradlew test lint assembleDebug` 通過，並完成 API 28 Android TV emulator MP4 smoke test；正式目標 TV smoke test 待選定設備。

---

## 17. 開發前仍需確認的產品事項

1. 目標電視／機上盒型號、Android TV 版本、WebView 版本與是否有 Google Play Services。
2. 三個出版社是否同時上線，或先用一個版本驗證內容與播放器。
3. Notion MP4 是否為不需登入、可長期存取的 HTTPS；若否，搬到 NAS／CDN。
4. YouTube 影片是否允許嵌入，以及廣告與官方控制列是否可接受。
5. YouTube No-Go 時是否完全 MP4-only，或允許家長明確選擇官方 YouTube App 備援。
6. PDF 是第二階段 TV 閱讀，還是只顯示「已提供紙本講義」。
7. App 是自家 TV sideload APK，還是未來發布到 Google Play。
8. 影片播放到結尾後是否永遠停留在完成畫面；MVP 預設不自動播放下一部。

---

## 18. 主要風險與決策記錄

| 風險 | 影響 | 決策／緩解 |
| --- | --- | --- |
| YouTube WebView callback 或 seek 不穩 | 續播與遙控器體驗失敗 | Phase 0 spike；No-Go 即 MP4-only |
| YouTube 禁止嵌入／廣告／年齡限制 | 個別內容不可播放 | 匯入報告標記；易懂錯誤；不繞過限制 |
| Notion signed URL 過期 | MP4 播放失效 | 匯入搬到自有 HTTPS 儲存；不直接依賴 Notion URL |
| MP4 CDN 不支援 HEAD／Range | 預檢查誤判、seek 差 | 不強制 HEAD；Media3 直接載入；Range GET 驗證 |
| 內容更新改變列表 index | 進度／焦點錯配 | 全面使用 stable ID + revision |
| YouTube `getCurrentTime()` callback 太晚 | Back 時丟失最後秒數 | native position cache + timeout + immediate checkpoint |
| Compose Flow 晚到覆蓋 live player | 畫面與實際播放不同步 | player session generation、一次性 restore guard |
| Android process 被殺 | 進度遺失 | 5–10 秒節流 checkpoint + lifecycle checkpoint，不依賴 onDestroy |
| 多播放器同時存在 | 解碼器、音訊與 WebView 資源衝突 | single active player，換來源先 release |
| 網路同步資料損壞 | 課程列表消失 | validate、checksum、atomic replace、last-known-good |

**目前的產品優先順序：**

```text
可靠的內容與遙控器操作
  > 正確保存最後影片／秒數
  > 首頁一鍵續播
  > MP4 穩定播放
  > YouTube 擴充
  > PDF、家長功能與其他 polish
```
