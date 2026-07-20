# FamilyRoot — Project Memory dan Session Handoff

> **Status:** Memori operasional aktif untuk melanjutkan pekerjaan lintas sesi chat
> **Snapshot diperbarui:** 19 Juli 2026, Asia/Jakarta
> **Repository:** `asadara/FamilyRoot`
> **Branch:** `main`
> **Baseline sebelum implementasi frontend v2:** `b8679d3ca772cf3786a381e0716fedc4906f24da` (`feat: complete phase 4 data sustainability`)
> **Keyword pemulihan konteks:** `FAMILYROOT-MEMORY`
> **Tidak menggantikan:** `PROJECT_BLUEPRINT.md`; dokumen ini merangkum keadaan implementasi dan keputusan kerja agar sesi baru dapat melanjutkan konteks dengan benar.

## 1. Cara Melanjutkan di Sesi Chat Baru

Di sesi baru, buka workspace repository ini lalu kirim instruksi berikut:

```text
FAMILYROOT-MEMORY — baca PROJECT_MEMORY.md seluruhnya, lalu PROJECT_BLUEPRINT.md,
android-client/ARCHITECTURE.md, dan docs/PHASE4_PRODUCTION_REVIEW.md. Periksa git
status dan commit terbaru. Ringkas pemahaman serta kondisi aktual sebelum melakukan
perubahan apa pun. Jangan gunakan Wireless ADB; pengujian tablet hanya melalui USB.
```

Keyword tidak menyimpan memori secara magis di luar repository. Fungsinya adalah
perintah singkat yang mengarahkan sesi baru ke dokumen kanonik yang benar. Agen yang
menerima keyword wajib membaca file ini dari disk, bukan mengandalkan ingatan chat.

Urutan pemulihan yang aman:

1. baca `PROJECT_MEMORY.md` sampai selesai;
2. baca dokumen sumber sesuai daftar pada bagian 16;
3. jalankan `git status --short --branch` dan `git log -5 --oneline --decorate`;
4. bandingkan keadaan aktual dengan snapshot dalam dokumen ini;
5. laporkan pemahaman dan perbedaan yang ditemukan;
6. tunggu arah produk sebelum menyusun Blueprint v2 atau mengubah implementasi.

## 2. Identitas, Tujuan, dan Batas Produk

FamilyRoot adalah aplikasi keluarga privat dan kolaboratif untuk membangun,
memelihara, serta mewariskan sejarah keluarga lintas generasi. Produk ini bukan
sekadar pembuat diagram silsilah dan bukan jejaring sosial publik. Sasaran akhirnya
adalah **arsip keluarga digital yang terpercaya**.

Nilai produk yang harus tetap dijaga:

- **Preserve:** menyimpan identitas, sejarah, cerita, foto, dan dokumen keluarga.
- **Connect:** membantu orang memahami hubungan keluarga lintas generasi.
- **Collaborate:** keluarga dapat memperbarui arsip bersama sesuai kewenangan.
- **Trust:** perubahan, sumber informasi, dan keputusan dapat ditelusuri.
- **Private by default:** data keluarga tidak dipublikasikan sebagai jejaring sosial.
- **Family-owned portability:** keluarga dapat mengekspor dan memulihkan datanya.

Prinsip yang tidak boleh hilang saat UI/UX diperbarui:

- profil `Person` mewakili orang nyata dan berbeda dari akun `User`;
- satu keluarga bekerja di ruang privat bernama `Family Space`;
- backend adalah sumber kebenaran bisnis dan penegak aturan silsilah;
- Android adalah client, cache, dan antrean kerja offline—bukan otoritas kebenaran;
- identitas teknis seperti UUID dan `spaceId` tidak boleh menjadi beban UX;
- kemudahan penggunaan tidak boleh mengorbankan privasi, audit, atau integritas lineage;
- AI di masa depan hanya boleh memberi saran yang dikonfirmasi manusia.

## 3. Status Produk dan Roadmap Saat Snapshot

Blueprint aktif saat ini adalah `PROJECT_BLUEPRINT.md` versi 1.0. Blueprint v1
berfokus pada pembangunan fondasi aplikasi. Seluruh empat fase fondasi telah selesai:

| Fase | Status | Hasil utama |
|---|---|---|
| Fase 1 — Fondasi Aman | `DONE` 100% | JWT auth, refresh/session, role authorization, endpoint protection, integrity, transaction, audit, API contract |
| Fase 2 — Android Modern | `DONE` 100% | Compose/Material 3, feature ViewModel, Navigation Compose, Room offline read, adaptive/accessibility foundation |
| Fase 3 — Produk Inti Matang | `DONE` 100% | onboarding/invitation, claim/review, graph, source/media metadata, proposal, duplicate merge, relationship path, seed demo |
| Fase 4 — Keberlanjutan Data | `DONE` 100% | offline write/conflict, refresh recovery, GEDCOM/backup/PDF/PNG, privacy hardening, benchmark, release pipeline |

Belum ada Fase 5 yang disepakati. Langkah selanjutnya bukan otomatis menambah fase
teknis, melainkan diskusi produk dan UI/UX untuk menghasilkan **Blueprint v2**.
Blueprint v2 harus:

- tetap mempunyai hubungan dan traceability ke fondasi Blueprint v1;
- mendefinisikan pengalaman produk aktual, information architecture, user journey,
  visual language, interaksi graph, dan prioritas enhancement;
- membedakan fitur yang sudah ada secara teknis dari fitur yang sudah layak secara UX;
- dibahas bersama pengguna sebelum file blueprint atau kode diubah;
- tidak memaksakan Definition of Done pada awal diskusi produk karena pengguna ingin
  lebih dahulu mengeksplorasi arah UI/UX dan kebutuhan aplikasi.

