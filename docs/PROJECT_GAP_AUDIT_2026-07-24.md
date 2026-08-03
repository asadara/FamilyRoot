# FamilyRoot — Audit Gap dan Backlog Aktif

> **Tanggal audit:** 24 Juli 2026, Asia/Jakarta
> **Pembaruan audit terakhir:** 3 Agustus 2026, Asia/Jakarta
> **Repository:** `asadara/FamilyRoot`
> **Branch:** `main`
> **Baseline implementasi sebelum dokumen audit:** `a79bbae` (`fix: keep splash logo within safe area`)
> **Baseline audit terbaru:** `2b6726d` (`Merge PR #2: fix duplicate claims and account avatar sync`)
> **Status dokumen:** backlog aktif dan acuan handoff lintas perangkat

## 1. Tujuan dan cara menggunakan dokumen

Dokumen ini mencatat perbedaan antara rencana/keputusan produk dengan implementasi
aktual setelah rangkaian perbaikan frontend, graph, profil, foto, sinkronisasi,
branding, lifecycle, privacy, offline write, akun, dan aktivitas sampai 3 Agustus
2026.

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
- `SUPERSEDED` — rencana lama digantikan keputusan produk yang lebih baru;
- `DONE` — kontrak, implementasi, dan quality gate gap sudah ditutup.

## 2. Ringkasan hasil audit

Fondasi teknis Blueprint v1 Fase 1–4 tetap berstatus selesai. Status tersebut tidak
berarti seluruh visi produk, lifecycle data, privacy model, atau kesiapan production
sudah selesai.

Workspace pilot saat ini sudah mendukung graph adaptif, partnership aktif atomik, lineage
eksplisit, integrity recommendation, person tanpa hubungan, tambah person dari
workspace, drag untuk menghubungkan person, filter generasi, foto profil, edit profil
lengkap, undangan berbasis role, offline queue untuk mutasi inti tertentu, serta
export/backup.

Gap aktif terbesar pada audit 3 Agustus 2026 berada pada:

1. privacy scope cabang/field, delegasi, versi alternatif, dan dispute;
2. perluasan offline write di luar delapan tipe mutation yang tersedia;
3. scope cabang/detail/durasi undangan;
4. penutupan dan bukti operasional cloud pilot;
5. model profil/provenance yang lebih kaya, accessibility evidence, observability,
   resilience, dan rollout final.

## 3. Backlog aktif terprioritas

### P1 — Hapus person yang aman (`DONE`, 28 Juli 2026)

Keputusan final: `OWNER` dan `ADMIN` dapat menghapus langsung setelah pemeriksaan
dampak; `EDITOR` hanya dapat mengirim permintaan beserta alasan; `VIEWER` tidak
memperoleh aksi. Person hanya di-soft-delete bila tidak memiliki relationship, claim,
media, source, proposal lain yang masih menunggu, atau mutation lokal.

Relationship dan data terhubung tidak pernah dihapus diam-diam. Masing-masing harus
diselesaikan melalui alur koreksi yang sesuai. Persetujuan permintaan Kontributor
mengulang impact check dan melakukan soft-delete dalam transaksi yang sama. Card dan
cache foto lokal baru dibersihkan setelah server mengonfirmasi keberhasilan; audit
`PERSON/DELETE` tetap disimpan.

Bukti implementasi:

- `backend/src/persons/person-deletion.service.ts`
- `backend/src/persons/persons.controller.ts`
- `backend/src/database/migrations/1753315200000-AddPersonDeletionProposal.ts`
- Android `models/PersonDeletionModels.kt`, `network/ApiService.kt`,
  `repository/PersonRepository.kt`, dan `feature/persondetail/`
- kontrak role/integrity diuji di `backend/test/app.e2e-spec.ts`; helper role diuji
  oleh unit test Android dan test cache DAO ditambahkan untuk connected test berikutnya.

### P1b — Gate kompatibilitas APK/backend (`DONE`, 28 Juli 2026)

