# FamilyRoot — Project Memory dan Session Handoff

> **Status:** Memori operasional aktif untuk melanjutkan pekerjaan lintas sesi chat
> **Snapshot diperbarui:** 3 Agustus 2026, Asia/Jakarta
> **Repository:** `asadara/FamilyRoot`
> **Branch:** `main`
> **Baseline audit terbaru:** `2b6726dfd97158d962751645457270dff548e294` (`Merge PR #2: fix duplicate claims and account avatar sync`)
> **Baseline sebelum implementasi frontend v2:** `b8679d3ca772cf3786a381e0716fedc4906f24da` (`feat: complete phase 4 data sustainability`)
> **Baseline implementasi sebelum audit aktif:** `a79bbae04f872fb150a3eeb4cab7a3b1c549c128` (`fix: keep splash logo within safe area`)
> **Audit/backlog aktif:** `docs/PROJECT_GAP_AUDIT_2026-07-24.md`
> **Keyword pemulihan konteks:** `FAMILYROOT-MEMORY`
> **Tidak menggantikan:** `PROJECT_BLUEPRINT.md`; dokumen ini merangkum keadaan implementasi dan keputusan kerja agar sesi baru dapat melanjutkan konteks dengan benar.

## 1. Cara Melanjutkan di Sesi Chat Baru

Di sesi baru, buka workspace repository ini lalu kirim instruksi berikut:

```text
FAMILYROOT-MEMORY — baca PROJECT_MEMORY.md seluruhnya, lalu
docs/PROJECT_GAP_AUDIT_2026-07-24.md, PROJECT_BLUEPRINT.md,
android-client/ARCHITECTURE.md, dan docs/PHASE4_PRODUCTION_REVIEW.md. Periksa git
status dan commit terbaru. Ringkas pemahaman, backlog aktif, serta kondisi aktual
sebelum melakukan perubahan apa pun. Jangan gunakan Wireless ADB; pengujian tablet
hanya melalui USB.
```

Keyword tidak menyimpan memori secara magis di luar repository. Fungsinya adalah
perintah singkat yang mengarahkan sesi baru ke dokumen kanonik yang benar. Agen yang
menerima keyword wajib membaca file ini dari disk, bukan mengandalkan ingatan chat.

Urutan pemulihan yang aman:

1. baca `PROJECT_MEMORY.md` sampai selesai;
2. baca audit/backlog aktif dan dokumen sumber sesuai daftar pada bagian 16;
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

Backend pilot sudah dideploy di Cloud Run dengan PostgreSQL dan private storage
Supabase. Ini belum berarti platform production final: domain/SLA, observability,
backup/PITR, disaster recovery, retention, dan operasi production masih belum
ditutup. Android release pipeline dapat menerima URL HTTPS dan signing material
melalui CI secrets, tetapi kesiapan build Android tidak sama dengan kesiapan
infrastruktur production.

Risiko skema yang perlu dibahas sebelum production:

- entity TypeORM saat ini banyak menyimpan relasi sebagai kolom UUID tanpa deklarasi
  foreign-key relation TypeORM eksplisit;
- cross-space dan referential integrity ditegakkan oleh service, transaction, dan test;
- production memakai `synchronize: false` dan repository mempunyai migration
  PostgreSQL awal serta migration evolusi. Prosedur rollout, rollback, dan bukti
  migration production tetap harus dipelihara;
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
dua orang tua biologis. `FOSTER` dan `GUARDIAN` sudah tersedia sebagai care overlay
non-lineage dengan periode/konteks; keduanya tidak mengubah generation, parentage,
partnership, legalitas, atau ACL secara implisit.

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
- `user_google_identities` — Google subject satu-ke-satu untuk akun login;
- `family_spaces` — ruang keluarga;
- `space_members` — membership dan role;
- `space_invitations` — token undangan single-use dan expiry;
- `persons` — profil manusia;
- `relationships` — edge parent-child dan spouse;
- `user_person_claims` — klaim akun ke profil;
- `fact_sources` — sumber/citation fakta;
- `media_items` — metadata/URI media; foto profil pilot disimpan sebagai object
  privat di Supabase Storage dan diakses melalui signed URL;
