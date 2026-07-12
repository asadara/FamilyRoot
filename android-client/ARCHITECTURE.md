# Android Client Architecture

> **Status:** Aktif — arsitektur teknis Android  
> **Diperbarui:** 13 Juli 2026  
> **Dokumen induk:** [`../PROJECT_BLUEPRINT.md`](../PROJECT_BLUEPRINT.md)

Dokumen ini menjelaskan arsitektur teknis Android untuk Family Tree Platform. Visi produk, scope, prioritas, roadmap, dan Definition of Done lintas platform ditentukan oleh `PROJECT_BLUEPRINT.md`. Jika terdapat pertentangan, blueprint adalah sumber yang berlaku.

## 1. Tujuan dan Prinsip

### Backend sebagai source of truth

Backend menjadi otoritas untuk:

- autentikasi dan otorisasi;
- membership dan role;
- integritas referensi antar-entity dan Family Space;
- aturan relasi keluarga, termasuk anti-cycle;
- claim dan verifikasi;
- audit trail;
- versioning dan conflict resolution;
- kebijakan privasi serta akses data.

Android tidak boleh menduplikasi atau menggantikan aturan tersebut. Validasi Android hanya bertujuan meningkatkan UX; hasil akhir setiap mutasi tetap ditentukan backend.

### Android sebagai client modern

Android bertanggung jawab atas:

- UI dan interaksi pengguna;
- navigasi dan adaptive layout;
- validasi UX ringan;
- state loading, empty, content, error, offline, pending sync, dan conflict;
- cache lokal dan orkestrasi sinkronisasi;
- integrasi platform Android;
- aksesibilitas, localization, dan performa client.

Database lokal dapat menjadi sumber data yang diamati UI agar aplikasi responsif dan dapat membaca data secara offline. Backend tetap menjadi sumber kebenaran bisnis.

## 2. Kondisi Saat Ini

Client saat ini telah menyelesaikan target arsitektur Fase 2 Android Modern, dengan:

- `MainActivity` sebagai host Compose tipis;
- Navigation Compose untuk route auth, spaces, people, activity, dan person detail;
- screen terpisah per feature (`auth`, `spaces`, `people`, `activity`, `person-detail`);
- `PeopleViewModel`, `ActivityViewModel`, dan `PersonDetailViewModel` yang mengekspos immutable UI state;
- manual application container sebagai constructor DI awal untuk database dan repository;
- Retrofit dan OkHttp untuk API dengan Bearer access token;
- Room cache untuk read flow daftar/detail person setelah sinkronisasi;
- UI people adaptif berbasis available container width untuk phone, tablet, split-screen, dan foldable;
- Material 3, resource string, serta semantics heading/content description untuk jalur TalkBack dasar;
- test unit, lint, dan instrumented test yang berjalan di Samsung SM-T225 Android 14.

Sisa pekerjaan Android berikutnya masuk fase lanjutan, terutama onboarding/undangan, graph matang dan aksesibel, media/sumber fakta, offline write, WorkManager sync persisten, serta release hardening.

## 3. Arsitektur Target

```text
Compose Feature UI
        ↓ events / immutable UI state
ViewModel per screen
        ↓
Use case (hanya untuk orkestrasi atau domain client yang bermakna)
        ↓
Repository interface
        ↓
Local data source (Room/DataStore) ↔ Remote data source (API)
        ↓
WorkManager sync/retry
```

Aliran dependensi hanya menuju ke bawah. UI tidak mengakses Retrofit, Room DAO, atau konfigurasi networking secara langsung.

### UI layer

- Jetpack Compose dan Material 3.
- Satu immutable `UiState` utama per screen.
- User action dikirim sebagai event atau method ViewModel yang eksplisit.
- State dikumpulkan secara lifecycle-aware.
- Navigasi tidak disimpan sebagai boolean tampilan yang tersebar.
- Snackbar dan one-off effects dipisahkan dari persistent screen state.
- Composable dibuat stateless bila memungkinkan dan memiliki preview representatif.

### ViewModel

- Satu ViewModel per screen atau navigation scope yang jelas.
- Mengekspos `StateFlow<UiState>`.
- Tidak menyimpan `Activity`, `View`, atau `Context` berumur panjang.
- Tidak berisi parsing HTTP atau akses database langsung.
- Saved state hanya digunakan untuk argumen navigasi atau state yang perlu dipulihkan.