Backend menyediakan policy per channel dengan minimum/latest version code, API
contract version, update URL, allowlist admin sistem, dan audit perubahan. Android
memeriksa policy sebelum pemulihan sesi dan saat resume, membedakan warning update
yang dapat dilanjutkan selama enforcement nonaktif dari hard block setelah
enforcement aktif, serta memakai cache maksimal 24 jam yang terikat pada
build/contract/channel yang sama. Request gate-enabled membawa header versi;
enforcement backend dapat diaktifkan setelah rollout untuk menolak APK legacy atau
inkompatibel dengan `426 UPGRADE_REQUIRED`.

Bukti implementasi:

- `backend/src/compatibility/`
- `backend/src/database/migrations/1753401600000-AddAppReleasePolicies.ts`
- Android `models/AppCompatibilityModels.kt`, `feature/compatibility/`, dan gate di
  `navigation/AppNavigation.kt`
- kontrak status/admin diuji lewat backend e2e dan keputusan gate diuji lewat unit
  test Android.

### P2 — Lifecycle akun, anggota, undangan, dan silsilah (`DONE`, 28 Juli 2026)

Vertical slice lifecycle membership sudah tersedia:

- seluruh anggota dapat melihat daftar anggota tanpa membuka email akun;
- `OWNER` dapat mengubah role anggota non-owner, mengeluarkan anggota, dan
  mentransfer kepemilikan secara atomik;
- `ADMIN` hanya dapat mengubah atau mengeluarkan `EDITOR` dan `VIEWER`;
- database mencegah lebih dari satu `OWNER` aktif pada satu silsilah;
- `OWNER` tidak dapat meninggalkan silsilah sebelum transfer ownership;
- anggota non-owner dapat keluar setelah mutation lokal selesai tersinkron;
- perubahan role, transfer, pengeluaran anggota, dan leave diaudit;
- Android membersihkan cache/queue space setelah leave atau setelah mendeteksi akses
  telah dicabut pada pemeriksaan resume berikutnya.

Vertical slice lifecycle lanjutan juga sudah tersedia:

- undangan aktif dapat dilihat menurut status dan dicabut tanpa menampilkan ulang
  token rahasia;
- penghapusan akun menampilkan dampak, memblokir OWNER yang belum transfer,
  memutus session/membership/claim/identity, menganonimkan akun, dan tidak menghapus
  record Person atau riwayat keluarga;
- Family Space harus melalui status `ARCHIVED` read-only sebelum dapat dihapus
  secara lunak dengan konfirmasi nama persis serta keputusan ekspor;
- undangan aktif dicabut ketika silsilah diarsipkan;
- Android menampilkan ringkasan dampak dan jalur ekspor, memblokir tindakan bila
  mutation lokal belum selesai, dan membersihkan cache setelah akses dihapus;
- akses aktif diperiksa saat resume dan setiap 60 detik selama aplikasi aktif.

Server menegakkan revocation pada setiap request. Pemeriksaan periodik Android
membatasi jendela UI lokal yang stale tanpa mengklaim push real-time.

Penghapusan data dummy langsung melalui database bukan fitur produk dan tidak boleh
dianggap sebagai penyelesaian lifecycle.

Acuan produk: bagian **Siklus Keluar, Penghapusan Akun, dan Data Person** pada
`docs/FRONTEND_GRAPH_WORKSPACE_DECISION.md`.

### P3 — Privacy granular dan claim kolektif (`PARTIAL`, pilot selesai)

Pilot privacy per person kini tersedia end-to-end:

- visibility `Keluarga`, `Terbatas`, dan `Privat`;
- person hidup/tidak diketahui baru default `Terbatas`, sedangkan person meninggal
  default `Keluarga`;
- keputusan akses mempertimbangkan role, verified claimant, dan temporary privacy
  manager;
- Kontributor tetap dapat mengedit person `Terbatas`, Pembaca hanya memperoleh
  struktur, dan person `Privat` hanya terbuka penuh bagi verified claimant atau
  temporary manager sebelum ada claim;
- redaksi diterapkan pada person, hubungan, path, claim, proposal, foto/media,
  sumber, duplicate review, export JSON/GEDCOM/backup, dan respons mutasi;
- pengaturan visibility tersedia pada profil lengkap dengan optimistic version;
- cache foto dibersihkan ketika akses menyempit, dan mutation offline sensitif tidak
  diterapkan ulang di atas data lokal yang telah teredaksi.