- `edit_proposals` — proposal dengan status review;
- `change_log` — audit perubahan;
- `refresh_sessions` — digest refresh token, rotation/revocation family;
- `client_mutations` — idempotency result untuk mutasi offline-safe.

## 7. Authentication, Session, dan Security

Alur auth saat ini:

1. pengguna dapat register/login dengan email-password atau Google Sign-In;
2. Google ID token hanya ditukar pada backend setelah signature, audience, issuer,
   expiry, email verification, dan subject diverifikasi; backend lalu mengeluarkan
   access token singkat dan opaque refresh token;
3. Android menyimpan access token hanya di memory;
4. refresh token dan user ID disimpan sebagai ciphertext AES-256-GCM;
5. key enkripsi dibuat non-exportable di Android Keystore;
6. refresh token berotasi; replay dapat merevoke satu token family;
7. `activeSpaceId` disimpan di private SharedPreferences;
8. process restart dapat memulihkan session melalui refresh flow;
9. logout merevoke session server, membersihkan state Credential Manager, dan
   membersihkan session lokal.

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

Room database privat bernama `family-tree.db`, schema version 8, dengan tabel:

- `persons` — subset person untuk cache daftar/detail;
- `relationships` — cache edge `PARENT_CHILD` dan `SPOUSE`;
- `sources` — cache sumber teks yang privacy-aware;
- `offline_mutations` — antrean mutasi persisten.

Status antrean:

- `PENDING` — menunggu jaringan/worker;
- `SYNCING` — sedang dikirim;
- `FAILED` — gagal dan dapat dicoba ulang;
- `CONFLICT` — server mempunyai versi berbeda dan butuh keputusan pengguna.

Jenis mutasi offline yang sudah ada:

- `CREATE_PERSON`;
- `UPDATE_LIFE_STATUS`;
- `UPDATE_PROFILE`;
- `ADD_PARENT_CHILD`;
- `ADD_SPOUSE`;
- `UPDATE_SPOUSE`;
- `DELETE_RELATIONSHIP`;
- `CREATE_SOURCE`.

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

Pada baseline audit ulang 3 Agustus 2026:

- `git pull --ff-only origin main` memperbarui lokal dari `102e105` ke `2b6726d`;
- branch lokal `main` sama dengan `origin/main` dan tidak mempunyai commit lokal
  tambahan;
- worktree bersih sebelum pembaruan dokumentasi audit ini dimulai;
- empat commit yang ditarik mencakup merge PR #1 dan PR #2 untuk akun, aktivitas,
  foto/avatar, history access, Google build configuration, dan hardening claim.

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

> **Update audit 24 Juli 2026:** status gap dan urutan backlog terbaru berada di
> `docs/PROJECT_GAP_AUDIT_2026-07-24.md`. Dokumen audit tersebut memisahkan pekerjaan
> aktif, implementasi parsial, verifikasi eksternal, rencana deferred, dan keputusan
> yang telah menggantikan rencana lama. Bila ringkasan historis di bawah berbeda dari
> source atau audit terbaru, periksa Git history lalu gunakan keputusan paling baru.

Hal berikut diketahui pada snapshot dan belum boleh disalahartikan sebagai pekerjaan
yang sudah selesai:

- backend pilot sudah di-host di Cloud Run, tetapi platform production final, SLA,
  observability, dan disaster-recovery operation belum ditetapkan;
- database/storage pilot tersedia di Supabase Free dan migration awal sudah
  diterapkan, tetapi automated backup/PITR production belum tersedia;
- binary media upload pilot sudah tersedia melalui private Supabase Storage, tetapi
  lifecycle, retention, portability provider, dan kebijakan data nyata belum ditutup;
- privacy per Person sudah tersedia pada pilot, tetapi field-level/branch scope,
  delegasi pengelola, versi alternatif, dispute, dan provenance penuh masih target;
- beberapa target domain Blueprint lebih kaya dari entity aktual;
- Blueprint v1 menyatakan empat fase selesai, tetapi kualitas UI/UX produk masih akan
  dirumuskan ulang dalam Blueprint v2;