### Domain/use case

Use case tidak wajib untuk setiap operasi. Tambahkan hanya jika:

- mengorkestrasi beberapa repository;
- dipakai ulang oleh beberapa ViewModel;
- menyederhanakan aturan presentasi yang kompleks;
- memberikan batas testing yang bermakna.

Aturan integritas silsilah tetap berada di backend, bukan domain layer Android.

### Data layer

- Repository adalah satu-satunya pintu masuk data bagi layer di atasnya.
- Remote DTO, Room entity, domain model, dan UI model dipisahkan.
- Mapping dilakukan pada boundary masing-masing layer.
- UI membaca stream data dari local source untuk fitur yang mendukung offline read.
- Respons network memperbarui local source sebelum dipresentasikan sebagai state stabil.
- Error dipetakan ke tipe aplikasi yang konsisten, bukan string mentah dari exception.

### Dependency injection

Dependency injection menyediakan:

- API service dan HTTP client;
- database dan DAO;
- repository implementation;
- use case;
- ViewModel dependencies;
- dispatcher atau clock yang perlu dikontrol dalam test.

Tidak ada singleton manual yang menyembunyikan dependency atau environment runtime.

## 4. Struktur Feature Target

```text
app/
core/
  common/
  designsystem/
  model/
  network/
  database/
  data/
  testing/
feature/
  auth/
  home/
  tree/
  people/
  person-detail/
  activity/
  space-settings/
sync/
```

Struktur dapat dimulai sebagai package dalam satu Gradle module. Pemecahan menjadi multi-module dilakukan ketika boundary stabil dan memberi manfaat build atau ownership yang nyata.

## 5. Navigasi dan Adaptive UI

Destinasi utama mengikuti blueprint:

- Beranda;
- Pohon;
- Keluarga;
- Aktivitas;
- Profil/Space.

Ketentuan:

- gunakan Navigation Compose dengan route dan argument type-safe;
- deep link dan restore state dipertimbangkan sejak desain route;
- phone menggunakan pola single-pane yang sesuai;
- layar besar menggunakan list-detail atau multi-pane ketika memberi nilai;
- layout ditentukan oleh available window size, bukan nama perangkat;
- graph memiliki representasi daftar alternatif yang aksesibel.

## 6. Offline dan Sinkronisasi

### Fase 2: offline read

- Room menjadi cache persisten untuk data keluarga yang diperlukan UI.
- UI tetap menampilkan data terakhir yang tersedia saat jaringan tidak ada.
- Refresh network memperbarui Room melalui repository.
- WorkManager menangani refresh/retry yang harus bertahan melewati process death.
- UI menampilkan freshness atau kondisi offline ketika relevan.

### Fase 4: offline write

- Mutasi offline disimpan dalam antrean persisten.
- Setiap item memiliki status `PENDING`, `SYNCING`, `FAILED`, `CONFLICT`, atau `SYNCED`.
- Idempotency key mencegah mutasi ganda saat retry.
- Version dari backend digunakan untuk mendeteksi conflict.
- Conflict tidak diselesaikan diam-diam jika berpotensi menghilangkan kontribusi keluarga.

## 7. Networking dan Kontrak API

Kontrak endpoint tidak disalin secara manual ke dokumen ini agar tidak menjadi usang. Kontrak kanonik harus dihasilkan atau divalidasi dari backend melalui OpenAPI dan diuji dengan contract test.

Ketentuan client:

- development emulator menggunakan host `http://10.0.2.2:3001/`;
- base URL selalu berasal dari build/environment configuration dan memiliki trailing slash;
- device fisik menggunakan endpoint environment yang dikonfigurasi, bukan perubahan source manual;
- production wajib menggunakan HTTPS;
- authentication menggunakan token/session tervalidasi, bukan `x-user-id` hardcoded;
- interceptor logging body hanya boleh aktif pada debug dan harus memperhatikan data sensitif;
- timeout, retry, dan idempotency dibedakan berdasarkan jenis operasi;
- response DTO memakai identifier aktual seperti `userId`, `spaceId`, `personId`, `relationshipId`, dan `claimId`.

### Error model