Claim baru sekarang memerlukan dua konfirmasi dari OWNER/ADMIN berbeda; pemilik
claim tidak dapat mengonfirmasi sendiri, retry konfirmator yang sama idempoten, dan
setiap konfirmasi diaudit. Claim lama yang telah terverifikasi dipertahankan sebagai
hasil legacy agar migration tidak menurunkan keputusan keluarga secara diam-diam.

Endpoint self-claim memastikan halaman akun memperoleh hubungan ke Person diri tanpa
menyamakan `User` dan `Person`. Hanya satu claim `PENDING` atau `VERIFIED` boleh aktif
untuk tuple Family Space, akun, dan Person; retry mengembalikan claim yang sama,
unique index mencegah race, dan migration mempertahankan satu record kanonik sambil
menandai duplikat aktif lama sebagai `REJECTED` agar audit trail tidak hilang.

P3 tetap `PARTIAL` karena scope per cabang/field kustom, privacy manager yang dapat
didelegasikan, request akses detail Person, status `Diperdebatkan`, versi alternatif,
dan dispute tanpa overwrite belum menjadi bagian pilot. Signed URL yang sudah terbit
berakhir menurut TTL; perangkat lain memperoleh redaksi pada refresh berikutnya.

### P4 — Relasi Foster dan Guardian (`DONE`, 28 Juli 2026)

Pilihan `FOSTER` dan `GUARDIAN` kini tersedia pada backend, Android, Room,
inspector, renderer, legend, export/cadangan, dan test. Periode serta konteks
perawatan dapat dicatat. Implementasi menjaga bahwa:

- foster/guardian tidak otomatis mengubah generation level;
- tidak ada inferensi partnership, parentage biologis, ACL, atau legalitas;
- inspector memakai label eksplisit;
- graph memakai care-relationship overlay/pola tersendiri;
- pola dapat dibedakan tanpa hanya mengandalkan warna.

Foster memakai pola dash-dot; Guardian memakai pola dashed dengan marker. Keduanya
ditampilkan sebagai overlay care ketika person terkait difokuskan dan sengaja
dikecualikan dari GEDCOM karena format tersebut tidak mempunyai pemetaan yang aman.

### P5 — Perluasan offline write (`PARTIAL`)

Room mutation queue saat ini hanya memuat:

- `CREATE_PERSON`;
- `UPDATE_LIFE_STATUS`;
- `UPDATE_PROFILE`;
- `ADD_PARENT_CHILD`;
- `ADD_SPOUSE`;
- `UPDATE_SPOUSE`;
- `DELETE_RELATIONSHIP`;
- `CREATE_SOURCE`.

Belum masuk queue:

- delete person;
- foto dan media;
- proposal/review;
- invitation dan lifecycle membership/silsilah.

Setiap perluasan harus mempunyai idempotency, optimistic state atau fallback yang
jelas, retry, conflict handling, rollback, dan indikator sync. Label status header
saat ini benar untuk mutation yang sudah terdaftar, tetapi belum mewakili tindakan
yang masih online-only.

Create person kini memakai card lokal optimistis dan remap ID atomik setelah server
menjawab; relationship serta payload mutation yang bergantung ikut diremap. Delete
relationship menyimpan snapshot untuk rollback, menganggap `404` sebagai konvergen,
dan membatalkan relasi lokal yang belum sync secara atomik. Keduanya memakai
`clientMutationId` idempoten dan audit server. Room instrumentation pada perangkat
serta continuity selection selama remap tetap menjadi acceptance final.

Regresi Silsilah Semarang menutup race dependency create-person → relationship:
unique work memakai `APPEND_OR_REPLACE`, satu repository hanya menjalankan satu batch,
urutan timestamp yang sama mendahulukan `CREATE_PERSON`, dan relationship dengan ID
`local-person-*` tidak dikirim. Refresh relationship juga merekonsiliasi mutation
lama bila fakta ekuivalen sudah tersimpan di server, sehingga item gagal usang tidak
terus ditawarkan untuk retry.

Create source kini memakai cache Room privacy-aware dan `clientMutationId` idempoten.
Catatan langsung tampil dengan label menunggu sinkronisasi, bertahan setelah process
death, ikut diremap bersama Person lokal, dan diganti dengan hasil server tanpa
duplikasi. Penolakan permanen me-rollback catatan optimistis; penyempitan privacy atau
pencabutan akses membersihkan cache sumber. Foto/binary, review, lifecycle, claim,
dan penghapusan tetap online-only karena membutuhkan otorisasi mutakhir, dampak
destruktif, atau transfer file yang tidak aman untuk queue generik.

