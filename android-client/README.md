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
