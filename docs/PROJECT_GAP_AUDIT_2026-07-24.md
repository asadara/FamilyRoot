# FamilyRoot — Audit Gap dan Backlog Aktif

> **Tanggal audit:** 24 Juli 2026, Asia/Jakarta
> **Repository:** `asadara/FamilyRoot`
> **Branch:** `main`
> **Baseline implementasi sebelum dokumen audit:** `a79bbae` (`fix: keep splash logo within safe area`)
> **Status dokumen:** backlog aktif dan acuan handoff lintas perangkat

## 1. Tujuan dan cara menggunakan dokumen

Dokumen ini mencatat perbedaan antara rencana/keputusan produk dengan implementasi
aktual setelah rangkaian perbaikan frontend, graph, profil, foto, sinkronisasi, dan
branding pada 21–23 Juli 2026.

Dokumen ini tidak menggantikan `PROJECT_BLUEPRINT.md` atau keputusan produk di
`FRONTEND_GRAPH_WORKSPACE_DECISION.md`. Fungsinya adalah:

- mencegah pekerjaan yang sudah selesai dikerjakan ulang;
- menjaga pekerjaan yang belum selesai tetap terlihat setelah clone/pull;
- membedakan gap aktif, implementasi parsial, pekerjaan operasional, dan rencana yang
  sengaja ditunda atau digantikan keputusan baru;
- memberi urutan kerja yang aman untuk sesi pengembangan berikutnya.

Status yang digunakan:

- `ACTIVE` — sudah disepakati atau diperlukan, tetapi belum tersedia end-to-end;
- `PARTIAL` — sebagian kontrak/UI sudah ada, tetapi alur belum lengkap;
- `EXTERNAL VERIFY` — source siap atau pernah diuji, tetapi bukti layanan eksternal
  belum lengkap di repository;
- `DEFERRED` — rencana jangka panjang yang belum menjadi fase implementasi aktif;
- `SUPERSEDED` — rencana lama digantikan keputusan produk yang lebih baru.

## 2. Ringkasan hasil audit

Fondasi teknis Blueprint v1 Fase 1–4 tetap berstatus selesai. Status tersebut tidak
berarti seluruh visi produk, lifecycle data, privacy model, atau kesiapan production
sudah selesai.

Workspace pilot saat ini sudah mendukung graph adaptif, partnership atomik, lineage
eksplisit, integrity recommendation, person tanpa hubungan, tambah person dari
workspace, drag untuk menghubungkan person, filter generasi, foto profil, edit profil
lengkap, undangan berbasis role, offline queue untuk mutasi inti tertentu, serta
export/backup.

Gap aktif terbesar berada pada:

1. penghapusan person end-to-end;
2. lifecycle akun, anggota, undangan, dan silsilah;
3. privacy per person/field/scope dan claim kolektif;
4. relasi `FOSTER` dan `GUARDIAN`;
5. cakupan offline write di luar empat tipe mutation yang tersedia;
6. penutupan dan bukti operasional cloud pilot.

## 3. Backlog aktif terprioritas

### P1 — Hapus person yang aman (`PARTIAL`)

Backend sudah menyediakan `DELETE /persons/:personId` dan soft-delete, tetapi Android
belum menyediakan endpoint, repository action, ViewModel action, confirmation flow,
atau tombol hapus person.

Gap yang harus ditutup:

- putuskan role final. Backend saat ini mengizinkan `OWNER` dan `ADMIN`, sedangkan
  diskusi pengguna menyebut pemilik dan kontributor;
- tampilkan ringkasan dampak sebelum konfirmasi: relationship, claim, media, source,
  proposal, dan mutation yang terkait;
- tentukan apakah relationship ikut ditandai terhapus, dipertahankan sebagai audit,
  atau memerlukan koreksi satu per satu;
- hapus card/cache lokal hanya setelah hasil server jelas dan tetap pertahankan audit;
- lindungi person yang sedang diklaim, person pusat, atau person yang masih diperlukan
  oleh struktur bersama sesuai keputusan produk;
- tambahkan test izin, integrity, cache, graph, dan kegagalan jaringan.

Bukti implementasi parsial:

- `backend/src/persons/persons.controller.ts`
- `backend/src/persons/persons.service.ts`
- Android `network/ApiService.kt` belum mempunyai operasi delete person.

### P2 — Lifecycle akun, anggota, undangan, dan silsilah (`ACTIVE`)

Belum tersedia endpoint dan UX lengkap untuk:

- meninggalkan silsilah;
- transfer ownership;
- mengubah atau mencabut role/membership;
- mencabut undangan yang belum dipakai;
- menghapus akun dan memutus seluruh session;
- menghapus atau mengarsipkan silsilah secara aman;
- export/ringkasan dampak sebelum tindakan;
- purge cache perangkat setelah akses dicabut.

Penghapusan data dummy langsung melalui database bukan fitur produk dan tidak boleh
dianggap sebagai penyelesaian lifecycle.

