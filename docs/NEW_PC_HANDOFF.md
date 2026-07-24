# FamilyRoot — Handoff ke PC Pengembangan Baru

> **Keyword pemulihan konteks:** `familyroot`
> **Target:** VS Code/Codex pada PC Windows baru
> **Branch kerja:** `main`

## 1. Clone dan verifikasi repository

Jalankan di PowerShell pada direktori tempat project akan disimpan:

```powershell
git clone https://github.com/asadara/FamilyRoot.git
Set-Location FamilyRoot
git checkout main
git pull --ff-only origin main
git status --short --branch
git log -1 --oneline
```

Status yang benar tidak memiliki file berubah dan tidak menunjukkan branch tertinggal
dari `origin/main`.

## 2. Pulihkan konteks Codex

Buka folder root `FamilyRoot` di VS Code, mulai sesi Codex dari folder tersebut, lalu
kirim keyword berikut sebagai pesan tersendiri:

```text
familyroot
```

File `AGENTS.md` memetakan keyword tersebut ke prosedur berikut:

```text
FAMILYROOT-MEMORY — baca PROJECT_MEMORY.md seluruhnya, pulihkan konteks project,
periksa status Git terbaru, lalu konfirmasi pemahaman sebelum melakukan perubahan.
```

Konteks kanonik berada di `PROJECT_MEMORY.md`, `PROJECT_BLUEPRINT.md`,
`docs/PROJECT_GAP_AUDIT_2026-07-24.md`, dan dokumen keputusan yang dirujuk oleh
memory. Riwayat chat lama tidak dibutuhkan untuk melanjutkan project.

Setelah konteks dipulihkan, pastikan sesi baru menyebutkan:

- commit dan hubungan branch lokal terhadap `origin/main`;
- fase fondasi yang sudah selesai;
- backlog aktif dari audit terbaru;
- item yang sengaja `DEFERRED` atau `SUPERSEDED`;
- batas bahwa audit tidak otomatis memberi izin melakukan perubahan destruktif.

## 3. Siapkan backend dan data demo

Data akun serta keluarga demo disimpan dalam manifest
`backend/src/dev/demo-data.ts` dan dibangun oleh seed
`backend/src/dev/seed-dev.ts`. Database `backend/dev.sqlite` adalah runtime lokal dan
tidak boleh dimasukkan ke Git karena dapat mengandung session, mutation log, data
e2e, atau data input lokal lain.

```powershell
Set-Location backend
npm ci
Copy-Item .env.example .env
npm run seed:dev
npm run start:dev
```

Sebelum penggunaan di luar pengembangan lokal, ganti `JWT_SECRET` dalam `.env` dengan
nilai privat minimal 32 karakter. `.env` sengaja tidak disimpan di Git.

Daftar akun dan batas penggunaan seed dijelaskan di `backend/DEMO_DATA.md`.

## 4. Siapkan Android client

Buka folder `android-client` melalui Android Studio satu kali agar
`android-client/local.properties` dibuat sesuai lokasi Android SDK PC baru. File
tersebut bersifat lokal dan tidak dipindahkan dari PC lama.

Verifikasi dari PowerShell:

```powershell
Set-Location ..\android-client
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Untuk perangkat Android fisik, gunakan USB debugging dan serial perangkat:

```powershell
adb devices -l
adb -s <SERIAL_USB> reverse tcp:3001 tcp:3001
adb -s <SERIAL_USB> install -r .\app\build\outputs\apk\debug\app-debug.apk
```

Wireless ADB tidak digunakan dalam project ini.

## 5. File yang tidak perlu dipindahkan

- `.idea`, `.vscode`, `node_modules`, `.gradle`, dan seluruh folder build;
- APK/AAB hasil build;
- `android-client/local.properties`;
- `backend/dev.sqlite` beserta file journal/WAL;
- `.env`, token, session, keystore, dan secret lain.

Semua dependency serta artefak build dibuat ulang pada PC baru. Jika suatu saat data
lokal non-demo memang perlu dimigrasikan, lakukan sebagai backup terenkripsi di luar
Git dan audit isinya terlebih dahulu.
