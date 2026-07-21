# Tahap 7 — Runbook Cloud Pilot

> **Status:** Repository, Supabase Free, CI, dan deployment Cloud Run terverifikasi;
> penutupan menunggu acceptance pada perangkat serta pemeriksaan operasional pemilik.
> **Batas data:** Hanya seed/dummy. Jangan unggah data atau foto keluarga nyata.
> **Target awal:** Cloud Run dan Supabase Free di Singapore.
> **Endpoint pilot:**
> `https://familyroot-api-pilot-441927816740.asia-southeast1.run.app`

## 1. Yang Sudah Disiapkan di Repository

- backend tetap NestJS/TypeScript; tidak ada migrasi ke Kotlin;
- development lokal tetap memakai SQLite;
- production wajib memakai PostgreSQL `DATABASE_URL` dengan `sslmode=require`;
- `synchronize` selalu nonaktif pada PostgreSQL dan schema dikelola migration;
- migration mengaktifkan RLS pada seluruh tabel aplikasi secara eksplisit, sehingga
  tidak bergantung pada checkbox otomatis di dashboard Supabase;
- container production memakai Node.js 22, non-root user, port `8080`, dan healthcheck;
- Cloud Run dapat scale-to-zero dengan pool database awal maksimal lima koneksi;
- bucket media diasumsikan privat;
- upload foto divalidasi maksimal 2 MB, magic bytes JPEG/PNG/WebP, batas pixel,
  re-encoding/compression, dan penghapusan metadata termasuk EXIF;
- URL baca media ditandatangani selama 60 detik setelah pemeriksaan Family Space dan
  role;
- CI membangun container setelah lint, build, unit test, dan e2e test lulus.

Google Sign-In kemudian ditambahkan dengan pola yang telah diputuskan: Credential
Manager Android mengirim Google ID token ke endpoint pertukaran backend, lalu request
berikutnya tetap memakai access JWT dan rotating refresh token TRêdhAH. Setup OAuth,
migration identity, package/SHA-1, dan acceptance test berada di
`GOOGLE_SIGN_IN_SETUP.md`.

Identitas `Person` lintas Family Space juga tidak digabung. Isolasi tenant yang telah
disepakati tetap berlaku sampai model consent, field sharing, pencabutan, dan audit
diputuskan secara eksplisit.

## 2. Tindakan Pemilik Akun — Supabase

Jangan kirim password database atau secret key melalui chat dan jangan menaruhnya di
Git.

1. Masuk ke Supabase, buat organization bila belum ada, lalu pilih **New project**.
2. Pilih plan **Free** dan region **Singapore/Southeast Asia**. Buat password database
   acak dan simpan di password manager.
3. Setelah project aktif, buka **Connect** lalu pilih **Session pooler**. Gunakan URI
   port `5432`, bukan transaction pooler port `6543`. Pastikan URI berakhir dengan
   `sslmode=require`. Bila password mempunyai karakter khusus, gunakan nilai URI yang
   sudah di-encode oleh dashboard.
4. Buka **Storage > New bucket** dan buat bucket `family-media`:
   - public: **OFF**;
   - maksimum file: `2 MB`;
   - MIME yang diizinkan: `image/jpeg`, `image/png`, `image/webp`.
5. Buka pengaturan API keys, buat/copy **secret key** berformat `sb_secret_...` untuk
   server. Jangan memakai publishable key untuk backend dan jangan memasukkan secret
   key ke APK.
6. Catat Project URL (`https://PROJECT_REF.supabase.co`). URL dan project ref bukan
   password, tetapi secret key serta database URI tetap rahasia.