- beberapa baris historis Development Log Blueprint tetap merekam keadaan
  “belum commit/push” pada saat event terjadi; baris itu bukan status Git saat ini;
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
> Pada 20 Juli 2026 identitas produk resmi diputuskan menjadi **TRêdhAH** dengan
> tagline **“Merangkai jejak, menyatukan trah”**. Kapitalisasi tersebut wajib karena
> huruf kapital membentuk `TRAH`, sementara `dh` mempertahankan pembedaan bunyi Jawa.
> Nama `FamilyRoot` tetap menjadi nama teknis internal sampai ada migrasi terpisah.
> Android kini memakai logo `images/main_logo.png` sebagai sumber launcher icon,
> splash, autentikasi, dan header; palet berubah menjadi emas-gading-cokelat, serta
> istilah pengguna `Family Space` diganti menjadi `silsilah` tanpa mengubah tenant/API.
> Unit test 46/46, lint, assemble debug, install/cold launch, pemeriksaan visual header,
> dan 20/20 connected instrumentation test lulus pada Samsung SM-T225 Android 14.
> Koreksi visual berikutnya memakai kanvas transparan 288 dp dengan tanda utama
> sekitar 128 dp agar logo penuh aman pada mask splash Android/Samsung dan aksara
> Jawa tidak terpotong; logo launcher/header tetap memakai aset penuh.
> Pada 21 Juli 2026 layout graph diperketat: partnership yang terlihat diposisikan
> sebagai unit atomik untuk cabang saudara, keturunan, dan leluhur; satu person tidak
> diduplikasi pada multiple partnership. Parentage tetap sepenuhnya eksplisit:
> co-parent dengan partnership dan tipe sama memakai junction cincin, co-parent tanpa
> partnership memakai hub netral, sedangkan single parent memakai anchor card serta
> slot visual orang tua belum tercatat tanpa membuat record `Person` dummy. Pasangan
> aktif tidak diinferensikan sebagai parent, dan anak partnership lama tetap pada
> cabang asal. Kontrol arah kosong memakai `+` hanya bagi role yang boleh mengubah;
> `VIEWER` melihat status terkunci. Export visual memakai snapshot layout workspace
> yang sama. Keputusan rinci berada di bagian 31.7 risalah frontend.
> Koreksi export berikutnya membawa state visual kartu yang sama (nama akrab, avatar
> gender, status hidup/meninggal, dan umur) serta merender cincin untuk partnership
> atomik dan progresif/historis. Field ambigu `Tanggal mulai` pada quick-add pasangan
> diganti menjadi `Tanggal mulai hubungan`: ini bukan tanggal lahir, dipilih melalui
> date picker, disimpan sebagai ISO `YYYY-MM-DD`, dan ditampilkan dalam format
> Indonesia seperti `01 Januari 1990`.
> Menu Alat kemudian diseragamkan untuk layout bottom navigation dan rail. Ia memuat
> export/reset pada Pohon, halaman Tentang aplikasi, halaman Petunjuk penggunaan,
> label `Beta 0.1.0`, dan footer `© sadar@studio 2026`. About/Help memakai back
> stack terpisah agar workspace graph tidak direset. Tinggi bottom navigation menjadi
> 64 dp dan inset bawah ganda dihapus karena scaffold aktivitas sudah menangani
> system bars. Keputusan rinci dicatat pada bagian 31.8 risalah frontend.
> Quality gate gabungan layout atomik, parity export, date picker, dan menu pendukung
> lulus: unit test, lintDebug, assembleDebug/androidTest, serta seluruh 31 connected
> instrumentation test pada Samsung SM-T225 Android 14.
> Pada 28 Juli 2026 baseline penghapusan person yang aman diimplementasikan.
> `OWNER/ADMIN` dapat menghapus person bersih setelah impact check;
> `EDITOR` hanya dapat mengirim permintaan dengan alasan; `VIEWER` tidak memperoleh
> aksi. Relationship, claim, media, source, proposal tertunda, dan mutation lokal
> menjadi blocker dan tidak pernah dihapus diam-diam. Persetujuan proposal dan
> soft-delete berjalan dalam satu transaksi, audit dipertahankan, serta cache Room/foto
> baru dibersihkan setelah server berhasil. Kontrak berada di
> `backend/API_CONTRACT.md` dan keputusan rinci di bagian 31.9 risalah frontend.
> Quality gate lokal lulus untuk lint/build/unit/e2e backend serta unit test,
> `lintDebug`, dan `assemblePilot` Android. Migration/backend baru belum dideploy dan
> APK pilot baru belum dipasang ke tablet pada snapshot ini agar build stabil yang
> sedang dipakai pengguna tidak tergantikan tanpa persetujuan.
> Pada 28 Juli 2026 gate kompatibilitas APK/backend ditambahkan sebagai perubahan
> berikutnya. Backend menyimpan policy per `DEBUG/PILOT/PRODUCTION`, membedakan
> `versionCode` dari `apiContractVersion`, dan hanya menerima perubahan policy dari
> user ID pada `SYSTEM_ADMIN_USER_IDS`, dengan audit terpisah. Android memeriksa
> sebelum restore session dan saat resume. Selama enforcement nonaktif, update,
> mismatch versi/kontrak, atau first-check yang tidak dapat diverifikasi memberi
> warning yang dapat dilanjutkan sementara; hanya policy enforcement aktif yang
> membuat inkompatibilitas menjadi hard block. Cache maksimum 24 jam hanya berlaku
> untuk build, contract, dan channel yang sama. Migration/backend telah dideploy dan
> APK hasilnya telah dipasang ke Samsung SM-T225 melalui USB. Gate pertama
> memakai `versionCode 2`, `versionName 0.1.1-beta`; seluruh request membawa header
> build/contract/channel. Enforcement backend default `false` agar APK tablet build 1
> tidak terkunci sebelum rollout, lalu dapat diaktifkan untuk menghasilkan
> `426 UPGRADE_REQUIRED` pada client legacy atau inkompatibel. Quality gate lokal
> lulus: backend lint/build, 18 unit test, 14 e2e test; Android unit test,
> `lintDebug`, dan `assemblePilot`. Policy PILOT saat rollout menerima build 1–2
> dengan contract 1 dan enforcement tetap `false`; enforcement hanya boleh menjadi
> gerbang penutup setelah fase pengembangan dinyatakan selesai.
> Pada 29 Juli 2026 policy PILOT menerima build 1–3 dengan contract 1 dan
> enforcement tetap `false`. Cloud Run `SYSTEM_ADMIN_USER_IDS` telah dikoreksi agar
> hanya berisi UUID satu akun Gmail aktif milik pemilik aplikasi. Identitas aktual
> tidak disimpan di Git. Revision `familyroot-api-pilot-00024-fgv` aktif 100%, akses
> `PUT /app-compatibility/android/policy` berhasil tanpa `403`, dan audit mencatat
> aktor yang sama. Perubahan operasional ini tidak memerlukan rebuild APK.
> P2 lifecycle kini mencakup membership, transfer ownership, leave, revoke invitation,
> hapus akun tanpa menghapus Person/riwayat keluarga, serta arsip read-only dan
> soft-delete Family Space dengan ringkasan dampak dan konfirmasi aman. Unique index
> menjaga satu pemilik per silsilah; mutation lokal menghalangi tindakan berisiko;
> Android membersihkan cache saat akses hilang dan memeriksa revocation saat resume
> serta setiap 60 detik. P2 ditutup `DONE` pada source lokal.
> P3 tetap `PARTIAL`, tetapi pilot privacy per person sudah end-to-end:
> `FAMILY`/`LIMITED`/`PRIVATE`, default protektif untuk person hidup, role-aware
> access, verified claimant sebagai pengelola final, server-side redaction pada
> seluruh jalur baca/export/media/proposal, UI visibility, dan cache purge. Claim
> baru memerlukan dua OWNER/ADMIN berbeda, pembuat tidak dapat mengonfirmasi sendiri,
> dan claim legacy tidak diturunkan diam-diam. Scope cabang/field kustom, access
> request, privacy manager delegatif, versi alternatif, serta dispute masih aktif.
> P4 Foster/Guardian ditutup pada backend, Android, Room schema 6, renderer,
> inspector, export/cadangan, dan test sebagai care overlay non-lineage.
> P6 kini mempunyai revoke serta undangan tertarget email dengan masking; scope
> cabang/detail/durasi akses pasca-accept tetap backlog.
> P7 source hardening Supabase dan pemeriksaan drift CI sudah siap serta diuji pada
> PostgreSQL sementara, tetapi migration cloud dan bukti console/perangkat masih
> memerlukan tindakan pemilik sesuai checklist P7.
> P5 bertambah dengan offline `CREATE_PERSON` dan `DELETE_RELATIONSHIP`: optimistic
> Room state, idempotency server, rollback, retry, serta remap ID lokal atomik.
> Acceptance Room instrumentation dan continuity selection saat remap tetap perlu
> diuji pada perangkat final.
> Pada 29 Juli 2026 P5 bertambah dengan offline `CREATE_SOURCE`. Backend memakai
> `clientMutationId` lintas operasi untuk replay idempoten dan menolak reuse berbeda.
> Android Room v8 menyimpan sumber optimistis, menampilkan status menunggu sync,
> mengamati hasil worker, meremap sumber yang bergantung pada Person lokal, serta
> membersihkan cache saat privacy/akses menyempit atau server menolak permanen.
> Foto/media binary, review, lifecycle, claim, dan penghapusan tetap online-only.
> P8 Graph besar ditutup dengan viewport culling, tiga tingkat detail berdasarkan
> zoom, fallback daftar tekstual di atas 800 card, dan minimap privacy-safe tanpa
> identitas/metadata yang hanya memproyeksikan geometri aktif serta viewport.
> P10 menjadi `PARTIAL`: review proposal telah memiliki perbandingan nilai saat
> dibuat/terkini/usulan, reviewer, waktu, alasan penolakan, audit, privacy gate, dan
> thread komentar immutable yang tidak mengekspos identitas akun. Receipt tindakan
> pribadi juga tersedia sebagai banner global Android dan riwayat akun privacy-safe
> untuk sukses/peringatan/error/menunggu sync. Undo audit dan dispute tetap backlog.
> Audit aktif kini memberi nomor eksplisit sampai P14: P8 graph, P9 profil/provenance,
> P10 kolaborasi, P11 accessibility/visual regression, P12 observability aman,
> P13 resilience/operasi, dan P14 signing/rollout/enforcement final. P14 tetap
> ditahan sampai gap pengembangan sebelumnya dinyatakan selesai.
> Perubahan lifecycle membership disiapkan sebagai calon APK `versionCode 3`,
> `versionName 0.1.2-beta`. Build 2 pada tablet tidak boleh diganti sebelum backend,
> migration, dan policy PILOT build 3 selesai di-rollout serta diverifikasi.
> Build 4 (`0.1.3-beta`) menambahkan batch awal perbaikan akun, aktivitas, avatar,
> sinkronisasi foto, dan Android Photo Picker. Hardening claim/avatar berikutnya
> menaikkan kandidat saat ini menjadi build 5 (`0.1.4-beta`); policy PILOT perlu
> dinaikkan ketika rollout backend/migration build ini dilakukan.
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
3. `docs/PROJECT_GAP_AUDIT_2026-07-24.md` — status implementasi dan backlog aktif
   hasil audit terhadap rencana, source, API, migration, UI, dan test;
