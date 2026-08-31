# Mia Chinese TV

Android TV 國文影片課學習 App，版本 `v0.1.15`。

## Pi agent mandatory workflow

以下是本 repository 的強制流程，不是建議。任何造成 repository 檔案變更的工作（包含文件修改）都必須完成一次版本遞增、驗證、簽章、commit 與 push：

1. 修改前確認目前 branch 與工作目錄狀態。
2. 在 `app/build.gradle.kts` 同時遞增：
   - `versionName` 的第三碼（patch）：`0.1.0` → `0.1.1`。
   - `versionCode` 整數：`1` → `2`。
   同一個 task 的多個檔案只遞增一次；不得降低或重用版本。
3. 完成修改後執行 `./gradlew test lint assembleDebug`。
4. 使用下方 wiki signing key 執行 `assembleRelease`，並用 `apksigner verify` 驗證 APK。
5. 確認沒有 private key、password、`local.properties` 或 signing secret 進入 Git。
6. `git diff --check`，建立清楚的 commit。
7. 使用 wiki 指定的 SSH key push 到 `origin main`；push 失敗不能假稱完成。

若只需要檢查、不應產生 repository 修改，必須明確說明，不要偷偷修改版本。

## Current v0.1.15 scope

- Android TV / Google TV，min SDK 28
- TV launcher icon：沿用 Mia bird 視覺，文字改為「米亞的國文」
- 540dp 高度 Android TV 使用較緊湊的文字、間距與播放控制列
- 內建 `app/src/main/assets/catalog/lessons.json` catalog baseline（Notion 115 上學期國七公開頁快照）
- D-pad / OK / Back 導覽，並使用高對比焦點／文字色彩
- 出版社、課程、影片段落、文字說明與附件瀏覽
- MP4-first Media3 播放器
- Room 保存每支影片播放進度與首頁繼續播放指標
- YouTube IFrame 播放器預設先使用 Android TV 系統 WebView；失敗時可明確交給 SmartTube／其他外部播放器。No-Go 仍代表不保證所有 TV，相容性差時可編成 MP4-only

目前 asset catalog 已替換為 Notion 公開課程資料；有效課程數為翰林 18、康軒 19、南一 18。課程卡片會分別顯示影片、文字說明與附件數量。可用下列工具重新產生 catalog：

```bash
python3 tools/notion_import/import_public_catalog.py \
  --output app/src/main/assets/catalog/lessons.json
```

Notion 上傳影片／附件仍需後續搬移至穩定 CDN；YouTube 內嵌播放仍受 Phase 0B Go/No-Go 限制，但目前 build 會先嘗試 WebView，失敗時可交給 SmartTube／系統外部播放器。PDF 附件可在 TV 嘗試 WebView，並提供手機 QR code；目前 Android TV WebView 不保證原生 PDF render。Phase 0B 測試結果記錄於 [`YOUTUBE_GO_NO_GO.md`](YOUTUBE_GO_NO_GO.md)。

驗證已產生的 catalog（不會對媒體發出網路請求）：

```bash
python3 tools/validate_catalog/validate_catalog.py \
  app/src/main/assets/catalog/lessons.json --json
python3 -m unittest tools.validate_catalog.test_validator
```

App 端的 `CatalogRepository.sync()` 只接受 HTTPS、可信 SHA-256、合法 schema，
並以 ETag、暫存檔與 atomic replace 保留 last-known-good catalog；MVP 啟動仍只依賴
APK／本機 baseline。若要在受控環境啟用同步，請由 CI／本機 secret-free 設定注入 endpoint
與 checksum（不把 signed URL 或 secret 寫進 repo）：

```bash
MIA_CATALOG_ENDPOINT='https://cdn.example.invalid/catalog/lessons.json' \
MIA_CATALOG_SHA256='<64 位 hex checksum>' \
./gradlew assembleRelease
```

未提供兩者時，設定頁的手動同步會清楚顯示尚未設定，且不影響離線 baseline。

## Build

Unsigned/debug verification build:

```bash
./gradlew test lint assembleDebug
```

使用 wiki 指定的既有 signing key 產生 release APK（密碼只從本機 secret／環境變數提供，不寫入 repo）：

```bash
MIA_SIGNING_STORE=/mnt/ssd/vvdoc/key/victor.keystore.jks \
MIA_SIGNING_ALIAS=victor \
MIA_SIGNING_PASSWORD='<從 wiki 取得>' \
./gradlew assembleRelease
```

若要產生保守的 MP4-only release（關閉 WebView），可使用受控的 build property：

```bash
./gradlew assembleRelease -PmiaEnableYoutubeWebView=false
```

預設 build 會先走系統 WebView；若播放失敗，可按外部播放器。若未安裝 SmartTube，fallback 會再嘗試系統 YouTube／瀏覽器；外部播放器無法回報即時播放秒數。

APK 會產生於 `app/build/outputs/apk/debug/app-debug.apk` 或
`app/build/outputs/apk/release/app-release.apk`。