## 4. Gambaran Arsitektur Sistem

```text
┌──────────────────────── Android tablet ────────────────────────┐
│ Jetpack Compose UI                                              │
│        ↓ events / immutable state                               │
│ ViewModel per feature                                           │
│        ↓                                                        │
│ Repository                                                      │
│   ↙ local                              remote ↘                  │
│ Room cache + offline mutation queue      Retrofit/OkHttp         │
└──────────────────────────────────────────────┬───────────────────┘
                                               │ HTTP REST + JWT
                                               │ development: :3001
┌──────────────────────── Backend ──────────────▼──────────────────┐
│ NestJS controllers → services/rules/transactions → TypeORM      │
│                                         ↓                       │
│                             SQLite `dev.sqlite` (development)    │
└─────────────────────────────────────────────────────────────────┘
```

Ringkasan kepemilikan data:

```text
Backend database  = kebenaran pusat data keluarga
Room Android      = salinan kerja/cache + antrean perubahan offline
GitHub repository = source code dan dokumentasi, bukan database pengguna
Export files      = salinan portabel yang dikendalikan pengguna
```

Kalimat pengingat utama:

> Server menyimpan kebenaran keluarga, tablet menyimpan salinan kerja, GitHub
> menyimpan source code, dan export menyimpan salinan portabel.

## 5. Backend: Teknologi, Runtime, dan Penyimpanan

Backend menggunakan:

- NestJS/TypeScript;
- TypeORM;
- SQLite untuk development;
- REST API pada port `3001`;
- JWT access token dan opaque rotating refresh token;
- validasi DTO, authorization berbasis membership/role, transaksi, audit, dan
  optimistic concurrency.

Konfigurasi database aktif berasal dari `DB_DATABASE`; jika tidak diberikan,
backend memakai `dev.sqlite`. `synchronize` hanya aktif ketika
`NODE_ENV !== 'production'`.

Konsekuensi penting:

- `backend/dev.sqlite` adalah database lokal laptop, bukan database cloud;
- file `*.sqlite`, `*.db`, `.env`, log, build output, APK, dan AAB diabaikan Git;
- akun asli, akun dummy, password hash, Family Space, person, dan relationship yang
  dibuat saat runtime tidak ikut ke GitHub;
- clone repository di komputer lain hanya membawa kode dan dokumentasi;
- untuk bekerja dari komputer lain, buat `.env` lokal yang aman, jalankan seed
  development atau register ulang akun, atau arahkan client ke backend permanen yang
  sama setelah deployment tersedia;
- jangan pernah memasukkan database development, JWT secret, signing key, password,
  pairing code, token undangan, atau credential akun ke repository.

Backend produksi belum dideploy. Belum tersedia domain/API production permanen,
managed production database, object storage media, strategi backup server yang sudah
dioperasikan, atau pipeline migration backend yang siap produksi. Android release
pipeline sudah dapat menerima URL HTTPS dan signing material melalui CI secrets,
tetapi kesiapan build Android tidak sama dengan kesiapan infrastruktur backend.

Risiko skema yang perlu dibahas sebelum production:

- entity TypeORM saat ini banyak menyimpan relasi sebagai kolom UUID tanpa deklarasi
  foreign-key relation TypeORM eksplisit;
- cross-space dan referential integrity ditegakkan oleh service, transaction, dan test;
- production memakai `synchronize: false`, tetapi repository belum mempunyai rangkaian
  migration backend yang menjadi mekanisme inisialisasi/evolusi skema;
- pemilihan PostgreSQL atau database production lain, migration policy, backup,
  restore drill, retention, encryption, dan monitoring belum diputuskan.

## 6. Model Data dan Makna Domain

### 6.1 User tidak sama dengan Person

`User` adalah akun yang dapat login. Ia memiliki identitas akun seperti email/phone,
display name, dan password hash. `Person` adalah satu manusia dalam silsilah—termasuk
orang yang tidak pernah memiliki akun, anak kecil, leluhur, atau orang yang sudah
meninggal.

Hubungannya dimodelkan melalui `UserPersonClaim`:

- akun meminta klaim atas profil dirinya;
- status claim adalah `PENDING`, `VERIFIED`, atau `REJECTED`;
- OWNER/ADMIN dapat memverifikasi;
- pemisahan ini mencegah setiap profil keluarga diperlakukan sebagai akun login.

### 6.2 Family Space adalah batas tenant

`FamilySpace` adalah ruang privat sebuah keluarga. `SpaceMember` menghubungkan User
dengan Family Space dan memberi role:

- `OWNER`;
- `ADMIN`;
- `EDITOR`;
- `VIEWER`.

Sebagian besar record domain membawa `spaceId`. Semua akses backend harus memastikan
akun terautentikasi adalah member space dan memiliki role yang cukup. Mengetahui
`spaceId` saja tidak memberikan akses.

Kapabilitas ringkas:

- semua role dapat membaca data keluarga sesuai membership;
- OWNER/ADMIN/EDITOR dapat membuat atau memperbarui person dan relationship;
- VIEWER tidak boleh melakukan mutasi langsung dan dapat memakai proposal pada alur
  yang tersedia;
- OWNER/ADMIN dapat meninjau claim/proposal dan merge kandidat duplicate;
- export/import/restore dibatasi untuk OWNER/ADMIN;
- hanya OWNER dapat mengundang ADMIN; ADMIN dapat mengundang EDITOR/VIEWER.

### 6.3 Person

Record `persons` menyimpan antara lain:

- `personId`, `spaceId`, dan `fullName`;
- title, first name, last name, suffix, nickname;
- gender, birth/death date dan place;
- notes dan ID number;
- `lifeStatus`: `ALIVE`, `DECEASED`, atau `UNKNOWN`;
- `version` untuk optimistic concurrency;
- `isDeleted` untuk soft delete;
- created/updated timestamp.

Model target Blueprint masih lebih kaya daripada implementasi saat ini. Nama historis,
tanggal tidak pasti, field-level privacy, timeline event lengkap, dan struktur tempat
belum seluruhnya matang.

### 6.4 Relationship adalah edge graph

Orang tua, anak, dan pasangan tidak disimpan sebagai `fatherId`, `motherId`, atau
`spouseId` di tabel Person. Mereka disimpan sebagai edge di tabel `relationships`.

Untuk `PARENT_CHILD`:

```text
fromPersonId = orang tua
toPersonId   = anak
meta         = BIOLOGICAL | ADOPTIVE | STEP
```

Untuk `SPOUSE`:

```text
fromPersonId ↔ toPersonId = dua pasangan
meta                   = MARRIED | DIVORCED | WIDOWED
startDate/endDate       = riwayat waktu dasar
```

Service backend memvalidasi self-link, duplicate, cycle, orang lintas space, dan batas
dua orang tua biologis. Target domain Blueprint juga menyebut `FOSTER` dan `GUARDIAN`,
tetapi tipe tersebut belum tercermin penuh pada entity relationship saat snapshot.

Aturan graph yang sudah dikoreksi dan tidak boleh diregresikan:

- pasangan berada pada generasi lineage yang sama;
- pasangan disusun sebagai satu couple unit;
- garis spouse horizontal;
- konektor kedua orang tua bergabung sebelum menuju anak;
- spouse tidak boleh salah dibaca sebagai parent/child atau mendapat level 0 hanya
  karena tidak mempunyai edge parent-child;
- person yang sama tidak boleh digambar dua kali akibat record duplicate yang tidak
  terhubung;
- representasi graph dan daftar aksesibel harus menunjukkan relasi yang konsisten.

### 6.5 Tabel pendukung backend

Tabel aktif meliputi:

- `users` — akun login;
- `family_spaces` — ruang keluarga;
- `space_members` — membership dan role;
- `space_invitations` — token undangan single-use dan expiry;
- `persons` — profil manusia;
- `relationships` — edge parent-child dan spouse;
- `user_person_claims` — klaim akun ke profil;
- `fact_sources` — sumber/citation fakta;
- `media_items` — metadata/URI media, belum binary storage;
- `edit_proposals` — proposal dengan status review;
- `change_log` — audit perubahan;
- `refresh_sessions` — digest refresh token, rotation/revocation family;
- `client_mutations` — idempotency result untuk mutasi offline-safe.

## 7. Authentication, Session, dan Security

Alur auth saat ini:

1. pengguna register/login dengan email-password pada fondasi development;
2. backend mengeluarkan access token singkat dan opaque refresh token;
3. Android menyimpan access token hanya di memory;
4. refresh token dan user ID disimpan sebagai ciphertext AES-256-GCM;
5. key enkripsi dibuat non-exportable di Android Keystore;
6. refresh token berotasi; replay dapat merevoke satu token family;
7. `activeSpaceId` disimpan di private SharedPreferences;
8. process restart dapat memulihkan session melalui refresh flow;
9. logout merevoke session server dan membersihkan session lokal.

Android system backup dan device-transfer backup dinonaktifkan. HTTP logging hanya
`BASIC` pada debug dan tidak membawa body/header sensitif; release logging dimatikan.
Production wajib HTTPS dan tidak boleh memakai cleartext transport.

`Clear offline data` hanya menghapus cache space aktif. Operasi ditolak bila masih
ada mutation `PENDING`, `FAILED`, atau `CONFLICT` agar perubahan pengguna tidak hilang.

## 8. Android Client dan Penyimpanan Lokal

Android menggunakan Jetpack Compose dan Material 3 dengan struktur target:

```text
Feature Screen → ViewModel → Repository → Room/Remote API
```

Feature utama saat ini mencakup auth, home/onboarding, people, person detail, graph,
activity, dan space settings. Navigation Compose, StateFlow, lifecycle-aware UI,
Room, WorkManager, dan manual application container/constructor injection dipakai
sebagai fondasi.

Room database privat bernama `family-tree.db`, schema version 4, dengan tabel:

- `persons` — subset person untuk cache daftar/detail;
- `relationships` — cache edge `PARENT_CHILD` dan `SPOUSE`;
- `offline_mutations` — antrean mutasi persisten.

Status antrean:

- `PENDING` — menunggu jaringan/worker;
- `SYNCING` — sedang dikirim;
- `FAILED` — gagal dan dapat dicoba ulang;
- `CONFLICT` — server mempunyai versi berbeda dan butuh keputusan pengguna.

Jenis mutasi offline yang sudah ada:

- update life status;
- update birth place/notes profile;
- add parent-child;
- add spouse.

Mutasi memakai UUID `clientMutationId`; update versioned juga membawa
`expectedVersion`. Backend menyimpan hasil untuk replay idempoten. `409 CONFLICT`
mengembalikan snapshot/version server agar UI menawarkan `Keep my change` atau
`Use server version`. WorkManager menangani retry persisten.

Konsekuensi UX/cache:

- setelah sinkronisasi, daftar dan graph dapat tampil dari Room saat offline;
- cache kosong bukan bukti data server hilang;
- jika cache dibersihkan lalu backend tidak hidup, endpoint salah, atau ADB reverse
  tidak aktif, People/Graph dapat terlihat kosong;