4. `docs/FRONTEND_GRAPH_WORKSPACE_DECISION.md` — baseline produk frontend dan
   tata kelola data yang telah disepakati untuk arah Blueprint v2;
5. `docs/CLOUD_PILOT_DECISION.md` — arah pilot Cloud Run + Supabase tanpa mengganti
   arsitektur NestJS;
6. `android-client/ARCHITECTURE.md` — keputusan arsitektur Android;
7. `backend/API_CONTRACT.md` — kontrak endpoint, role, error, concurrency, portability;
8. `docs/PHASE4_PRODUCTION_REVIEW.md` — privacy/security/performance/release review;
9. `android-client/README.md` dan `backend/README.md` — runbook ringkas;
10. source code dan automated tests — bukti implementasi aktual;
11. Git history — bukti waktu dan commit perubahan.

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
- membaca `docs/PROJECT_GAP_AUDIT_2026-07-24.md` dan menyebut backlog aktif yang
  relevan sebelum mengusulkan pekerjaan baru;
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

## 18. Update Layout Keluarga Besar dan Navigasi (30 Juli 2026)

- P1 proposal layout keluarga besar telah diimplementasikan sebagai pass final
  reservasi birth-family block. Packing berjalan bottom-up, hanya mengubah X, dan
  mempertahankan satu level garis horizontal per gap generasi.