Dokumentasi resmi: [Supabase database connections](https://supabase.com/docs/guides/database/connecting-to-postgres),
[private buckets](https://supabase.com/docs/guides/storage/buckets/fundamentals), dan
[API keys](https://supabase.com/docs/guides/getting-started/api-keys).

## 3. Jalankan Migration dan Seed Dummy

Isi `backend/.env` lokal (file ini diabaikan Git) dengan nilai sebenarnya:

```dotenv
NODE_ENV=development
JWT_SECRET=<acak-minimal-32-karakter>
DATABASE_URL=<session-pooler-uri-dengan-sslmode=require>
DB_POOL_MAX=5
SUPABASE_URL=https://PROJECT_REF.supabase.co
SUPABASE_SECRET_KEY=<sb_secret_...>
SUPABASE_STORAGE_BUCKET=family-media
HOST=0.0.0.0
PORT=3001
CORS_ORIGINS=
```

Kemudian dari PowerShell:

```powershell
Set-Location D:\FamilyRoot\backend
npm ci
npm run migration:show
npm run migration:run
npm run seed:dev
```

Migration tidak dijalankan otomatis saat server start agar dua instance yang mulai
bersamaan tidak berebut perubahan schema. Seed hanya untuk pilot dan tidak boleh
dijalankan pada database keluarga nyata.

## 4. Tindakan Pemilik Akun — Google Cloud

Cloud Run Free Tier tetap memerlukan billing account dan bukan hard spending cap.
Kerjakan bagian ini hanya setelah menerima risiko tersebut.

1. Buat/pilih Google Cloud project khusus pilot dan hubungkan billing account.
2. Buat budget kecil (contoh USD 1) dengan alert 50%, 90%, dan 100%. Budget hanya
   memberi notifikasi; ia tidak otomatis menghentikan biaya.
3. Aktifkan Cloud Run, Cloud Build, Artifact Registry, dan Secret Manager API.
4. Buat tiga Secret Manager secret:
   - `familyroot-database-url`;
   - `familyroot-jwt-secret`;
   - `familyroot-supabase-secret-key`.
5. Di Cloud Run pilih **Deploy from source / continuously deploy from repository**,
   hubungkan GitHub `asadara/FamilyRoot`, branch `main`, dan root `Dockerfile`.
6. Gunakan konfigurasi awal berikut:
   - service: `familyroot-api-pilot`;
   - region: `asia-southeast1` (Singapore);
   - authentication: allow unauthenticated invocation (endpoint aplikasi tetap
     dilindungi JWT; `/health` memang publik);
   - port: `8080`;
   - memory: `512 MiB`;
   - CPU: `1` dan hanya dialokasikan selama request;
   - minimum instances: `0`;
   - maximum instances: `1`;
   - concurrency: `20`;
   - timeout: `60 seconds`.
7. Tambahkan environment variable non-secret:
   - `NODE_ENV=production`;
   - `HOST=0.0.0.0`;
   - `DB_POOL_MAX=5`;
   - `SUPABASE_URL=https://PROJECT_REF.supabase.co`;
   - `SUPABASE_STORAGE_BUCKET=family-media`;
   - `CORS_ORIGINS=` untuk APK native. Tambahkan hanya origin HTTPS yang eksplisit
     saat client web benar-benar tersedia.
8. Referensikan secret sebagai environment variable:
   - `DATABASE_URL` → `familyroot-database-url`;
   - `JWT_SECRET` → `familyroot-jwt-secret`;
   - `SUPABASE_SECRET_KEY` → `familyroot-supabase-secret-key`.
9. Pastikan runtime service account memperoleh role **Secret Manager Secret
   Accessor** hanya untuk ketiga secret tersebut.

Setelah URL HTTPS Cloud Run tersedia, buat APK debug pilot dengan trailing slash pada
base URL:

```powershell
Set-Location D:\FamilyRoot\android-client
.\gradlew.bat assemblePilot
```

Untuk build ini ADB reverse tidak dipakai sebagai jalur API; USB tetap dipakai untuk
install, instrumentation, dan logcat.

Referensi resmi: [Cloud Run secrets](https://cloud.google.com/run/docs/configuring/services/secrets),
[minimum instances](https://cloud.google.com/run/docs/configuring/min-instances), dan
[Cloud Run container contract](https://cloud.google.com/run/docs/container-contract).

## 5. Verifikasi Sebelum Tahap 7 Ditutup

- buka `https://SERVICE_URL/health` dan pastikan respons `200`;
- register/login, refresh rotation, logout, lalu login kembali;
- pastikan enam profil seed tampil dari dua session/perangkat;
- buat perubahan saat offline lalu pastikan queue tersinkron setelah online;
- restart/scale-to-zero Cloud Run dan pastikan data PostgreSQL tetap ada;
- upload satu PNG/JPEG/WebP dummy di bawah 2 MB, lalu pastikan URL baca kedaluwarsa;
- pastikan bucket tidak dapat dibaca publik;
- jalankan export data uji dan simpan di lokasi aman;
- periksa APK, repository, build log, dan Cloud Run log untuk memastikan tidak ada
  secret;
- periksa billing report dan Supabase usage sebelum menandai pilot selesai.

Tahap 7 hanya dapat ditutup setelah seluruh pemeriksaan cloud di atas mempunyai bukti
hasil. Kesiapan source code saja belum cukup.

### Bukti checkpoint Supabase — 20 Juli 2026

- Session Pooler port 5432 terhubung melalui TLS `sslmode=require`;
- migration awal berhasil dan tercatat di `familyroot_migrations`;
- RLS aktif pada seluruh 13 tabel aplikasi;
- seed cloud menghasilkan satu `Keluarga Demo` dan enam profil;
- login akun dummy, pembacaan Family Space, dan pembacaan enam profil berhasil;
- bucket `family-media` ditemukan private, limit 2 MB, dan hanya menerima
  JPEG/PNG/WebP;
- satu pixel PNG dummy berhasil di-upload, metadata media tercatat, signed URL 60
  detik dibuat, dan download menghasilkan HTTP 200.

### Bukti checkpoint Cloud Run — 20 Juli 2026

- GitHub Actions run `29721825385` lulus untuk backend, production container build,
  dan Android;
- Cloud Run berhasil men-deploy revision dari root `Dockerfile` dan `/health`
  mengembalikan `200` dengan status `ok` melalui HTTPS;
- endpoint yang dilindungi mengembalikan `401` tanpa FamilyRoot JWT;
- login, rotating refresh token, logout, penolakan reuse refresh token, dan login
  kembali berhasil terhadap deployment cloud;
- dua session akun demo terpisah melihat Family Space dan enam person ID yang sama;
- upload media dummy ke bucket privat dan download terotorisasi menghasilkan `200`;
  URL yang sama terkonfirmasi ditolak dengan `400` setelah 70 detik;
- export backup cloud menghasilkan format `familyroot-backup` schema 1 berisi enam
  person dan enam relationship; export GEDCOM memiliki header dan trailer valid;
- APK debug berhasil melewati unit test, lint, dan assemble dengan endpoint Cloud Run
  tertanam sebagai `API_BASE_URL`; `local.properties` tetap diabaikan Git.
- APK tersebut berhasil dipasang dan cold-launch pada Samsung SM-T225 Android 14
  tanpa crash; seluruh 20 connected instrumentation test lulus.

Checkpoint yang masih terbuka sebelum Tahap 7 dapat ditutup:

- selesaikan smoke test login/data cloud pada Samsung dan satu perangkat kedua melalui
  USB (install/cold-launch serta instrumentation Samsung sudah lulus);
- buat mutation saat perangkat offline dan pastikan queue tersinkron setelah online,
  termasuk verifikasi conflict/idempotent retry;
- biarkan service scale-to-zero atau buat revision restart, kemudian pastikan enam
  profil PostgreSQL tetap tersedia;
- simpan file backup dummy di lokasi aman milik pemilik project;
- periksa Cloud Run revision (`min=0`, `max=1`), secret reference, build/runtime log,
  APK, dan repository untuk memastikan tidak ada secret;
- periksa Google Cloud billing report/budget alert serta Supabase usage.

Jika perangkat kedua tidak tersedia, kontrak kolaborasi, conflict, dan idempotency
dapat diuji sementara melalui dua session independen. Script hanya menerima akun
dummy `example.test`, memerlukan Family Space `Keluarga Demo`, harus diberi izin
mutation dummy secara eksplisit, memulihkan nilai profil awal, dan logout kedua sesi:

```powershell
Set-Location D:\FamilyRoot\backend
$env:PILOT_API_URL='https://SERVICE_URL'
$env:PILOT_DEMO_PASSWORD='<password-akun-dummy>'
$env:PILOT_ALLOW_DUMMY_MUTATION='true'
npm run smoke:step7:cloud
Remove-Item Env:PILOT_API_URL,Env:PILOT_DEMO_PASSWORD,Env:PILOT_ALLOW_DUMMY_MUTATION
```

Smoke ini membuktikan kontrak backend/cloud, tetapi bukti UI lintas perangkat tetap
ditunda sampai perangkat kedua tersedia.

### Bukti smoke dua session — 20 Juli 2026

Smoke terhadap deployment Cloud Run lulus untuk seluruh checkpoint berikut:

- dua login independen dan snapshot enam profil yang identik;
- retry mutation sukses dengan `clientMutationId` yang sama tidak menaikkan version
  dua kali;
- session dengan version lama menerima conflict `409` beserta version server;
- mutation yang direbase berhasil dan terlihat dari session pertama;
- profil dummy dikembalikan ke nilai awal dan kedua refresh session di-logout.

Perangkat kedua tetap diperlukan nanti untuk validasi UI/device-specific, tetapi tidak
lagi memblokir pembuktian kontrak kolaborasi backend pada pilot ini.

Audit sesudah smoke menemukan E2E lokal lama pernah memuat `backend/.env` dan menyentuh
Supabase pilot. Akun dummy `owner@example.test` memiliki empat Family Space test:
`Secure Family`, `Other Family`, `GEDCOM Restore`, dan `Backup Restore`. Setup test
telah diperbaiki agar `NODE_ENV=test` mengabaikan `.env` dan selalu memakai SQLite
`:memory:`; 8/8 E2E kemudian lulus secara hermetic. Artefak cloud lama belum dihapus
karena cleanup destruktif memerlukan persetujuan pemilik.

Catatan acceptance Samsung: pengujian pertama membuktikan worker mengirim mutation
offline dan Cloud Run membalas `200`; PostgreSQL menyimpan catatan serta menaikkan
versi person. Namun workspace graph tetap menampilkan snapshot seed lama. Akar masalah
berada pada state Android, bukan pada queue atau backend: graph belum mengamati Room
dan refresh masih dapat mengembalikan respons mentah sebelum hasil reapply queue.
Perbaikan membuat graph mengamati Room serta mengembalikan hasil lokal gabungan.
Retest manual kemudian berhasil: teks tambahan tetap tersedia setelah worker sync dan
muncul kembali saat halaman dibuka. Agar pengguna tidak perlu menutup halaman untuk
meminta data terbaru, halaman profil juga mendapat pull-to-refresh dari posisi paling
atas beserta indikator dan refresh person/relationship/source/media. Regression/unit
test, lint, assemble, dan seluruh 20 instrumentation test lulus; gesture refresh pada
APK terbaru menunggu konfirmasi visual pemilik.