- setelah login dan backend terjangkau, repository harus mengambil ulang data server
  dan mengisi Room;
- backend tetap memutuskan validitas final, walaupun client juga melakukan validasi
  ringan dan optimistic update untuk UX.

## 9. Seed Demo dan Lineage yang Benar

Script development `npm run seed:dev` membuat `Keluarga Demo`. Seed bersifat
idempoten dan hanya boleh dijalankan di environment development. Empat profil
memiliki akun quick-login debug; dua kerabat tambahan hanya profil Person.

| Tokoh | Peran keluarga | Role akun | Catatan |
|---|---|---|---|
| Budi Santoso | ayah Raka, suami Siti, anak Hadi | OWNER | akun quick-login Father |
| Siti Aminah | ibu kandung Raka, istri Budi, anak Nur | ADMIN | akun quick-login Mother |
| Raka Santoso | anak kandung Budi dan Siti, suami Alya | EDITOR | akun quick-login First child |
| Hadi Santoso | kakek, ayah kandung Budi | VIEWER | akun quick-login Grandfather |
| Nur Aisyah | ibu kandung Siti | tidak ada akun login | profil kerabat seed |
| Alya Putri | istri Raka | tidak ada akun login | profil kerabat seed, bukan anak/menantu pada lineage vertikal |

Relasi seed yang benar:

```text
Hadi Santoso ──biological parent──> Budi Santoso
Nur Aisyah   ──biological parent──> Siti Aminah

Budi Santoso ══married spouse══════ Siti Aminah
             └──biological parents──> Raka Santoso

Raka Santoso ══married spouse══════ Alya Putri
```

Siti adalah ibu kandung Raka, bukan menantu pada edge lineage. Alya adalah pasangan
Raka dan harus berada pada Generation 2 bersama Raka, bukan Generation 0. Raka hanya
boleh muncul satu kali di export graph. Skenario duplicate tetap diuji di e2e tetapi
record duplicate-test tidak boleh dipersistenkan dalam Family Space demo utama.

Credential seed tidak dicatat di dokumen ini. Gunakan quick-login yang hanya muncul
pada debug build atau periksa konfigurasi development lokal secara aman. Credential
development bukan credential produksi dan tetap tidak boleh dipublikasikan.

## 10. Export, Import, Backup, dan Media

Data portability yang sudah tersedia:

- JSON export/backup `familyroot-backup` schema version 1;
- GEDCOM 5.5.1 export dan import subset yang didukung;
- export pohon PDF;
- export pohon PNG;
- restore transaksional dengan ID remap.

Import GEDCOM dan restore backup sengaja hanya menerima Family Space target yang
kosong. Ini mencegah overwrite, merge tak disengaja, dan duplicate restore.

Backup membawa:

- people;
- relationships;
- sources;
- media metadata/URI references.

Backup tidak membawa:

- binary foto/dokumen;
- password, refresh session, atau akun login lengkap;
- seluruh konteks deployment/backend;
- file media lokal yang hanya direferensikan URI.

Export Android memakai Storage Access Framework sehingga pengguna memilih lokasi.
File GEDCOM/JSON/PDF/PNG yang sudah keluar dari app **tidak otomatis dienkripsi**;
pengguna bertanggung jawab memilih lokasi aman. Rancangan Blueprint v2 perlu
memutuskan object storage, upload binary media, download/offline policy, ownership,
retention, dan backup lintas perangkat.

## 11. Development dan Runbook Pengujian

### 11.1 Backend lokal

Prasyarat: Node/npm sesuai project, `.env` lokal dengan JWT secret development yang
kuat, dan database yang bukan file e2e/disposable test saat menjalankan demo manual.

```powershell
cd backend
npm ci
npm run seed:dev
npm run start:dev
```

Quality gate backend:

```powershell
npm run lint:check
npm run build
npm test -- --runInBand
npm run test:e2e
npm audit --omit=dev --audit-level=high
```

E2E harus menggunakan database disposable melalui `DB_DATABASE`, tidak boleh
mengotori `dev.sqlite`.

### 11.2 Kebijakan perangkat fisik yang wajib

Perangkat referensi:

- Samsung SM-T225;
- Android 14/API 34;
- serial USB `R9RR900LL1V` pada snapshot.

**Semua instalasi APK, instrumentation, logcat, dan smoke test perangkat fisik wajib
melalui USB debugging. Wireless debugging/Wireless ADB tidak boleh diaktifkan atau
digunakan lagi.**

Backend laptop diekspos ke tablet melalui USB reverse:

```powershell
adb devices -l
adb -s R9RR900LL1V reverse tcp:3001 tcp:3001
adb -s R9RR900LL1V reverse --list
```

Debug APK default memakai `http://127.0.0.1:3001/`. Karena `127.0.0.1` di tablet
menunjuk tablet itu sendiri, koneksi hanya berhasil bila `adb reverse` aktif dan
backend laptop hidup pada port 3001.

Emulator harus memilih endpoint secara eksplisit:

```powershell
cd android-client
.\gradlew.bat assembleDebug -PfamilyTreeApiBaseUrl=http://10.0.2.2:3001/
```

Jangan mengembalikan default debug ke `10.0.2.2`; keputusan terakhir menetapkan
localhost + ADB reverse sebagai default untuk keamanan dan konsistensi test tablet.

### 11.3 Android build dan test

```powershell
cd android-client
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
.\gradlew.bat connectedDebugAndroidTest
adb -s R9RR900LL1V install -r app\build\outputs\apk\debug\app-debug.apk
```