- Blok dengan descendant yang bertemu kembali digabung sebelum dipindahkan agar
  invariant satu card per person tidak dilanggar.
- Minimap dapat ditutup dari workspace dan dipanggil kembali melalui menu `Alat`.
- Menu akun mempunyai `Ganti silsilah`; implementasinya hanya memanggil
  `SessionStore.clearActiveSpace()`, sehingga sesi login dipertahankan dan pemilih
  Family Space dibuka ulang melalui navigation target.
- Insiden hubungan Silsilah Semarang ditutup di client: WorkManager tidak lagi
  membatalkan worker aktif ketika mutation baru masuk, eksekusi queue diserialkan,
  `CREATE_PERSON` didahulukan pada timestamp yang sama, relationship ber-ID lokal
  ditahan, dan mutation lama dibersihkan ketika relationship ekuivalen sudah ada di
  server.
- Picker tanggal profil mendukung data historis sejak tahun 1600; avatar akun di
  header memakai frame aksen dengan inisial sebagai fallback.
- P2 incremental reflow, P3 dense partnership stress, dan P4 tablet performance
  gate tetap belum ditutup. Detail kanonik ada di
  `docs/GRAPH_LARGE_FAMILY_LAYOUT_PROPOSAL.md`.

## 19. Perbaikan Akses Akun, Profil, Aktivitas, dan Avatar (2 Agustus 2026)

