# Mia Chinese TV

Android TV 國文影片課學習 App，版本 `v0.1.0`。

## v0.1.0 scope

- Android TV / Google TV，min SDK 28
- 內建 `app/src/main/assets/catalog/lessons.json` catalog baseline
- D-pad / OK / Back 導覽
- 出版社、課程與影片段落瀏覽
- MP4-first Media3 播放器
- Room 保存每支影片播放進度與首頁繼續播放指標
- YouTube 內容先顯示未啟用狀態，待 Phase 0B Go/No-Go 後再整合

目前 asset catalog 是可播放的 demo 資料；Notion 匯入工具尚未加入。正式內容應由 schema v2 匯入結果取代該檔案。

## Build

```bash
./gradlew test lint assembleDebug
```

APK 會產生於 `app/build/outputs/apk/debug/app-debug.apk`。