Sebelum connected test, pastikan hanya transport USB yang relevan terlihat di ADB.
Sesudah build/install, lakukan smoke test minimal:

1. backend `/health` merespons;
2. ADB reverse port 3001 aktif;
3. quick-login Father berhasil tanpa timeout;
4. `Keluarga Demo` dapat dipilih/dipulihkan;
5. People menampilkan enam profil seed;
6. Graph menunjukkan lineage dan pasangan dengan benar;
7. kembali dari Space Settings tidak mengosongkan graph;
8. force-stop/cold-start memulihkan session;
9. tidak ada `AndroidRuntime` crash di logcat;
10. bila perubahan menyentuh export, periksa file nyata secara visual/format.

## 12. CI, Release, dan Budget yang Sudah Ada

Workflow `CI` menjalankan backend install/audit/lint/build/unit/e2e serta Android
lint, unit, debug APK, dan release bundle. Workflow `Android release` mengambil URL
API dan signing material dari GitHub Actions secrets, memverifikasi signature, dan
membuat checksum SHA-256. Store publication tetap tindakan manusia.

Budget Phase 4:

| Journey | Gate |
|---|---:|
| Pilih initial graph focus untuk 10.000 person/9.999 relationship | ≤ 1.500 ms |
| Render PNG 60 person pada SM-T225 | ≤ 8.000 ms |
| Release Android App Bundle | ≤ 25 MiB |
| Cold app launch pada SM-T225 | ≤ 5.000 ms |

Hasil referensi penutupan Phase 4: graph focus sekitar 260 ms, PNG 60 anggota sekitar
403 ms, cold launch sekitar 3,8 detik, dan minified AAB sekitar 2,76 MB. Angka ini
snapshot pengujian, bukan jaminan untuk semua perangkat.

Production release masih membutuhkan secret/infrastruktur eksternal yang tidak boleh
masuk Git: API URL HTTPS, signing keystore/password/alias, random `JWT_SECRET`,
database persistence/backup, TLS termination, dan allowed browser origins bila web
client dibuat.

## 13. Kondisi Git dan Aturan Perubahan

Pada awal pembuatan dokumen ini:

- branch lokal `main` sama dengan `origin/main`;
- HEAD dan remote menunjuk commit `b8679d3`;
- worktree bersih sebelum `PROJECT_MEMORY.md` dibuat;
- commit `b8679d3` mencakup penyelesaian Phase 4 dan koreksi debug endpoint USB.

Dokumen ini dan implementasi frontend Tahap 1–5 kemudian disiapkan dalam satu
worktree. Pada 19 Juli 2026 pengguna memberi instruksi eksplisit untuk merapikan,
commit, dan push perubahan tersebut.

Aturan kerja repository:

- pertahankan perubahan milik pengguna yang tidak terkait;
- selalu cek status sebelum edit, commit, pull, atau push;
- jangan memasukkan `.env`, database, credential, key, token, pairing code, APK/AAB,
  atau file export keluarga;
- gunakan pesan commit yang menjelaskan outcome;
- sesudah push, bandingkan hash lokal dan remote serta pastikan worktree bersih;
- dokumentasi roadmap/progress harus diperbarui setelah pekerjaan benar-benar diuji,
  bukan hanya setelah kode ditulis.

## 14. Known Gaps dan Utang Dokumentasi

Hal berikut diketahui pada snapshot dan belum boleh disalahartikan sebagai pekerjaan
yang sudah selesai:

- backend pilot sudah di-host di Cloud Run, tetapi platform production final, SLA,
  observability, dan disaster-recovery operation belum ditetapkan;
- database/storage pilot tersedia di Supabase Free dan migration awal sudah
  diterapkan, tetapi automated backup/PITR production belum tersedia;
- binary media upload pilot sudah tersedia melalui private Supabase Storage, tetapi
  lifecycle, retention, portability provider, dan kebijakan data nyata belum ditutup;
- field-level privacy untuk orang hidup dan model provenance penuh masih target;
- beberapa target domain Blueprint lebih kaya dari entity aktual;
- Blueprint v1 menyatakan empat fase selesai, tetapi kualitas UI/UX produk masih akan
  dirumuskan ulang dalam Blueprint v2;
- `android-client/ARCHITECTURE.md` masih memiliki kalimat default networking yang
  dapat terbaca sebagai default emulator, sedangkan keputusan implementasi terbaru
  adalah debug default `127.0.0.1` untuk USB reverse;
- beberapa baris Development Log Blueprint masih mengatakan “belum commit/push”
  walaupun seluruh Phase 4 kemudian sudah di-commit dan dipush pada `b8679d3`;
- dokumen historis tidak boleh mengalahkan source code dan keputusan terbaru bila
  keduanya sudah berubah; setiap ketidaksesuaian harus dilaporkan lalu diperbaiki
  secara eksplisit setelah disetujui.

## 15. Agenda Diskusi Blueprint v2