- Build `pilot` dan `release` wajib menerima Google Web Client ID yang valid. Workflow
  CI dan rilis membaca repository variable `FAMILY_TREE_GOOGLE_WEB_CLIENT_ID`; build
  tidak lagi boleh berhasil dengan tombol Google yang diam-diam nonaktif. Web Client
  ID adalah identifier publik yang tertanam di APK, tetapi nilainya tetap tidak
  di-hardcode pada source.
- Akun memperoleh endpoint self-claim yang aman. Profil akun menggunakan verified
  `UserPersonClaim` pada silsilah aktif sebagai hubungan ke Person diri, tanpa
  menyamakan entitas User dan Person.
- Halaman akun disederhanakan: detail teknis ID dan kartu informasi berulang tidak
  lagi ditampilkan; pengaturan silsilah, riwayat terbaru, profil diri, foto, logout,
  dan lifecycle akun tetap tersedia.
- Notifikasi pribadi dan aktivitas kolaborasi default dibatasi maksimal 10 item.
  Riwayat kolaborasi lengkap memerlukan permintaan serta persetujuan OWNER/ADMIN,
  lalu dimuat dengan cursor maksimal 50 item per halaman.
- Aktivitas menampilkan display name akun; pengguna aktif ditampilkan sebagai
  `Anda`. UUID audit tetap disimpan backend tetapi tidak ditampilkan sebagai label UI.
- Header adaptif menyembunyikan teks brand saat ruang horizontal sangat sempit untuk
  mencegah teks menjadi satu huruf per baris. Hero akun juga memakai susunan vertikal
  yang aman pada perangkat kecil.
- Avatar header seluruh halaman memakai foto dari self-claim aktif dan fallback
  inisial. Status `PENDING` boleh memakai foto yang sudah dapat dilihat anggota,
  tetapi hak mengganti foto dan mengelola privasi tetap memerlukan `VERIFIED`.
  Endpoint foto diri menghindari pengambilan seluruh foto silsilah hanya untuk header.
