# Family Tree Platform — Project Blueprint

> **Status dokumen:** Aktif dan kanonik  
> **Versi:** 1.0  
> **Ditetapkan:** 13 Juli 2026  
> **Menggantikan:** Seluruh blueprint, roadmap, atau rumusan produk sebelumnya  
> **Bahasa produk utama:** Indonesia

Dokumen ini adalah sumber utama untuk visi produk, prinsip arsitektur, prioritas pengembangan, roadmap, dan pencatatan progres Family Tree Platform. Jika dokumentasi lain bertentangan dengan dokumen ini, dokumen ini yang berlaku. README dan dokumen arsitektur lama hanya dianggap sebagai referensi implementasi historis sampai diperbarui agar selaras.

## 1. Rumusan Produk

Family Tree Platform adalah aplikasi keluarga privat dan kolaboratif untuk membangun, memelihara, serta mewariskan sejarah keluarga lintas generasi.

Aplikasi ini bukan sekadar pembuat diagram silsilah. Produk ini merupakan **arsip keluarga digital yang terpercaya**:

- Satu keluarga memiliki ruang privat bernama **Family Space**.
- Anggota yang diundang dapat berkontribusi sesuai perannya.
- Setiap profil merepresentasikan orang nyata, bukan akun pengguna.
- Akun pengguna dapat mengklaim profil dirinya melalui proses verifikasi.
- Hubungan keluarga, perubahan data, dan sumber informasinya dapat ditelusuri.
- Data tetap dimiliki keluarga dan dapat diekspor.
- Backend menjadi sumber kebenaran dan penegak seluruh aturan silsilah.

### Nilai utama

1. **Preserve** — menjaga sejarah, identitas, cerita, foto, dan dokumen keluarga.
2. **Connect** — membantu anggota memahami hubungan lintas generasi.
3. **Collaborate** — memungkinkan keluarga memperbarui arsip bersama.
4. **Trust** — setiap perubahan dapat diverifikasi dan diaudit.
5. **Private by default** — data keluarga tidak menjadi jejaring sosial publik.

### Batas produk

- Produk tidak diarahkan menjadi jejaring sosial publik.
- Produk tidak hanya menjadi graph viewer.
- AI, jika ditambahkan, hanya menghasilkan saran yang harus dikonfirmasi manusia.
- Aturan kebenaran data tidak dipindahkan ke Android.
- Kemudahan penggunaan tidak boleh mengorbankan privasi, auditabilitas, atau integritas silsilah.

## 2. Prinsip Arsitektur

### Backend sebagai source of truth

Backend bertanggung jawab atas:

- autentikasi dan otorisasi;
- membership dan role;
- validasi referential integrity;
- aturan relasi keluarga dan anti-cycle;
- claim dan verifikasi;
- versioning dan conflict resolution;
- audit trail;
- kebijakan privasi dan akses data;
- export dan keberlanjutan data.

### Android sebagai client modern

Android bertanggung jawab atas:

- UI dan interaksi pengguna;
- validasi UX ringan;
- presentasi state loading, empty, success, error, offline, dan conflict;
- cache lokal dan antrean sinkronisasi;
- navigasi serta UI adaptif;
- aksesibilitas dan integrasi platform.

Database lokal Android dapat menjadi sumber penyajian data bagi UI, tetapi backend tetap menjadi sumber kebenaran bisnis.

### Arsitektur target Android

```text
Feature UI
    ↓ events / immutable UI state
ViewModel per screen
    ↓
Use cases/domain (hanya jika memberi nilai)
    ↓
Repositories
    ↓
Room/local data source ↔ Remote API
```

Feature target:

- `auth`
- `home`
- `tree`
- `people`
- `person-detail`
- `activity`
- `space-settings`

Standar implementasi:

- Jetpack Compose dan Material 3;
- Navigation Compose dengan route type-safe;
- unidirectional data flow;
- ViewModel, Coroutines, Flow/StateFlow, dan lifecycle-aware collection;
- dependency injection;
- pemisahan network DTO, database entity, domain model, dan UI model;
- Room untuk cache lokal;
- WorkManager untuk sinkronisasi/retry persisten;
- UI adaptif untuk phone, tablet, foldable, split-screen, dan desktop windowing;
- dark mode, font scaling, TalkBack, serta alternatif aksesibel untuk visualisasi graph.

Referensi platform: [Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations), [offline-first guidance](https://developer.android.com/topic/architecture/data-layer/offline-first), dan [adaptive Compose apps](https://developer.android.com/develop/ui/compose/build-adaptive-apps).

## 3. Pengalaman Pengguna Target

### Onboarding

1. Pengguna masuk atau membuat akun.
2. Pengguna membuat Family Space atau menerima undangan.
3. Pengguna memilih profil diri yang sudah ada atau membuat profil baru.
4. Pengguna mengundang anggota keluarga.
5. Pengguna mulai menambahkan orang tua, pasangan, anak, atau saudara.

UUID, `spaceId`, dan identitas teknis lain tidak boleh menjadi bagian dari UX pengguna.

### Navigasi utama

- **Beranda** — ringkasan keluarga, ulang tahun, memorial, aktivitas, dan tindakan cepat.
- **Pohon** — visualisasi silsilah interaktif.
- **Keluarga** — pencarian dan daftar seluruh orang.
- **Aktivitas** — perubahan, kontribusi, dan permintaan verifikasi.
- **Profil/Space** — akun, anggota, role, privasi, export, dan pengaturan.

Pada layar besar, gunakan pola list-detail atau multi-pane yang relevan.

### Detail orang

- Foto utama dan nama lengkap.
- Nama panggilan, variasi nama, gelar, dan histori nama.
- Status hidup dan event kehidupan.
- Tanggal serta tempat lahir/meninggal, termasuk nilai tidak pasti.
- Orang tua, pasangan, anak, saudara, wali, dan relasi relevan lain.
- Biografi, galeri media, dan dokumen.
- Sumber/bukti untuk fakta.
- Riwayat perubahan.
- Tindakan kontekstual untuk menambah atau mengoreksi relasi.

Informasi sensitif orang yang masih hidup tidak ditampilkan secara default.

### Pohon keluarga

- Pan, pinch-to-zoom, reset viewport, dan fokus pada satu orang.
- Expand/collapse cabang.
- Filter garis keturunan, generasi, keluarga inti, dan jenis relasi.
- Status hidup yang dapat dikenali tanpa hanya mengandalkan warna.
- Navigasi node ke profil.
- Representasi daftar yang dapat digunakan TalkBack.
- Export gambar atau PDF.

## 4. Domain dan Aturan Data Target

### Identitas dan nama

- nama resmi, nama lahir, alias, nama tunggal, gelar adat/agama, marga, dan histori nama;
- tanggal lengkap, hanya tahun, kisaran, sekitar, atau tidak diketahui;
- tempat terstruktur dan koordinat opsional;
- field sensitif memiliki aturan visibilitas tersendiri.

### Relasi

- parent-child: `BIOLOGICAL`, `ADOPTIVE`, `STEP`, `FOSTER`, `GUARDIAN`;
- spouse/partnership dimodelkan sebagai riwayat event/status, bukan sekadar satu nilai yang ditimpa;
- aturan relasi harus fleksibel terhadap struktur keluarga Indonesia;
- kebijakan pasangan tidak boleh menjadi asumsi teknis tersembunyi dan harus diputuskan secara eksplisit pada level produk.

### Trust dan provenance

- setiap fakta dapat memiliki sumber/citation;
- tingkat keyakinan atau status verifikasi dapat dicatat;
- perubahan menyimpan actor, waktu, before/after, alasan, dan sumber;
- duplicate detection dan merge mempertahankan audit trail;
- operasi penting menggunakan transaction dan optimistic concurrency.

## 5. Keamanan dan Privasi

- Family Space privat secara default.
- Gunakan autentikasi nyata; jangan gunakan `x-user-id` sebagai bukti identitas produksi.
- Android menggunakan Credential Manager untuk metode sign-in yang dipilih.
- Terapkan role `OWNER`, `ADMIN`, `EDITOR`, dan `VIEWER` pada setiap operasi.
- Lindungi endpoint baca, perubahan, activity, media, dan export.
- Undangan tidak boleh memungkinkan privilege escalation.
- Terapkan field-level visibility dan persetujuan profil orang yang masih hidup.
- Sediakan transfer ownership, revoke session, export, backup/restore, dan penghapusan yang terkontrol.
- Telemetry, crash report, dan log tidak boleh membocorkan data keluarga sensitif.

## 6. Enhancement Produk

### Kolaborasi tepercaya

- Undangan melalui link atau QR.
- Inbox perubahan yang perlu ditinjau.
- Proposal edit untuk data sensitif.
- Komentar, alasan penolakan, dan perbandingan before/after.
- Restore/undo berdasarkan audit log.
- Notifikasi terkontrol untuk peristiwa penting.

### Arsip dan kenangan

- Foto profil dan album.
- Scan dokumen melalui kamera.
- Rekaman cerita suara.
- Timeline kehidupan dan memorial mode.
- Label sumber seperti dokumen resmi, cerita keluarga, atau belum diverifikasi.

### Pencarian dan insight

- Pencarian yang mendukung alias dan toleransi salah eja.
- Filter generasi, marga, lokasi, status hidup, dan hubungan.
- Penjelasan jalur hubungan antara dua orang.
- Deteksi kemungkinan duplikat.
- Saran hubungan dan kelengkapan profil.

### Portabilitas data

- Export JSON lengkap.
- Import/export GEDCOM.
- Export pohon ke PDF atau gambar.
- Backup dan restore yang dapat diverifikasi.

## 7. Kualitas dan Operasional

- Unit test untuk ViewModel, repository, use case, dan business rule.
- Integration/e2e test backend dan contract test Android–API.
- Compose UI test untuk critical user journeys.
- Screenshot test berbagai ukuran layar dan tema.
- Test TalkBack, font besar, contrast, keyboard, dan touch target.
- CI menjalankan lint, test, security checks, dan build.
- Release build memakai minification secara aman.
- Baseline Profile dan Macrobenchmark untuk startup, scrolling, dan membuka pohon.
- Monitoring produksi berorientasi privasi.

Referensi performa: [Android Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles/overview).

## 8. Roadmap dan Definition of Done

Status yang digunakan: `NOT STARTED`, `IN PROGRESS`, `BLOCKED`, `DONE`.

Sebuah fase hanya berstatus `DONE` apabila seluruh acceptance criteria dan verifikasi fase terpenuhi. Keberadaan prototipe parsial tidak otomatis menyelesaikan fase.

### Fase 1 — Fondasi Aman

**Status:** `DONE`

Scope:

- autentikasi nyata dan session/token tervalidasi;
- authorization berbasis role;
- proteksi seluruh endpoint;
- validasi referential integrity lintas entity dan Family Space;
- transaction untuk operasi penting;
- API contract konsisten dan terdokumentasi;
- penghapusan seluruh user/space ID hardcoded dari runtime flow;
- audit log lengkap untuk seluruh mutasi penting.

Definition of Done:

- pengguna tidak dapat mengakses Family Space tanpa membership yang sah;
- setiap role lulus test izin dan larangan;
- tidak ada endpoint data keluarga yang terbuka hanya dengan `spaceId`;
- relasi/claim lintas-space ditolak;
- Android memperoleh identitas dan space aktif dari alur pengguna;
- test keamanan, integration, dan contract utama lulus di CI.

### Fase 2 — Android Modern

**Status:** `DONE`

Scope:

- modularisasi UI dan ViewModel per feature/screen;
- Navigation Compose;
- Material 3 design system;
- Room cache dan offline read;
- state/error handling konsisten;
- dependency injection;
- UI adaptif dan aksesibel;
- dark mode dan localization-ready resources.

Definition of Done:

- tidak ada screen monolitik yang memegang seluruh fitur;
- critical read flows tetap berfungsi tanpa jaringan setelah sinkronisasi;
- rotasi dan process recreation tidak merusak state penting;
- phone, tablet/foldable, dark mode, font besar, dan TalkBack lolos checklist;
- unit test ViewModel/repository serta UI test alur utama lulus.

### Fase 3 — Produk Inti Matang

**Status:** `DONE`

Scope:

- onboarding dan undangan anggota;
- pohon interaktif dan aksesibel;
- profil, timeline, relasi, dan histori lengkap;
- proposal/approval perubahan;
- media dan sumber fakta;
- duplicate detection dan merge;
- pencarian serta relationship path.

Definition of Done:

- pengguna baru dapat membuat/bergabung ke space tanpa bantuan teknis;
- keluarga dapat menyelesaikan alur create–connect–verify–review;
- setiap fakta penting dapat menyimpan sumber;
- graph dan representasi daftar menghasilkan relasi yang konsisten;
- merge tidak menghilangkan provenance atau audit trail.

### Fase 4 — Keberlanjutan Data

**Status:** `NOT STARTED`

Scope:

- offline write dan conflict resolution;
- import/export GEDCOM, PDF, gambar, dan backup;
- restore dan disaster recovery;
- privacy controls lanjutan;
- observability, performance benchmark, dan release pipeline produksi.

Definition of Done:

- perubahan offline tersinkron tanpa kehilangan data dan conflict terlihat pengguna;
- export/import tervalidasi melalui round-trip test;
- backup dapat dipulihkan pada environment bersih;
- target performa terukur dan tidak mengalami regresi pada CI/release gate;
- privacy dan security review produksi selesai.

## 9. Kondisi Baseline Saat Blueprint Ditetapkan

Implementasi yang sudah tersedia sebagai prototipe:

- backend NestJS, TypeORM, dan SQLite;
- entity user, Family Space, membership, person, relationship, claim, dan change log;
- relasi parent-child dan spouse dasar;
- anti-cycle dan batas dua orang tua biologis;
- claim/verify dasar, soft delete, life status, activity log, dan export JSON;
- Android Compose dengan Retrofit, Repository, ViewModel, daftar/detail person, relasi, aktivitas, export, dan graph eksperimental.

Kesenjangan utama:

- autentikasi belum produksi dan identitas masih hardcoded;
- authorization role belum ditegakkan lengkap;
- beberapa endpoint baca/export belum terlindungi;
- referential integrity dan transaction belum menyeluruh;
- UI dan state Android masih monolitik serta memiliki bagian yang dinonaktifkan sementara;
- belum ada Room/offline-first, navigation architecture, atau test suite Android yang memadai;
- model domain, provenance, media, dan privasi masih minimal;
- README tidak sepenuhnya mencerminkan implementasi aktual.

Baseline ini bukan progres penyelesaian Fase 1–4; ia adalah titik awal migrasi menuju blueprint.

## 10. Progress Tracker

| Fase | Status | Progress | Catatan terakhir |
|---|---|---:|---|
| Fase 1 — Fondasi Aman | `DONE` | 100% | JWT auth, role authorization, endpoint protection, integrity, transaction, audit, API contract, Android auth/space flow, test, dan CI terverifikasi |
| Fase 2 — Android Modern | `DONE` | 100% | Android modern ditutup: feature/screen ViewModel, Navigation Compose, Room offline read, adaptive layout, accessibility semantics, localization-ready resources, lint, unit test, dan instrumented test lulus |
| Fase 3 — Produk Inti Matang | `DONE` | 100% | Produk inti matang ditutup: onboarding/undangan, create-connect-verify-review, graph, source/media metadata, proposal approval, duplicate detection/merge, relationship path, seed dev, dan Android test surface tersedia |
| Fase 4 — Keberlanjutan Data | `NOT STARTED` | 0% | Menunggu produk inti stabil |

### Development log

> Catatan status terkini Fase 3: `DONE` 100%. Scope inti produk telah
> ditutup dengan onboarding/undangan, alur create-connect-verify-review,
> graph, source/media metadata, proposal approval, duplicate detection/merge,
> relationship path, seed dev, dan Android test surface.

| Tanggal | Fase | Perubahan | Verifikasi | Keputusan/risiko |
|---|---|---|---|---|
| 2026-07-17 | Fase 3 | Fase 3 ditutup: backend menambahkan source/citation, media metadata, proposal edit approval, duplicate candidates/merge dengan audit, relationship path, export data tambahan, dan seed dev lengkap; Android menambahkan UI source/media/proposal/path pada Person Detail serta proposal review dan duplicate merge pada Space Settings | Backend lint/build/e2e lulus dengan DB e2e sementara; Android `testDebugUnitTest`, `assembleDebug`, `lintDebug`, dan `connectedDebugAndroidTest` lulus; APK debug terpasang dan launch tanpa AndroidRuntime crash di SM-T225; smoke API source/media/proposal/duplicate/path OK | Implementasi media masih metadata/URI, bukan binary upload; offline write, GEDCOM/PDF/export lanjutan, backup, conflict resolution, dan release hardening masuk Fase 4 |
| 2026-07-13 | Fase 3 | Closure pass slice inti: endpoint daftar klaim ditambahkan, Space Settings dapat review/verify claim, People mendapat filter status dan akses Graph, GraphScreen diaktifkan melalui Navigation Compose, Person Detail mendapat editor status hidup dan relasi parent/child/spouse berbasis daftar anggota | Backend lint/build/e2e lulus; Android `testDebugUnitTest`, `assembleDebug`, `lintDebug`, dan `connectedDebugAndroidTest` lulus; APK debug terpasang dan launch tanpa AndroidRuntime crash di SM-T225; smoke API dummy seed OK | Fase 3 belum ditutup sebagai `DONE` karena media/sumber fakta, duplicate merge, relationship path, dan proposal edit detail masih tersisa |
| 2026-07-13 | Blueprint | Blueprint v1.0 ditetapkan sebagai sumber kanonik | Kondisi repo dan dokumen awal ditinjau | Fitur prototipe dicatat sebagai baseline, bukan fase yang selesai |
| 2026-07-13 | Blueprint | Arsitektur Android diselaraskan dengan blueprint v1.0 | Kontrak usang, target layer, offline, security, accessibility, testing, dan DoD ditinjau | Tidak menaikkan progres fase karena perubahan hanya dokumentasi |
| 2026-07-13 | Fase 1 | Implementasi Fase 1 dimulai | Scope dan Definition of Done dikunci dari blueprint | Status fase menjadi `IN PROGRESS`; hasil akhir tetap bergantung pada test dan verifikasi |
| 2026-07-13 | Fase 1 | Fondasi keamanan backend dan alur identitas Android diselesaikan | Backend lint/build/unit/e2e lulus; Android assembleDebug dan unit task lulus; production dependency audit 0 vulnerability | Email-password + JWT dipakai sebagai fondasi; token Android memory-only sampai refresh rotation dan encrypted storage tersedia |
| 2026-07-13 | Fase 1 | Fase 1 ditutup pada 100% | OWNER/ADMIN/EDITOR/VIEWER, unauthenticated, non-member, cross-space, audit, dan export permissions diuji | Fase 2 dapat dimulai; refactor Android modern tetap di luar scope Fase 1 |
| 2026-07-13 | Fase 1 | Smoke test perangkat fisik Samsung SM-T225 (Android 14/API 34) | Wireless ADB terhubung; tablet mencapai backend via Wi-Fi; APK terpasang dan login screen terbuka tanpa crash | Debug build memakai endpoint LAN melalui `BuildConfig`; production tetap menolak cleartext traffic |
| 2026-07-13 | Fase 2 | Implementasi Android Modern dimulai | Scope dan Definition of Done Fase 2 dikunci dari blueprint | Migrasi dilakukan inkremental dan setiap tahap harus tetap buildable di tablet |
| 2026-07-13 | Fase 2 | Fondasi navigasi dan feature boundary pertama diterapkan | `assembleDebug` lulus; APK terpasang di Samsung SM-T225; MainActivity foreground tanpa AndroidRuntime crash | Manual application container dipakai sebagai constructor DI awal; Room dependency dipasang dan cache diimplementasikan pada slice berikutnya |
| 2026-07-13 | Fase 2 | Screen legacy monolitik diganti feature people, person-detail, dan activity; Room menjadi sumber baca daftar person | Unit test 2/2 dan Compose instrumented test 2/2 lulus di SM-T225; lint/build lulus; dark mode, font 1.3, dan phone-like width smoke test tanpa crash | Offline read aktif setelah sinkronisasi pertama; foldable/TalkBack checklist dan test repository/Room masih diperlukan sebelum fase ditutup |
| 2026-07-13 | Fase 2 | Fase 2 ditutup: Activity dan Person Detail dipindah ke ViewModel, People memakai adaptive container layout, semantics aksesibilitas ditambahkan, Room cache diuji in-memory, dan Android build target memakai Java 17 | `testDebugUnitTest`, `assembleDebug`, `lintDebug`, dan `connectedDebugAndroidTest` lulus; 3 instrumented tests berjalan di Samsung SM-T225 Android 14 | Connected test sempat gagal saat install karena duplicate ADB/mDNS dan timeout transfer Wi-Fi; diselesaikan dengan menargetkan serial IP tunggal `192.168.100.48:36375` |
| 2026-07-13 | Fase 3 | Implementasi Produk Inti Matang dimulai dari onboarding/undangan: backend membuat invitation token single-use, preview dan accept invitation, membership via invite diaudit, Android dapat join dengan invitation code | Backend lint/build/e2e lulus; Android `testDebugUnitTest`, `assembleDebug`, `lintDebug`, dan `connectedDebugAndroidTest` lulus di SM-T225 | Pembuatan undangan via Android UI ditempatkan untuk slice Space Settings berikutnya; backend/API sudah siap dipakai |
| 2026-07-13 | Fase 3 | Space Settings Android ditambahkan untuk membuat invitation code, memilih role undangan, mengatur expiry 1-30 hari, menampilkan token, dan menyalin token ke clipboard | Android `testDebugUnitTest`, `assembleDebug`, dan `lintDebug` lulus | Role tetap ditegakkan backend; VIEWER/EDITOR yang membuka layar akan menerima error authorization saat mencoba membuat undangan |
| 2026-07-13 | Fase 3 | Person Detail Android menambahkan aksi `Claim as me` untuk mengirim klaim profil diri melalui endpoint claim backend yang sudah terlindungi role/membership | Android `testDebugUnitTest`, `assembleDebug`, dan `lintDebug` lulus | Approval klaim oleh OWNER/ADMIN dari Android masih menjadi slice berikutnya |
| 2026-07-13 | Fase 3 | Seed data development ditambahkan untuk membuat 4 akun demo keluarga (ayah, ibu, anak pertama, kakek), Family Space, profil, relasi dasar, dan verified claim; Android debug build mendapat tombol quick-login demo | Backend seed script, lint/build, Android build/lint/test perlu dijalankan setiap perubahan seed | Shortcut login hanya muncul pada `BuildConfig.DEBUG`; password demo bukan secret produksi |

## 11. Aturan Pemeliharaan Blueprint

Setiap pekerjaan roadmap harus:

1. dikaitkan dengan fase dan scope;
2. memiliki acceptance criteria sebelum implementasi;
3. diuji sesuai risikonya;
4. memperbarui Progress Tracker dan Development Log setelah diverifikasi;
5. mencatat keputusan arsitektur atau perubahan scope yang material;
6. tidak menandai fase `DONE` hanya berdasarkan estimasi persentase.

Jika arah produk berubah, revisi dokumen ini melalui versi baru dan catat alasannya di Development Log. Dokumen lama tidak boleh diam-diam menjadi sumber kebenaran kembali.