> **Update 19 Juli 2026:** Arah pilot cloud telah diputuskan dalam
> `docs/CLOUD_PILOT_DECISION.md`. Pembahasan baseline frontend graph workspace,
> inspector, visual language, privasi, kolaborasi, invitation, media, dan data
> lifecycle telah ditutup dalam `docs/FRONTEND_GRAPH_WORKSPACE_DECISION.md`.
> Implementasi frontend telah mencapai Tahap 5: visual graph dan inspector, shell
> adaptif, pencarian global lokal, serta jalur hubungan hybrid berbasis teks dengan
> aksi opt-in untuk menyorot dan menambahkan card minimum lintas generasi di graph.
> Sesudah Tahap 5, polish prafase berikutnya menambahkan halaman Profil Akun yang
> terpisah dari profil person, mengubah Keluarga menjadi direktori visual dengan
> statistik/filter/avatar fallback, dan mengubah Aktivitas menjadi timeline
> kolaboratif yang menerjemahkan kode audit backend ke bahasa pengguna. Header
> Keluarga dan Aktivitas juga memiliki mode ringkas untuk viewport ponsel 360dp,
> diverifikasi pada vivo 1816 tanpa memotong daftar atau timeline yang dapat digulir.
> Ringkasan header Aktivitas dapat di-collapse menjadi strip pendek sehingga timeline
> memperoleh tinggi maksimum, dengan kontrol eksplisit untuk membukanya kembali.
> Pada viewport ponsel, halaman Keluarga memakai satu `LazyColumn` untuk seluruh
> halaman; swipe dari header, form, kontrol direktori, maupun card menggerakkan alur
> yang sama dan tidak lagi bergantung pada nested list berukuran kecil.
> Halaman Profil Person lengkap yang dibuka dari inspector juga telah dimigrasikan
> dari artefak UI lama ke shell dan bahasa visual frontend saat ini. Halaman memakai
> hero person yang adaptif, satu alur scroll, serta section collapsible untuk data
> utama, sinkronisasi, sumber keluarga, tautan kenangan, usulan kolaboratif, dan
> hubungan keluarga. Seluruh aksi lama tetap tersedia, tetapi target hubungan
> ditampilkan berbasis pencarian agar ringan. Sumber hanya berupa referensi teks dan
> kenangan berupa tautan eksternal; aplikasi tidak menyediakan unggahan dokumen formal.
> Tombol kembali dari profil lengkap mempertahankan state graph dan inspector asal.
> Implementasi ini diverifikasi manual pada vivo 1816 (360dp) dan Samsung SM-T225,
> serta lulus unit test, lint, dan assemble debug.
> Audit lanjutan menemukan empat screen generasi awal dan semuanya telah dimigrasikan:
> Pengaturan Family Space kini berada di dalam shell dengan section collapsible dan
> istilah Indonesia; Login/Daftar serta Pemilihan Family Space memakai ViewModel,
> immutable UI state, layout adaptif, satu alur scroll, dan error yang tidak membocorkan
> endpoint teknis; Beranda menjadi ringkasan berbasis data nyata untuk jumlah person,
> status hidup, kontributor, antrean sync, kelengkapan profil, dan aktivitas terbaru.
> Logout dari protected route juga dibuat aman terhadap frame transisi ketika
> `spaceId` telah dibersihkan. Alur logout → Login → akun demo → pemilihan Keluarga
> Demo → graph diverifikasi end-to-end pada vivo 1816. Seluruh 18 connected
> instrumentation tests kembali lulus pada Samsung SM-T225, bersama unit test, lint,
> dan assemble debug. APK final dipasang pada kedua perangkat dengan USB reverse
> `tcp:3001` aktif.
> Setelah penutupan Tahap 5, Tahap 6 disepakati sebagai pengembangan kompleksitas
> lineage: recursive expand/collapse dari setiap node, progressive multi-generation,
> multiple historical partnership, cabang keluarga pasangan, penjagaan satu identitas
> person, state workspace, collision avoidance, serta regression/performance test.
> Keputusan privasi berikutnya menetapkan keluarga asal pasangan sebaiknya berada
> dalam Family Space terpisah. Istri B dapat menjadi anggota Family Space Keluarga B
> dan Family Space Keluarga Asal Istri B, sedangkan A tidak memperoleh akses lineage
> keluarga istri B hanya karena ia kakak kandung B. Bahkan B tidak otomatis memperoleh
> membership ruang keluarga asal istrinya. Relationship graph tidak pernah menjadi
> ACL; invitation, scope, pencarian, path, cache, dan export tetap terisolasi per
> Family Space. Model sekarang mendukung satu User pada banyak space, tetapi Person
> dan claim tetap per-space serta belum mempunyai identitas/sinkronisasi lintas-space.
> Tahap 6 tidak menghubungkan Family Space terpisah atau memperluas izin otomatis.
> Tahap 7 baru menyentuh backend dan server sesuai
> `docs/CLOUD_PILOT_DECISION.md`: NestJS di Cloud Run dan PostgreSQL/private storage
> Supabase untuk pilot, dengan setiap perubahan kontrak atau cross-space identity
> memerlukan keputusan tersendiri.
> Baseline Tahap 6.1 kemudian diterapkan hanya pada frontend Android: setiap card
> non-pusat dapat membuka orang tua atau keluarga anak secara progresif berdasarkan
> relationship yang sudah ada dalam Family Space aktif; co-parent tercatat ikut
> terlihat, person tidak diduplikasi, collapse tidak mengganti center, state ekspansi
> saveable, collision dihindari, dan viewport menjaga posisi center. Relationship yang
> tidak diterima dari ruang aktif tidak menghasilkan arrow, placeholder, maupun
> inference. Unit test, lint, assemble debug, serta seluruh 19 instrumentation test
> lulus pada Samsung SM-T225 Android 14. Status tersebut menyelesaikan baseline 6.1.
> Pada 20 Juli 2026, sisa Tahap 6 diselesaikan dan Tahap 6 resmi CLOSED. Frontend
> Android sekarang mendukung partnership historis yang diurutkan kronologis,
> pembukaan partnership/orang tua/keluarga anak secara progresif, status
> divorced/widowed, pengelompokan anak berdasarkan parentage tercatat, pembeda
> biological/adoptive/step, satu card per person ID, placement deterministik dengan
> spatial collision avoidance, state collapse/restore, dan inspector riwayat
> partnership. Tidak ada kontrak backend, database, deployment, ACL, atau batas
> Family Space yang diubah.
> Quality gate penutupan Tahap 6 mencatat 45 unit test, lint tanpa error (121 warning
> non-fatal, terutama 106 `UnusedResources`),
> assemble debug dan androidTest, serta seluruh 20 connected instrumentation test
> lulus pada Samsung SM-T225 Android 14 melalui USB. Planner dan placement diuji
> terhadap 10.000 person/9.999 relationship dengan budget masing-masing 1.500 ms.
> Smoke test backend sehat, ADB reverse `tcp:3001` aktif, APK berhasil dipasang,
> cold launch `MainActivity` 4.918 ms, dan tidak ada `AndroidRuntime` crash. Bila
> instrumentation tiba-tiba mati dengan `SIGKILL`, periksa `Developer options >
> Select debug app`; FamilyRoot yang masih dipilih sebagai debug app terbukti memicu
> force-stop `set debug app` pada perangkat referensi.
> Tahap 7 dimulai pada 20 Juli 2026 sesuai `docs/CLOUD_PILOT_DECISION.md`. Repository
> kini mempunyai konfigurasi PostgreSQL Supabase dengan SSL dan pool kecil, migration
> awal eksplisit, Dockerfile production Node.js 22 untuk Cloud Run, validasi environment,
> soft-delete timestamp, serta adapter private storage. Upload foto dibatasi 2 MB,
> divalidasi melalui magic bytes, di-re-encode untuk kompresi/penghapusan EXIF, dan
> hanya dibaca melalui signed URL singkat setelah pemeriksaan role. Development lokal
> tetap SQLite dan backend tetap NestJS; tidak ada rewrite Kotlin. Unit/e2e/build/lint
> lokal lulus, tetapi Docker tidak tersedia di PC. Provisioning Supabase/GCP,
> migration cloud, deployment, dan acceptance test lintas perangkat masih wajib
> sebelum Tahap 7 dapat ditutup; panduannya berada di
> `docs/STEP7_CLOUD_PILOT_RUNBOOK.md`.
> Checkpoint Supabase pada hari yang sama kemudian lulus: migration PostgreSQL awal
> diterapkan dengan RLS pada 13 tabel, seed cloud menghasilkan satu Family Space dan
> enam profil, login/read API berhasil, serta private bucket `family-media` tervalidasi
> pada limit 2 MB dan MIME JPEG/PNG/WebP. Upload PNG dummy, pencatatan metadata,
> signed URL 60 detik, dan download HTTP 200 juga berhasil. Seed lama yang semula
> selalu menargetkan SQLite diperbaiki agar mengikuti `DATABASE_URL` tanpa pernah
> mengaktifkan synchronize pada PostgreSQL.
> Cloud Run kemudian berhasil dideploy di region Singapore melalui continuous
> deployment GitHub. GitHub Actions memvalidasi backend, production container, dan
> Android. Acceptance remote membuktikan health HTTPS, proteksi JWT, login/refresh/
> logout, dua session membaca enam profil yang sama, private media upload/signed read,
> expiry signed URL setelah 70 detik, serta backup dan GEDCOM cloud. APK debug
> ber-endpoint Cloud Run juga lulus unit test,
> lint, assemble, install/cold-launch tanpa crash, dan seluruh 20 connected
> instrumentation test pada Samsung SM-T225 Android 14. Tahap 7 belum CLOSED karena
> smoke test data cloud pada dua perangkat, mutation
> queue offline-online beserta conflict/idempotent retry, persistensi setelah
> scale-to-zero/restart, penyimpanan file backup oleh pemilik, audit log/secret, serta
> billing dan usage review masih wajib.
> Acceptance offline pertama pada Samsung menemukan workspace graph menampilkan
> snapshot seed lama setelah worker sebenarnya sukses melakukan PATCH 200; PostgreSQL
> sudah menyimpan catatan dan menaikkan version. Android diperbaiki agar graph
> mengamati Room dan `listPersons` mengembalikan hasil lokal setelah reapply queue.
> Regression/unit test, lint, assemble, dan 20 instrumentation test lulus. Retest
> manual offline-online kemudian berhasil mempertahankan teks tambahan. Halaman profil
> juga mendapat pull-to-refresh dengan indikator agar data dapat dimuat ulang tanpa
> menutup halaman; build dan 20 instrumentation test kembali lulus, sedangkan gesture
> pada APK terbaru menunggu konfirmasi visual pemilik.
> Karena perangkat kedua tidak tersedia, acceptance kolaborasi backend dilanjutkan
> dengan smoke dua session reusable. Test membuktikan snapshot enam profil identik,
> stale-version conflict 409, rebase/retry, idempotency tanpa kenaikan version ganda,
> visibilitas lintas session, pemulihan profil dummy, dan logout kedua session.
> Audit juga menemukan E2E lama pernah memuat `.env` cloud: akun dummy
> `owner@example.test` mempunyai empat Family Space test. `ConfigModule` kini
> mengabaikan `.env` saat `NODE_ENV=test`, dan 8/8 E2E lulus dengan SQLite `:memory:`.
> Artefak dummy lama belum dihapus karena cleanup destruktif menunggu persetujuan.
> Handoff PC pengembangan kemudian distandardisasi melalui `docs/NEW_PC_HANDOFF.md`.
> Keyword `familyroot` diatur oleh root `AGENTS.md` untuk meminta Codex membaca
> `PROJECT_MEMORY.md` seluruhnya, memeriksa Git, mengonfirmasi konteks, dan menunggu
> izin sebelum perubahan. Akun dan Family Space demo berada dalam manifest ter-track
> `backend/src/dev/demo-data.ts`, dipulihkan oleh `backend/src/dev/seed-dev.ts`, dan
> dijelaskan dalam `backend/DEMO_DATA.md`; database runtime
> `backend/dev.sqlite` tetap dilarang masuk Git karena selain demo dapat mengandung
> akun lokal, refresh session, mutation/e2e data, dan log operasional.
> Daftar di bawah tetap berguna sebagai konteks awal, tetapi keputusan terbaru dalam
> kedua risalah tersebut mengalahkan item agenda yang sudah diselesaikan.

