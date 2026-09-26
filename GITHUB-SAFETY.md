# GitHub 上傳前檢查

建議只把 `outputs/fitlog` 當成專案根目錄。已在此目錄初始化 Git，僅將原始碼、測試、文件與必要設定加入暫存區。上傳前仍需確認遠端儲存庫與暫存內容。

## 不能上傳的真實資料

| 資料 | 本專案位置或範例 | 原因 |
| --- | --- | --- |
| 會員、密碼雜湊、訓練資料 | `backend/data/fitlog.mv.db`、`*.trace.db` | 包含電子郵件、會員個資與私人紀錄；雜湊不是公開資料 |
| 資料庫備份 | `work/backups/`、`*.bak`、資料庫 dump | 備份同樣包含個資 |
| 真實環境設定 | `.env`、`.env.production`、`application-local.properties`、`application-prod.yml` | 可能包含資料庫密碼、服務金鑰 |
| 登入憑證 | Cookie、Session ID、Authorization header、密碼、重設密碼連結 | 可被用來登入帳號 |
| 私密金鑰 | `*.pem`、`*.key`、`*.p12`、`*.pfx`、`*.jks` | 可能讓他人冒用服務身分 |
| 匯出、截圖、日誌 | 訓練 CSV、真實帳號截圖、伺服器 log | 可能暴露會員資訊或憑證；人工確認後才分享 |

即使是私人 GitHub 儲存庫，也不應提交以上真實機密。前端 `VITE_*` 環境變數會打包進瀏覽器，不能存放任何祕密。

## 可以上傳

Java、React、CSS 原始碼；`schema.sql` 的空資料表結構；`pom.xml`、`package.json`、`package-lock.json`；Docker 設定；文件、`.gitignore`；僅含假資料的測試。`application.properties` 目前使用環境變數參照，不含實際密碼，可以提交。

`node_modules/`、`target/`、`dist/`、JAR、ZIP、`work/` 工具和快取不是機密本身，但不應放入一般原始碼提交；需要時由 CI 建置產生。提供的 source ZIP 排除資料庫、依賴、建置產物及私密設定。

## 實際檢查方式

在專案根目錄執行：

```powershell
git status --short
git check-ignore -v backend/data/fitlog.mv.db .env
git ls-files
git diff --cached
```

逐一檢查即將提交的內容，搜尋真實 email、密碼、token、private key，並啟用 GitHub secret scanning / push protection（若帳號方案支援）。`.gitignore` 只是防止新檔加入，不會移除已追蹤檔案或歷史紀錄，也不能辨識所有機密。

若敏感檔已被追蹤，確認路徑後使用 `git rm --cached -- <檔案>` 停止追蹤並保留本機檔。如果機密已提交，先撤銷或輪換金鑰、失效相關登入，再清理 Git 歷史與遠端副本；只刪除最新版本不足以解除洩漏。會員資料外洩需另外評估通知與處理。

## 會員機制與部署

- 使用 Spring Security Session、BCrypt（cost 12）、CSRF 防護、HttpOnly / SameSite=Lax Cookie，閒置 30 分鐘後逾時。密碼不寫入瀏覽器 localStorage。
- 每位會員僅能讀寫自己的訓練、課表、自訂動作；內建動作共用。第一個註冊會員一次性接收原有未分配資料，之後註冊的會員不會再次接收。
- 尚未加入電子郵件驗證、忘記密碼、MFA 與登入限流。請先在本機完成第一個會員註冊；此版適合本機使用，公開服務前需補齊上述機制、HTTPS、備份及營運設定。
- 正式 HTTPS 環境設定 `SESSION_COOKIE_SECURE=true`；透過部署平台設定 `DB_PASSWORD` 和 `DB_URL`。Spring Boot 不會自動讀取 `.env`，必須由啟動環境或容器注入。
- 同一瀏覽器的草稿、休息計時依會員 ID 分開存放；共用電腦仍需保護瀏覽器及本機帳戶。舊版未分會員的瀏覽器草稿不會自動匯入。

參考：[Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)、[密碼雜湊](https://docs.spring.io/spring-security/reference/6.5/features/integrations/cryptography.html)。

Google OAuth 補充：`GOOGLE_CLIENT_SECRET`、Google 下載的 `client_secret*.json`／`credentials*.json`、OAuth code／token／Session 均不得上傳。已加入憑證 JSON 的忽略規則。Client ID 不是私密金鑰，但仍以環境變數管理，避免混用測試與正式用戶端。