Status pasangan kini dapat diubah menjadi `MARRIED`, `DIVORCED`, atau `WIDOWED` dari
Person Detail. `UPDATE_SPOUSE` menerapkan cache optimistis, retry idempoten, audit,
dan rollback ke snapshot relationship bila backend menolak permanen. Tanggal akhir
dibersihkan saat hubungan kembali aktif.

### P6 — Undangan tertarget dan dapat dicabut (`PARTIAL`)

Yang sudah ada:

- kode role berbeda: `FR-V`, `FR-K`, dan `FR-P`;
- token acak dan unik;
- single-use;
- expiry 1–30 hari;
- pembatasan pengangkatan pengelola oleh role yang tidak berhak.

Yang belum ada:

- anchor person dan scope cabang;
- batas tingkat detail;
- akses sementara setelah undangan diterima;
- QR invitation;
- usulan undangan oleh role yang tidak boleh mengundang langsung.

Revoke dan undangan tertarget email kini tersedia. Email dinormalisasi; hanya akun
yang cocok dapat preview/accept; mismatch tidak mengonsumsi token; UI dan riwayat
hanya menampilkan alamat yang dimasking. Undangan legacy tanpa target tetap
kompatibel selama masa transisi.

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

Repository kini mempunyai migration backend-only yang mengaktifkan RLS, mencabut
hak `PUBLIC`/`anon`/`authenticated`, dan mencabut eksekusi
`public.rls_auto_enable()` dari API roles. CI memeriksa drift tabel baru dan
migration PostgreSQL telah diuji pada cluster sementara. Saran `RLS Enabled No
Policy` tetap dipertahankan sebagai default-deny yang disengaja. P7 tetap
`EXTERNAL VERIFY` sampai pemilik menjalankan migration setelah backup dan menutup
bukti console/perangkat pada `docs/P7_CLOUD_SECURITY_CLOSURE_CHECKLIST.md`.

## 4. Backlog lanjutan

### P8 — Graph besar (`PARTIAL`)

Progressive expansion, filter generasi, deterministic placement, dan collision
avoidance telah dilengkapi viewport culling dengan overscan. Detail card memakai
tiga tingkat zoom; foto tidak dimuat pada mode compact/minimal. Lebih dari 800 card
aktif beralih ke daftar tekstual virtualized dengan urutan fokus lalu alfabetis.
Tidak ada animasi baru, sehingga preferensi reduced-motion tidak diabaikan. Layout
dan semantik care/lineage tidak diubah oleh optimasi render.

Audit keluarga Ummah Bugo menutup gap utama layout partnership historis pada 3
Agustus 2026. Resolver generasi kini menyelesaikan komponen parent-child lebih dahulu;
edge pasangan hanya boleh menyelaraskan komponen lineage yang belum mempunyai level
dan tidak dapat menimpa hasil biologis. `DIVORCED`/`WIDOWED` dikeluarkan dari komponen
placement keras, tetapi cincin setiap pasangan tetap menjadi anchor bagi anak bersama.
Reflow mengukur blok turunan, menempatkan pasangan lama ke sisi luar, dan melebarkan
jarak antarkoridor bila dua keluarga akan bertumpuk.

Fixture Sikem–Manto Karto Rejo, Sikem–Mbah Cangkring, dan Mbah Cangkring–Nn kini
menguji pasangan historis lunak, cincin tanpa anak, serta koridor terpisah untuk Karto
Setiko dan kelompok Kalinem/Kasinem/Lasiyem. Hubungan anomali Nn–Solihin dihapus dari
data Ummah Bugo; Solihin tetap anak biologis Harmanto dan Sri Lestari. P8 tetap
`PARTIAL` hanya untuk incremental reflow yang lebih lokal, dense-partnership stress
yang lebih luas, dan bukti performance gate tablet. Gate perubahan ini mencatat 144
unit test Android, lint, build debug, dan 40/40 connected instrumentation lulus pada
Samsung SM-T225; APK pilot build 5 juga terpasang dan cold-launch tanpa crash.

