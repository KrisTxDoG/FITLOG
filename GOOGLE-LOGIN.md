# Google 帳號登入設定

程式已提供 OpenID Connect 登入，但預設關閉，必須提供自己的 Google OAuth 用戶端後才可使用。沒有憑證時，電子郵件／密碼登入不受影響。Google 登入只讀取 openid、email、profile，不要求 Gmail、雲端硬碟或聯絡人權限。

## 1. 建立 Google OAuth 用戶端

到 [Google Cloud Console](https://console.cloud.google.com/) 選擇或建立專案，開啟 Google Auth Platform：

1. 設定 Branding：應用程式名稱、使用者支援信箱、開發者聯絡資訊。正式公開時填寫你的網站、隱私權政策與服務條款。
2. 設定 Audience：個人 Google 帳號通常選 External。測試階段將需要登入的帳號加入 Test users。公開使用前依 Console 指示發布，必要時完成 Google 要求的驗證。
3. 建立 Client，類型選「Web application／網頁應用程式」，不是 Desktop application。
4. 在 Authorized redirect URIs 加入下方**完整回呼網址**。本實作採後端重新導向，不使用前端 JavaScript Google SDK。

本機統一用這個入口：`http://127.0.0.1:8080/`

```text
http://127.0.0.1:8080/api/auth/google/callback/google
```

如果你改用 localhost，必須同時修改入口、Google 設定及 GOOGLE_REDIRECT_URI，不能混用兩個 hostname 的 Session Cookie。

```text
http://localhost:8080/api/auth/google/callback/google
```

正式網域範例（請換成你的實際網域）：

```text
https://your-domain.example/api/auth/google/callback/google
```

## 2. 設定後端環境變數

| 變數 | 用途 |
| --- | --- |
| GOOGLE_LOGIN_ENABLED | `true` 啟用；預設 `false` |
| GOOGLE_CLIENT_ID | Google 用戶端 ID |
| GOOGLE_CLIENT_SECRET | Google 用戶端密鑰，只能放後端 |
| GOOGLE_REDIRECT_URI | 與 Console 登記完全一致的回呼網址 |
| SESSION_COOKIE_SECURE | 正式 HTTPS 設為 `true`；本機 HTTP 保持 `false` |

在 Railway／Render 等部署平台的環境變數介面輸入，然後重新部署。不要放 `VITE_*`，不要把 Secret 貼到對話、README、GitHub 或指令歷史。Spring Boot 不會自動讀取 `.env`。

本機 PowerShell 可使用互動輸入，避免把 Secret 寫進指令內容：

```powershell
$env:GOOGLE_LOGIN_ENABLED = 'true'
$env:GOOGLE_CLIENT_ID = Read-Host 'Google Client ID'
$googleSecretInput = Read-Host 'Google Client Secret' -AsSecureString
$env:GOOGLE_CLIENT_SECRET = [System.Net.NetworkCredential]::new('', $googleSecretInput).Password
$env:GOOGLE_REDIRECT_URI = 'http://127.0.0.1:8080/api/auth/google/callback/google'
# 在 fitlog 專案根目錄執行；先停止舊的後端
try { ./start.ps1 } finally {
  Remove-Item Env:GOOGLE_CLIENT_SECRET -ErrorAction SilentlyContinue
  Remove-Variable googleSecretInput -ErrorAction SilentlyContinue
}
```

首次需依 README 建置新版前端及後端。建議先用打包後的 8080 同源版本驗證；Vite／Docker 若使用不同入口埠，也要登記對應入口的回呼網址。`/api/` 既有代理包含登入啟動與回呼路由。

## 3. 登入與連結行為

- 按「使用 Google 登入」後前往 Google，Google 驗證成功才返回 FITLOG。驗證 Google 簽章、到期時間、audience、issuer、state／nonce 的工作交由 Spring Security OIDC 流程執行。
- 後端另要求 `email_verified=true`，並用 Google `sub` 穩定識別會員，而不是只靠信箱。Google 信箱之後改變仍對應同一會員；不自動改寫 FITLOG 已保存的聯絡信箱。
- 沒有既有同信箱帳號時建立會員。Google-only 會員不會取得可用的本機登入密碼。第一位註冊會員接收舊資料的原有規則同樣適用，因此公開之前請先完成自己的帳號設定。
- 如果同信箱已有會員，顯示「連結既有帳號」，需輸入既有 FITLOG 密碼。待連結身分只放伺服器 Session、5 分鐘到期、最多嘗試 5 次；成功後使用原會員 ID，保留所有資料。沒有同信箱自動合併，也不會覆蓋另一個已連結 Google 帳號。
- 所有訓練 API 繼續使用本機會員 ID 與 Session Cookie；Google token 不寫入前端儲存空間、資料庫或 GitHub。
- 登出只登出 FITLOG，不會把使用者的 Google 帳號登出。尚未加入解除連結、Google-only 帳號設定密碼或忘記密碼功能。

## 常見問題

- `redirect_uri_mismatch`：比較協定、hostname、port、完整路徑是否逐字一致。
- 測試帳號被拒絕：確認 Audience 的測試使用者名單與 Google 帳戶政策。
- 返回後沒有登入：确认瀏覽器入口與回呼同源，以及 HTTPS Cookie 設定。
- 本機沒有按鈕或按鈕停用：確認更新前端，啟用環境變數並重新啟動後端。
- 不要關閉 state、nonce、CSRF 或 token 驗證來排錯。使用一般系統瀏覽器測試；Google 可能拒絕嵌入式瀏覽器授權。

本版本使用模擬已驗證 OIDC 身分進行整合測試；**沒有用真實 Google Client ID／Secret 完成端到端授權**，需設定後自行登入驗證。

參考：[Google OpenID Connect](https://developers.google.com/identity/openid-connect/openid-connect)、[Google 網頁伺服器 OAuth](https://developers.google.com/identity/protocols/oauth2/web-server)、[Spring Security OAuth2 Login](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/)。
