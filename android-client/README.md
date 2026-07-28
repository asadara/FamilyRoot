# Family Tree Platform Android Client

Android client berbasis Jetpack Compose untuk FamilyRoot. Implementasi aktif mencakup
authentication, Family Space, person dan relationship graph, Room offline cache,
mutation queue, conflict resolution, serta session terenkripsi Android Keystore.

## Menjalankan aplikasi

1. Jalankan backend pada port `3001`.
2. Buka `android-client` di Android Studio atau gunakan Gradle wrapper.
3. Pilih endpoint debug tanpa mengubah source:
   - perangkat fisik USB: default `http://127.0.0.1:3001/`, setelah menjalankan
     `adb reverse tcp:3001 tcp:3001`;
   - emulator: `-PfamilyTreeApiBaseUrl=http://10.0.2.2:3001/`;
   - LAN: `-PfamilyTreeApiBaseUrl=http://<IP-LAPTOP>:3001/`;
   - endpoint lain tetap harus diberikan eksplisit melalui Gradle property.

Environment variable `FAMILY_TREE_API_BASE_URL` dapat dipakai sebagai pengganti
Gradle property. Production mempunyai endpoint HTTPS terpisah dan tidak memakai
cleartext traffic.

APK untuk tablet/tester pilot selalu dibangun dengan build type khusus agar endpoint
Cloud Run tidak dapat tertimpa oleh default debug lokal:

```powershell
.\gradlew.bat assemblePilot
```

Google Sign-In membutuhkan Web OAuth client ID yang sama dengan backend:

```powershell
$env:FAMILY_TREE_GOOGLE_WEB_CLIENT_ID='<Web application Client ID>'
.\gradlew.bat assemblePilot
Remove-Item Env:FAMILY_TREE_GOOGLE_WEB_CLIENT_ID
```

Setiap build membawa `VERSION_CODE`, `VERSION_NAME`, `API_CONTRACT_VERSION`, dan
`RELEASE_CHANNEL`. Aplikasi memeriksa endpoint kompatibilitas publik sebelum
memulihkan sesi. Build pilot memakai channel `PILOT`, debug memakai `DEBUG`, dan
release memakai `PRODUCTION`. Naikkan `versionCode` untuk setiap APK baru; naikkan
`API_CONTRACT_VERSION` hanya ketika kontrak APK–backend memang tidak kompatibel.
Semua request juga membawa header versi agar backend dapat menolak APK lama setelah
enforcement diaktifkan. Baseline gate pertama adalah `versionCode 2`,
`versionName 0.1.1-beta`. Source pengembangan saat ini menyiapkan lifecycle lengkap,
claim kolektif, undangan tertarget, serta Foster/Guardian sebagai `versionCode 3`,
`versionName 0.1.2-beta`; backend, migration, object contract, dan policy PILOT
harus menerima build 3 sebelum APK tersebut dipasang. Backend model care tidak boleh
di-rollout terpisah karena build lama dapat salah membaca meta baru sebagai lineage.
Selama enforcement policy masih `false`, mismatch atau pemeriksaan yang tidak
tersedia menjadi warning yang dapat dilanjutkan sementara, bukan hard block.

Panduan Google Auth Platform, package/SHA-1 pilot, migration, dan acceptance test
berada di `../docs/GOOGLE_SIGN_IN_SETUP.md`.

Release bundle dibangun dengan R8, resource shrinking, dan Baseline Profile:

```powershell
.\gradlew.bat lintRelease testDebugUnitTest bundleRelease `
  -PfamilyTreeReleaseApiBaseUrl=https://api.example.com/ `
  -PfamilyTreeVersionName=1.0.0 -PfamilyTreeVersionCode=1
```

Signing tidak memiliki fallback key di repository. Workflow `Android release` mengambil
URL API, keystore, password, dan alias dari GitHub Actions secrets, lalu memverifikasi
signature serta membuat checksum SHA-256.

Semua instalasi APK, instrumentation, logcat, dan smoke test perangkat fisik wajib
melalui serial USB. Wireless debugging/Wireless ADB tidak digunakan.

## Verifikasi utama

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

Review privacy/security dan budget performa Fase 4 berada di
`../docs/PHASE4_PRODUCTION_REVIEW.md`.

Blueprint kanonik berada di `../PROJECT_BLUEPRINT.md`; keputusan arsitektur Android
berada di `ARCHITECTURE.md`.