Minimap privacy-safe kini hanya memproyeksikan geometri netral dari node dan lineage
yang memang aktif/terlihat. Ia tidak membawa ID, nama, foto, umur, status, metadata,
tipe relasi, placeholder, atau jumlah; semantics aksesibilitasnya generik. Indikator
viewport dan navigasi ketuk-ke-tengah tersedia, sedangkan mode fallback lebih dari
800 card sengaja tidak menampilkan minimap. Projection, batas koordinat, pemusatan
viewport, kondisi tampil, dan larangan field identitas diuji melalui pure unit test.

P1 lanjutan untuk keluarga besar ditutup pada 30 Juli 2026: layout menjalankan
reservasi interval horizontal berbasis birth-family block dari generasi terdalam ke
atas, tanpa menambah level Y garis. Blok yang memiliki descendant bersama digabung
sebelum packing. Minimap kini dapat ditutup dan dipanggil kembali melalui `Alat`.
Pergantian Family Space juga tersedia dari menu akun tanpa logout atau menghapus
antrean sinkron workspace lain. P2 incremental reflow, P3 dense partnership stress,
dan P4 tablet performance gate tetap dilacak di
`docs/GRAPH_LARGE_FAMILY_LAYOUT_PROPOSAL.md`.

### P9 — Model data profil yang lebih kaya (`DEFERRED`)

Belum tersedia penuh:

- histori nama, alias multipel, nama lahir, dan gelar adat/agama terstruktur;
- tanggal parsial, kisaran, perkiraan, atau tidak diketahui;
- tempat terstruktur dan koordinat;
- timeline event bersama;
- provenance per field dan confidence/status verification.

### P10 — Kolaborasi dan notifikasi (`PARTIAL`)

Proposal approve/reject kini menyimpan reviewer, waktu review, dan alasan review.
Daftar Android menampilkan nilai ketika usulan dibuat, nilai terkini, nilai usulan,
serta catatan kontributor/reviewer. Penolakan dari client kontrak baru wajib memiliki
alasan; client lama tanpa header versi tetap kompatibel selama rollout. Daftar dan
aksi review mengikuti keputusan privacy person: konteks sensitif hanya tersedia bagi
actor dengan akses `FULL`.

Thread diskusi immutable kini tersedia pada setiap usulan. Semua role dapat membaca
atau menambah komentar hanya ketika mempunyai akses privacy `FULL` ke person target.
UI memuat thread saat pengguna membukanya, menjaga draft ketika gagal, dan tidak
mengekspos email/user ID. Audit komentar menyimpan ID proposal/komentar/actor/waktu
tanpa menyalin isi komentar sensitif ke activity log.

Notifikasi tindakan terkontrol kini tersedia sebagai dua lapis. Android menampilkan
banner singkat di lapisan teratas untuk sukses, konflik/peringatan, gagal, dan
`menunggu sinkronisasi`. Backend menyimpan receipt pribadi untuk mutation yang
berhasil atau mencapai handler tetapi gagal; preview memakai copy generik tanpa nama
Person, nilai keluarga, email, token, body request, atau error mentah. Riwayat dapat
dibaca dan ditandai pada Profil akun, hanya oleh pemiliknya, serta dihapus bersama
akun. Penolakan pada guard tetap tampil langsung di perangkat tetapi tidak dipaksa
masuk database karena controller tidak pernah dijalankan.

Preview aktivitas kolaborasi dan notifikasi pribadi dibatasi 10 item. Riwayat
kolaborasi lengkap tidak lagi dibuka otomatis untuk semua anggota: pengguna biasa
meminta akses, OWNER/ADMIN menyetujui atau menolak, dan hasil yang disetujui dimuat
dengan cursor maksimal 50 item per halaman. Aktivitas memakai display name akun dan
label `Anda` untuk actor aktif; UUID audit tetap berada di backend dan tidak menjadi
label UI. Halaman akun, header, dan avatar memakai self-claim serta shared photo state
agar foto pengganti langsung konsisten lintas halaman.

Yang belum tersedia:

- undo/restore berbasis audit;
- versi alternatif dan dispute kolaboratif.

### P11 — Visual regression dan accessibility acceptance (`PARTIAL`)