Pembahasan berikutnya sebaiknya dimulai dari produk dan pengguna, bukan langsung dari
kode. Topik utama:

1. siapa pengguna primer: pencatat keluarga, anggota biasa, orang tua/lansia, atau
   admin keluarga;
2. momen inti: membuat keluarga, menambah kerabat, memahami hubungan, mengabadikan
   cerita, mengoreksi data, atau mengenang orang;
3. information architecture dan navigasi utama;
4. onboarding tanpa istilah teknis;
5. bentuk Home yang memberi nilai, bukan hanya menu;
6. UX People, pencarian, filtering, dan detail orang;
7. UX graph: focus person, pan/zoom, expand/collapse, couple unit, multiple marriages,
   adopsi/step/foster/guardian, dan alternatif aksesibel;
8. contribution flow, proposal/review, source/citation, dan trust indicator;
9. privacy orang hidup dan kontrol visibilitas;
10. media story/archive, binary storage, dan portability;
11. offline expectations pada penggunaan nyata;
12. backend hosting, production database, ownership, backup, dan multi-device sync;
13. design system, typography, warna, iconography, dark mode, tablet-first/adaptive;
14. prioritas MVP v2 versus enhancement lanjutan;
15. metrik keberhasilan produk yang tidak mengorbankan privasi.