Backend target menyediakan error code yang stabil dan machine-readable. Android memetakan error menjadi kategori:

- validation;
- unauthenticated (`401`);
- forbidden (`403`);
- not found (`404`);
- conflict (`409`);
- server/retryable;
- network unavailable;
- timeout;
- serialization/contract mismatch;
- unknown.

Pesan UI harus user-friendly dan tidak menampilkan stack trace, raw response sensitif, atau detail internal server.

## 8. Authentication dan Session

- Identitas pengguna berasal dari alur authentication, bukan `Config.CURRENT_USER_ID`.
- Credential Manager digunakan untuk metode sign-in yang dipilih produk.
- Token disimpan menggunakan mekanisme platform yang sesuai dan tidak ditulis ke log.
- Session dapat dicabut dan logout membersihkan data lokal yang terkait sesuai kebijakan.
- Pemilihan Family Space aktif merupakan state pengguna yang tervalidasi, bukan UUID hardcoded.
- Pergantian akun atau Family Space tidak boleh mencampur cache antar-keluarga.

## 9. Accessibility dan Design System

- Gunakan komponen Material 3 dan semantics yang benar.
- Seluruh tindakan penting memiliki label yang bermakna bagi TalkBack.
- Touch target, contrast, focus order, dan state description diverifikasi.
- Informasi tidak disampaikan hanya melalui warna.
- Font scaling tidak memotong data atau tindakan penting.
- Graph mendukung zoom dan reflow yang sesuai serta memiliki alternatif nonvisual.
- Dynamic color boleh digunakan jika tetap menjaga identitas visual dan contrast.
- String user-facing ditempatkan pada resources dan siap dilokalisasi.

## 10. Testing dan Quality Gates

### Unit test

- ViewModel state transition;
- repository dan mapper;
- use case;
- error mapping;
- sync queue dan conflict handling saat mulai diterapkan.

### Integration dan contract test

- serialisasi request/response terhadap kontrak backend;
- Room migration;
- repository dengan local dan remote source;
- authentication/session behavior.

### UI dan device test

- onboarding dan sign-in;
- daftar, pencarian, detail, dan pembuatan person;
- navigasi relasi dan pohon;
- offline/online transition;
- phone, tablet/foldable, dark mode, font besar, dan TalkBack.

### Performance

- ukur startup, scrolling daftar, dan membuka pohon menggunakan Macrobenchmark;
- sediakan Baseline Profile untuk critical user journeys;
- optimasi hanya dinyatakan berhasil setelah diukur.

## 11. Definition of Done Android Modern

Definition of Done resmi mengikuti **Fase 2 — Android Modern** pada blueprint. Pada 13 Juli 2026, fase ini ditandai `DONE` setelah:

- UI dan ViewModel dipisah berdasarkan screen/feature utama;
- Navigation Compose menggantikan state navigasi manual;
- Room menyediakan offline read untuk critical person list/detail flow setelah sinkronisasi;
- state loading, empty, content, error, dan offline dibawa melalui UI state;
- dependency injection awal diterapkan melalui application container;
- rotasi/activity recreation diuji pada route autentikasi;
- phone/tablet layout, dark mode, font besar, dan semantics aksesibilitas diverifikasi sebagai checklist fase;
- unit test, lint, dan instrumented test utama lulus di perangkat fisik.

Kemampuan lama untuk melakukan `POST /persons` dan menampilkan hasil diklasifikasikan sebagai **historical MVP milestone**, bukan Definition of Done arsitektur saat ini.

## 12. Urutan Migrasi yang Aman

Urutan detail mengikuti task breakdown roadmap. Arah teknis yang disarankan:

1. Stabilkan authentication dan API contract pada Fase 1.
2. Tambahkan navigation shell dan design system tanpa mengubah business flow.
3. Pisahkan screen serta ViewModel secara bertahap.
4. Perkenalkan dependency injection dan repository interface.
5. Pisahkan DTO/domain/UI model.
6. Tambahkan Room dan offline-read repository.
7. Terapkan adaptive layout, accessibility, dan quality gates.
8. Tambahkan offline write hanya setelah versioning dan conflict contract backend stabil pada Fase 4.

Setiap pekerjaan wajib memperbarui Progress Tracker dan Development Log pada `PROJECT_BLUEPRINT.md` setelah diverifikasi.