Masih perlu screenshot regression lintas ukuran/tema serta acceptance TalkBack, font
besar, contrast, keyboard, reduced motion, dan touch target pada perangkat nyata.
Matrix/alur/kriteria evidence sudah dibakukan pada
`docs/ACCESSIBILITY_ACCEPTANCE_CHECKLIST.md`.

Batch 2 Agustus menambah mitigasi sempit untuk header brand dan hero profil akun,
tetapi perbaikan responsif tersebut belum menggantikan acceptance matrix P11.

### P12 — Observability yang aman untuk privasi (`PARTIAL`)

Request correlation sudah tidak mencatat body/query/user/family values. Production
tetap memerlukan error/crash monitoring, redaction test, alerting, dashboard kesehatan,
dan aturan retensi log yang tidak mengubah aktivitas membaca menjadi telemetry.
Baseline data yang boleh dan dilarang dicatat berada pada runbook operasi P13.
Logging HTTP Android dimatikan juga pada debug karena level BASIC tetap membuka URL
yang dapat memuat token undangan dan identifier keluarga.

### P13 — Resilience dan operasi production (`PARTIAL`)

Baseline gate rollout, backup/restore drill, monitoring privat, klasifikasi insiden,
dan evidence penutupan tersedia di `docs/PRODUCTION_OPERATIONS_RUNBOOK.md`. SLA,
retention, backup/PITR, restore drill, disaster recovery, domain final, budget alert,
dan ownership operasional masih harus ditutup dengan bukti aktual milik pemilik.

### P14 — Release, signing, dan enforcement final (`DEFERRED`)

Production signing, store publication, reproducible artifact/provenance, rollout
bertahap backend → APK → minimum version, rollback, serta aktivasi enforcement
kompatibilitas adalah gerbang terakhir. Sesuai keputusan pengguna, P14 tidak
diaktifkan selama gap pengembangan sebelumnya belum dinyatakan selesai.

Source saat ini menyiapkan build 5 (`0.1.4-beta`) dan mewajibkan Google Web Client ID
valid pada build `pilot`/`release`. Rollout belum dapat dinyatakan selesai sebelum
migration/backend baru dideploy, repository variable build tersedia, policy PILOT
diperbarui, APK dipasang, dan acceptance Google/foto/aktivitas dilakukan pada
perangkat melalui USB.

Keputusan beta 3 Agustus 2026 membekukan build debug/pilot pada `versionCode 5`,
`versionName 0.1.4-beta`. Perbaikan beta tidak wajib menaikkan versionCode dan gate
startup tidak menutup aplikasi bila enforcement channel masih nonaktif. Ini tidak
mengubah kewajiban versionCode monoton, signing, rollout, dan enforcement untuk
production/store.

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

Urutan default untuk item yang masih terbuka bila pengguna tidak menetapkan prioritas
lain:

1. tentukan slice berikut P3 untuk scope/field privacy atau dispute;
2. tutup acceptance perangkat P5 dan pilih perluasan offline yang benar-benar aman;
3. tentukan scope cabang/detail/durasi P6;
4. selesaikan checklist eksternal P7 dan migration cloud yang tertunda;
5. lanjutkan P9 model profil/provenance dan P10 undo/dispute;
6. tutup evidence P11 accessibility serta visual regression;
7. tutup P12 observability dan P13 resilience/operasi dengan bukti aktual;
8. jalankan P14 signing, rollout artifact, dan enforcement kompatibilitas terakhir.

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

## 9. Hasil audit ulang 3 Agustus 2026

Audit dilakukan setelah `git pull --ff-only` membawa lokal dari `102e105` ke
`2b6726d`. Empat commit yang ditarik terdiri dari dua commit implementasi dan dua
merge commit PR #1–#2. Tidak ada divergensi lokal dan working tree bersih sebelum
pembaruan dokumentasi dimulai.

Bukti lokal yang dijalankan ulang:

- backend `lint:check` dan build lulus;
- backend 35 unit test dan 22 E2E lulus;
- Android `testDebugUnitTest`, `lintDebug`, dan `assembleDebug` lulus.

Status deployment tidak diinferensikan dari hasil tersebut. Migration history access
dan unique active claim, backend terbaru, Google Web Client ID, policy PILOT build 5,
serta acceptance perangkat tetap membutuhkan bukti eksternal sebelum ditandai
selesai.
