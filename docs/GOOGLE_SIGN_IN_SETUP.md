# Google Sign-In Setup — TRêdhAH Pilot

Google Sign-In memakai Android Credential Manager. APK meminta Google ID token
untuk **Web OAuth client ID**, lalu mengirim token tersebut melalui HTTPS ke
`POST /auth/google`. Backend memverifikasi signature, audience, issuer, expiry,
email terverifikasi, dan Google subject sebelum menerbitkan access/refresh token
TRêdhAH. Request aplikasi berikutnya tetap memakai JWT TRêdhAH.

Web client ID bukan secret dan memang tertanam di APK. OAuth client secret tidak
dipakai oleh alur ini dan tidak boleh dimasukkan ke aplikasi.

## 1. Konfigurasi Google Auth Platform

Gunakan Google Cloud project pilot yang sama dengan Cloud Run:

1. Buka **Google Cloud Console > Google Auth Platform > Branding**.
2. Isi app name `TRêdhAH`, support email, dan developer contact email.
3. Pada **Audience**, pilih `External`, biarkan publishing status `Testing`, lalu
   tambahkan setiap akun Google tester pada **Test users**.
4. Pada **Data Access**, gunakan hanya scope identitas dasar `openid`, `email`, dan
   `profile`. Jangan menambahkan scope Google Drive/Photos untuk fitur login.
5. Pada **Clients**, buat OAuth client bertipe **Web application** dengan nama
   `TRêdhAH Backend Pilot`. Redirect URI dan JavaScript origin tidak diperlukan
   untuk pertukaran ID token Android ini. Salin nilai **Client ID** yang berakhir
   dengan `.apps.googleusercontent.com`.
6. Buat OAuth client kedua bertipe **Android**:
   - name: `TRêdhAH Android Pilot`;
   - package name: `com.example.familytreeplatform`;
   - SHA-1 pilot/debug saat ini:
     `BA:A6:0D:94:83:56:80:99:1D:AA:02:5E:36:53:66:1C:E9:A5:61:6A`.

Package name tersebut adalah identitas APK pilot yang ada sekarang. Sebelum rilis
Play Store, putuskan application ID final dan buat Android OAuth client baru untuk
package serta Play App Signing SHA-1. Mengganti application ID akan dianggap sebagai
aplikasi berbeda oleh Android.

Referensi resmi:

- [Implement Sign in with Google menggunakan Credential Manager](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation)
- [Memverifikasi Google ID token pada backend](https://developers.google.com/identity/sign-in/android/backend-auth)
- [Brand verification dan status Testing](https://developers.google.com/identity/protocols/oauth2/production-readiness/brand-verification)

## 2. Konfigurasi dan migrasi backend

Tambahkan environment variable non-secret pada service Cloud Run:

```text
GOOGLE_OAUTH_CLIENT_ID=<Web application Client ID>
```

Gunakan Client ID Web yang sama untuk backend dan APK. Jangan gunakan Android
client ID pada variabel ini.

Migration `AddGoogleIdentity1753056000000` membuat tabel RLS
`user_google_identities`. Jalankan migration terhadap Supabase sebelum deployment
kode Google Sign-In:

```powershell
Set-Location D:\FamilyRoot\backend
npm ci
npm run build
npm run migration:show
npm run migration:run
```

Pastikan terminal tersebut membaca `DATABASE_URL` pilot yang benar. Jangan menaruh
connection string pada dokumentasi, Git, atau tangkapan layar. Setelah migration
lulus, pastikan Cloud Run mempunyai `GOOGLE_OAUTH_CLIENT_ID`, baru commit/push dapat
memicu continuous deployment dengan aman.

## 3. Build APK pilot

Pada PowerShell yang sama, isi Web client ID sebagai environment variable build:

```powershell
Set-Location D:\FamilyRoot\android-client
$env:FAMILY_TREE_GOOGLE_WEB_CLIENT_ID='<Web application Client ID>'
.\gradlew.bat clean testPilotUnitTest assemblePilot
Remove-Item Env:FAMILY_TREE_GOOGLE_WEB_CLIENT_ID
```

Alternatif Gradle property bernama `familyTreeGoogleWebClientId`. Jangan menulis
nilai proyek pilot ke source yang di-commit meskipun client ID bukan secret, agar
konfigurasi development/pilot/release tetap terpisah.

## 4. Acceptance test

Lakukan dengan akun yang sudah ditambahkan sebagai test user:

1. mode **Buat akun** > **Daftar dengan Google** membuat akun baru dan masuk;
2. logout, lalu mode **Masuk** > **Masuk dengan Google** kembali ke akun yang sama;
3. login Gmail yang sebelumnya sudah terdaftar dengan password menautkan ke user ID
   yang sama dan tidak menduplikasi akun;
4. akun yang tidak ada dalam daftar tester ditolak oleh Google selama status
   `Testing`;
5. token Google palsu/kedaluwarsa ditolak backend dengan HTTP 401; dan
6. login email/password, refresh, logout, undangan, dan role lama tetap berfungsi.

Untuk publikasi ke semua pengguna, siapkan halaman publik aplikasi dan kebijakan
privasi pada domain yang terverifikasi, selesaikan Branding, lalu ubah Audience dari
Testing sesuai proses Google. Tahap pilot tidak perlu meminta scope sensitif.