- Upload foto mengembalikan signed URL baru dan langsung memperbarui shared state.
  Cache memakai `mediaId` stabil, URL diperbarui sebelum kedaluwarsa, dan foto managed
  lama dibersihkan hanya setelah foto pengganti dapat dibaca. VIEWER dengan klaim diri
  terverifikasi hanya boleh mengganti foto Person dirinya sendiri, bukan Person lain.
- Pemilihan foto memakai Android Photo Picker dengan fallback Storage Access
  Framework dan backport Google Play Services. Aplikasi sengaja tidak meminta akses
  galeri/storage luas; import/export dokumen juga memakai system picker per-file.
- Source dan automated test telah diperbarui, tetapi pengaktifan Google pada APK pilot
  tetap memerlukan nilai Web Client ID aktual pada environment build serta deployment
  backend/migration sebelum acceptance test perangkat.
- Klaim aktif unik untuk tuple silsilah, akun, dan Person. Pengiriman ulang klaim
  `PENDING` bersifat idempoten; migration mempertahankan satu klaim kanonik dan
  menandai duplikat aktif lama sebagai `REJECTED` tanpa menghapus jejaknya.

## 20. Audit Ulang Setelah Sinkronisasi GitHub (3 Agustus 2026)

- `main` lokal disinkronkan secara fast-forward dan sama dengan `origin/main` pada
  commit `2b6726d`; tidak ada file proyek lokal yang tertimpa konflik.
- Delta dari baseline lokal sebelumnya terdiri dari empat commit Git: dua commit
  implementasi dan dua merge commit PR #1–#2.
- Audit source mengonfirmasi endpoint permintaan/persetujuan riwayat lengkap,
  pembatasan preview aktivitas/notifikasi, self-claim, sinkronisasi foto avatar,
  Photo Picker tanpa izin storage luas, Google Web Client ID wajib untuk build
  pilot/release, dan unique index claim aktif.
- Quality gate lokal lulus: backend lint/build, 35 unit test, dan 22 E2E; Android
  `testDebugUnitTest`, `lintDebug`, dan `assembleDebug`.
- Verifikasi lokal tidak membuktikan migration telah diterapkan ke cloud, backend
  baru telah dideploy, repository variable Google telah diisi, policy PILOT telah
  menerima build 5, atau acceptance test perangkat telah dilakukan. Seluruhnya tetap
  merupakan gate rollout eksternal.

## 21. Status Pasangan Historis dan Kebijakan Versi Beta (3 Agustus 2026)

- Person Detail dapat mengubah hubungan pasangan menjadi `MARRIED`, `DIVORCED`, atau
  `WIDOWED`, dengan tanggal mulai/berakhir opsional. Perubahan menggunakan queue
  offline `UPDATE_SPOUSE`, cache optimistis, replay idempoten, dan audit backend.
- `MARRIED` selalu membersihkan `endDate`; `DIVORCED` dan `WIDOWED` diperlakukan
  sebagai partnership historis. Export layout hanya mengunci card berdekatan untuk
  partnership `MARRIED` tanpa tanggal akhir.
- Cincin aktif tetap saling terkait; cerai memakai cincin lebih renggang dan garis
  putus-putus; wafat memakai cincin redup dengan tanda diagonal. Riwayat hubungan
  tidak dihapus dari graph.
- Koridor lineage berikutnya harus dipesan per pasangan/keluarga biologis dan card
  pasangan historis tidak boleh dipaksa selalu berdekatan. Status baru menyediakan
  data yang diperlukan, tetapi reflow koridor besar tetap perubahan layout terpisah.
- Build beta `DEBUG`/`PILOT` dibekukan pada `versionCode 5`, `versionName
  0.1.4-beta`. Selama policy channel tidak mengaktifkan enforcement, mismatch atau
  compatibility endpoint yang tidak tersedia tidak membuka full-screen gate.
- `PRODUCTION` tidak dilonggarkan: versionCode distribusi store tetap monoton,
  API contract naik hanya untuk incompatibility nyata, dan enforcement eksplisit
  tetap memblokir build beta sekalipun.