Acuan produk: bagian **Siklus Keluar, Penghapusan Akun, dan Data Person** pada
`docs/FRONTEND_GRAPH_WORKSPACE_DECISION.md`.

### P3 — Privacy granular dan claim kolektif (`ACTIVE`)

Authorization saat ini masih terutama berbasis membership role untuk seluruh
silsilah. Belum tersedia:

- visibility `Keluarga`, `Terbatas`, dan `Privat`;
- field-level visibility untuk person hidup;
- perlindungan default untuk anak;
- role × scope untuk diri sendiri, person tertentu, atau cabang;
- privacy manager dan request akses detail;
- cache purge setelah scope/revocation berubah;
- dua konfirmasi anggota berbeda untuk claim/informasi tertentu;
- status `Belum diperiksa`, `Dikonfirmasi keluarga`, dan `Diperdebatkan`;
- versi alternatif dan dispute tanpa menimpa data.

Claim saat ini dapat diverifikasi satu `OWNER` atau `ADMIN`. Model ini cukup untuk
pilot teknis, tetapi belum memenuhi keputusan tata kelola yang lebih lengkap.

### P4 — Relasi Foster dan Guardian (`ACTIVE`)

Pilihan hubungan `BIOLOGICAL`, `ADOPTIVE`, dan `STEP` sudah ada. `FOSTER` serta
`GUARDIAN` belum ada pada entity, DTO, migration constraint, Android model, pilihan
UI, integrity rule, renderer, legend, export, atau test.

Implementasi harus menjaga bahwa:

- foster/guardian tidak otomatis mengubah generation level;
- tidak ada inferensi partnership, parentage biologis, ACL, atau legalitas;
- inspector memakai label eksplisit;
- graph memakai care-relationship overlay/pola tersendiri;
- pola dapat dibedakan tanpa hanya mengandalkan warna.

### P5 — Perluasan offline write (`PARTIAL`)

Room mutation queue saat ini hanya memuat:

- `UPDATE_LIFE_STATUS`;
- `UPDATE_PROFILE`;
- `ADD_PARENT_CHILD`;
- `ADD_SPOUSE`.

Belum masuk queue:

- create/delete person;
- delete relationship;
- foto dan media;
- source;
- proposal/review;
- invitation dan lifecycle membership/silsilah.

Setiap perluasan harus mempunyai idempotency, optimistic state atau fallback yang
jelas, retry, conflict handling, rollback, dan indikator sync. Label status header
saat ini benar untuk mutation yang sudah terdaftar, tetapi belum mewakili tindakan
yang masih online-only.

### P6 — Undangan tertarget dan dapat dicabut (`PARTIAL`)

Yang sudah ada:

- kode role berbeda: `FR-V`, `FR-K`, dan `FR-P`;
- token acak dan unik;
- single-use;
- expiry 1–30 hari;
- pembatasan pengangkatan pengelola oleh role yang tidak berhak.

Yang belum ada:

- target akun/email tertentu;
- anchor person dan scope cabang;
- batas tingkat detail;
- akses sementara setelah undangan diterima;
- revoke invitation;
- QR invitation;
- usulan undangan oleh role yang tidak boleh mengundang langsung.

### P7 — Penutupan cloud pilot (`EXTERNAL VERIFY`)

Source Cloud Run/Supabase dan APK pilot tersedia, tetapi Tahap 7 belum mempunyai
seluruh bukti penutupan yang tercatat:

- persistensi setelah scale-to-zero atau revision restart;
- backup dummy disimpan di lokasi aman milik pemilik project;
- review Cloud Run revision, secret reference, build/runtime log, APK, dan repository;
- Google Cloud billing report/budget alert dan Supabase usage;
- validasi UI pada perangkat kedua;
- konfirmasi visual pull-to-refresh terbaru;
- status Security Advisor terkini.

Fungsi Supabase `public.rls_auto_enable()` yang pernah menghasilkan warning
`SECURITY DEFINER` tidak dikelola oleh migration repository. Bila sudah diperbaiki
manual, hasilnya tetap perlu dicatat atau diwujudkan sebagai migration administratif
yang aman dan idempoten. Saran `RLS Enabled No Policy` harus didokumentasikan sebagai
default-deny yang disengaja bila client memang tidak mengakses tabel langsung.

## 4. Backlog lanjutan

### Graph besar (`DEFERRED`)

Progressive expansion, filter generasi, deterministic placement, dan collision
avoidance sudah ada. Rencana yang belum tersedia:

- viewport culling;
- minimap yang tidak membocorkan data privat;
- detail card berdasarkan tingkat zoom;
- reduced-motion handling;
- fallback tekstual ketika renderer mencapai batas perangkat.

### Model data profil yang lebih kaya (`DEFERRED`)

Belum tersedia penuh:

- histori nama, alias multipel, nama lahir, dan gelar adat/agama terstruktur;
- tanggal parsial, kisaran, perkiraan, atau tidak diketahui;
- tempat terstruktur dan koordinat;
- timeline event bersama;
- provenance per field dan confidence/status verification.

### Kolaborasi dan notifikasi (`DEFERRED`)

Proposal approve/reject dan activity log sudah ada. Yang belum tersedia:

- komentar dan alasan penolakan terstruktur;
- perbandingan before/after yang lengkap di UI;
- undo/restore berbasis audit;
- notifikasi terkontrol;
- versi alternatif dan dispute kolaboratif.

### Quality dan production operations (`DEFERRED`)

Masih perlu direncanakan atau dibuktikan sebelum production:

- screenshot regression test lintas ukuran/tema;
- accessibility acceptance yang lebih lengkap untuk TalkBack, font besar, contrast,
  keyboard, dan touch target;
- privacy-safe production monitoring;
- SLA, retention, backup/PITR, restore drill, disaster recovery, dan incident runbook;
- production signing, store publication, domain final, serta budget operasional.

## 5. Rencana lama yang tidak lagi menjadi gap aktif

### Manual drag untuk memindahkan card (`SUPERSEDED`)

Card tidak dibuat bebas dipindahkan pengguna. Keputusan terbaru menggunakan block
lineage/partnership atomik, deterministic placement, dan collision avoidance otomatis
agar posisi tetap konsisten lintas perangkat, export, dan refresh.

### Penyimpanan dokumen formal dan galeri tanpa batas (`SUPERSEDED`)

Keputusan frontend terbaru membatasi aplikasi pada satu foto profil aktif, sumber
teks, dan tautan arsip eksternal. Scan dokumen formal, nomor identitas, dan galeri
internal besar bukan backlog aktif tanpa keputusan produk baru.

### Perbedaan tinggi garis lineage (`SUPERSEDED`)

Usulan membedakan level garis untuk menghindari collision ditolak. Layout memakai
block keluarga dan pemindahan horizontal.

## 6. Fitur hasil diskusi terbaru yang sudah selesai

Jangan membuka ulang item berikut tanpa bug atau kebutuhan baru:

- indikator destinasi aktif pada navigasi dan footer copyright;
- kode undangan berbeda per role;
- status sync membedakan offline, menunggu, gagal, konflik, dan tersinkron;
- person baru tetap muncul pada kelompok `Belum terhubung`;
- notifikasi sukses/error setelah membuat hubungan;
- pilihan adopsi/tiri melalui kelompok `Lainnya`;
- hapus relationship dengan confirmation;
- integrity recommendation untuk relationship rancu;
- drag handle antarkartu untuk membuat relationship;
- filter `Semua`, `Satu generasi`, `Leluhur`, dan `Keturunan`;
- partnership block atomik, sibling ordering, child distribution, dan junction cincin;
- card ringkas dan connector horizontal di antara generasi;
- tekan lama area kosong dan tombol tambah person dari workspace;
- foto profil untuk diri sendiri/person lain sesuai role dan sinkron ke graph/inspector;
- edit profil lengkap serta nama lengkap/nama panggilan yang jelas;
- tanggal mulai hubungan pasangan bersifat opsional;
- navigasi cepat kembali ke Pohon;
- halaman Petunjuk dan Tentang aplikasi yang diperbarui;
- Google Sign-In pada build pilot;
- launcher/splash memakai aset resolusi tinggi dan splash safe area Android.

## 7. Urutan kerja yang direkomendasikan

Urutan default bila pengguna tidak menetapkan prioritas lain:

1. desain dan implementasi hapus person yang aman;
2. lifecycle membership/silsilah/account;
3. privacy model dan claim kolektif;
4. Foster/Guardian;
5. perluasan offline queue;
6. undangan tertarget/revoke;
7. penutupan cloud pilot dan Security Advisor evidence;
8. optimasi graph besar;
9. pembaruan model profil/provenance dan fitur kolaborasi lanjutan.

Setiap item tetap memerlukan persetujuan pengguna sebelum perubahan kode. Audit ini
menjaga arah, bukan memberi izin otomatis untuk implementasi atau tindakan destruktif.

## 8. Prosedur handoff setelah clone/pull

Pada perangkat pengembangan baru:

1. clone repository dan checkout `main`;
2. jalankan `git pull --ff-only origin main`;
3. baca `PROJECT_MEMORY.md` seluruhnya;
4. baca dokumen audit ini;
5. baca keputusan frontend/cloud yang dirujuk oleh item yang akan dikerjakan;
6. periksa source dan test aktual karena implementasi dapat lebih baru dari audit;
7. laporkan commit, status Git, fase aktif, dan item backlog yang dipilih;
8. tunggu persetujuan pengguna sebelum mengubah file.

Database runtime, `.env`, token, credential, APK/AAB, dan backup data keluarga tidak
ikut Git. Konteks keputusan dan backlog harus tetap tersedia melalui file Markdown
yang tracked di repository.