Jangan membuat asumsi pasangan, parenthood, nama, gender, atau struktur keluarga
sebagai aturan tersembunyi. Keputusan sensitif harus eksplisit di level produk dan
tetap kompatibel dengan keluarga Indonesia yang beragam.

## 16. Dokumen dan Source of Truth

Gunakan urutan otoritas berikut:

1. `PROJECT_BLUEPRINT.md` — visi, prinsip, roadmap v1, dan progress tracker kanonik;
2. `PROJECT_MEMORY.md` — snapshot implementasi, keputusan kerja, dan handoff sesi;
3. `docs/FRONTEND_GRAPH_WORKSPACE_DECISION.md` — baseline produk frontend dan
   tata kelola data yang telah disepakati untuk arah Blueprint v2;
4. `docs/CLOUD_PILOT_DECISION.md` — arah pilot Cloud Run + Supabase tanpa mengganti
   arsitektur NestJS;
5. `android-client/ARCHITECTURE.md` — keputusan arsitektur Android;
6. `backend/API_CONTRACT.md` — kontrak endpoint, role, error, concurrency, portability;
7. `docs/PHASE4_PRODUCTION_REVIEW.md` — privacy/security/performance/release review;
8. `android-client/README.md` dan `backend/README.md` — runbook ringkas;
9. source code dan automated tests — bukti implementasi aktual;
10. Git history — bukti waktu dan commit perubahan.

File implementasi penting untuk verifikasi:

- `backend/src/app.module.ts` — database config dan synchronize policy;
- `backend/src/dev/seed-dev.ts` — seed demo dan lineage;
- `backend/src/persons/person.entity.ts` — model Person;
- `backend/src/persons/relationship.entity.ts` — model edge silsilah;
- `android-client/app/build.gradle` — endpoint build dan release configuration;
- `android-client/app/src/main/java/com/example/familytreeplatform/SessionStore.kt` —
  state session dan active space;
- `android-client/app/src/main/java/com/example/familytreeplatform/SecureSessionStorage.kt`
  — enkripsi refresh session;
- `android-client/app/src/main/java/com/example/familytreeplatform/data/local/` — Room
  entities, DAO, database, dan migrations;
- `.github/workflows/ci.yml` dan `.github/workflows/release.yml` — quality/release gate.

## 17. Checklist Agen Saat Keyword Dipanggil

Saat menerima `FAMILYROOT-MEMORY`, agen harus mengonfirmasi hal berikut sebelum bekerja:

- memahami tujuan FamilyRoot sebagai arsip keluarga privat, bukan graph viewer saja;
- memahami User ≠ Person dan Family Space sebagai tenant boundary;
- memahami backend = source of truth, Room = cache/queue;
- memahami database lokal tidak ikut Git;
- memahami Phase 1–4 sudah selesai dan belum ada Phase 5;
- memahami Blueprint v2 akan berfokus pada diskusi produk/UI/UX dan tetap terkait v1;
- membaca risalah cloud dan frontend terbaru sebelum membuka kembali keputusan yang
  telah ditutup;
- memahami lineage seed yang benar, khususnya Siti sebagai ibu kandung Raka dan Alya
  sebagai istri Raka di generasi yang sama;
- memahami testing tablet hanya via USB + ADB reverse;
- memahami tidak boleh commit/push atau mengubah blueprint tanpa instruksi pengguna;
- memeriksa keadaan repo aktual karena snapshot ini dapat menjadi usang setelah
  pekerjaan berikutnya.

Jika semua sudah diverifikasi, agen cukup mengatakan konteks telah dipulihkan,
menyebut commit/status aktual, merangkum perbedaan dari snapshot bila ada, lalu
melanjutkan diskusi atau pekerjaan yang diminta pengguna.
