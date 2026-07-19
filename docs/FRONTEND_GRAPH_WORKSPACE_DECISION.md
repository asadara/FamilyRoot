# Risalah Keputusan Frontend Graph Workspace FamilyRoot

> **Status:** Disepakati sebagai arah konsep frontend
> **Tanggal keputusan:** 18–19 Juli 2026
> **Status implementasi:** Tahap 1 vertical slice selesai dan tervalidasi lokal
> **Ruang lingkup:** Layout utama, graph workspace, person card, couple/lineage,
> expand/collapse, inspector, exploration, pencarian, navigation shell, visual
> language, media, privasi, kolaborasi, undangan, dan siklus hidup data
> **Tidak mengubah:** Blueprint v1, status Fase 1–4, source code, atau kontrak domain

## 1. Tujuan Konsep

Graph ditetapkan sebagai workspace utama FamilyRoot, bukan sekadar visualisasi
tambahan. Pengguna harus dapat menjelajahi keluarga besar melalui pan, zoom,
expand, collapse, pencarian, dan inspector tanpa kehilangan konteks kerja.

```text
Navigation shell
        ↓
Continuous graph workspace
        ↓
Person card + lineage connector
        ↓
Person inspector
        ↓
Full person profile untuk aktivitas mendalam
```

Konsep mengambil inspirasi umum dari genealogy workspace yang kompleks tanpa
menyalin mentah layout, visual language, navigasi, atau interaction design produk
lain. Identitas dan behaviour akhir harus menjadi milik FamilyRoot.

## 2. Layout Utama

### Tablet landscape

```text
┌───────────┬─────────────────────────────────────────────────────┐
│ FamilyRoot│ Family Space • Search • Sync                        │
├───────────┤                                      ┌──────────────┤
│ Pohon     │                                      │ Inspector    │
│ Beranda   │       GRAPH WORKSPACE DOMINAN        │ overlay      │
│ Keluarga  │                                      │ sementara    │
│ Aktivitas │ Minimap                  Toolbar     └──────────────┤
└───────────┴─────────────────────────────────────────────────────┘
```

Graph adalah panggung utama. Inspector menimpa sisi kanan workspace sebagai layer
sementara; ia tidak mempersempit, menyusun ulang, atau menggeser graph. Setelah
inspector ditutup, viewport terlihat persis seperti sebelumnya.

### Tablet portrait dan ponsel

- navigation rail berubah menjadi bottom navigation;
- graph tetap menggunakan ruang utama;
- inspector berubah menjadi bottom sheet;
- minimap tertutup secara default pada layar sempit;
- inspector dapat diperbesar menjadi profil lengkap;
- state workspace tetap sama ketika bentuk layout beradaptasi.

## 3. Person Card

Person card hanya menampilkan informasi minimum agar graph tetap dapat memuat banyak
orang:

- foto berbentuk lingkaran;
- nama singkat yang akrab di keluarga;
- umur untuk orang hidup jika dapat dihitung secara andal.

Orang hidup tidak diberi label `Hidup`. Jika tanggal lahir tidak cukup untuk
menghitung umur dengan benar, baris umur dihilangkan dan tidak diganti perkiraan
yang terlihat pasti.

Orang meninggal:

- seluruh card, foto, dan isinya dibuat abu-abu/desaturated;
- tidak menampilkan umur atau label kematian pada card;
- detail kehidupan dan kematian tersedia di inspector;
- accessibility semantics tetap menyampaikan statusnya.

Status `UNKNOWN` tidak dianggap meninggal. Card menggunakan tampilan netral tanpa
umur bila umur tidak dapat dihitung.

Prioritas gambar card:

1. foto profil asli;
2. avatar fallback laki-laki atau perempuan bila datanya tersedia;
3. avatar netral bila gender tidak diketahui.

Avatar dibedakan melalui ilustrasi, bukan hanya warna stereotip. FamilyRoot hanya
menyimpan satu foto profil aktif per person. Crop lingkaran hanya menjadi versi
tampilan; kebijakan media lengkap dijelaskan pada bagian 21.

Prioritas nama card:

1. nickname atau nama panggilan keluarga;
2. nama yang biasa digunakan;
3. full name sebagai fallback.

Card berbentuk portrait, mempunyai ukuran dasar yang konsisten, dan nama dibatasi
maksimal dua baris. Perbedaan panjang isi tidak mengubah ukuran luar card.

## 4. Graph Node Shell

Person card dibungkus graph node shell transparan.

```text
             expand parents
                   ▲
        ┌─────────────────────┐
        │   transparent shell │
        │  ┌───────────────┐  │
        │  │  PERSON CARD  │  │
        │  └───────────────┘  │
        │                 [⊕] │
        └─────────────────────┘
                   ▼
             expand children
```

Node shell tidak mempunyai background atau border dan tidak terlihat sebagai
lingkaran atau batas luar. Ia adalah konsep layout/hit-area logis, bukan wadah besar
yang menambah jarak antarperson. Ia menyediakan anchor dan area sentuh untuk:

- expand/collapse controls;
- tombol tambah hubungan;
- connector anchors;
- pending sync indicator;
- conflict indicator;
- selected state;
- touch target yang tetap memadai.

Batas visual utama hanya mengelilingi person card. Selection juga hanya memakai
outline card tanpa mengubah ukuran. Tombol tambah hubungan berupa `+` dalam tombol
lingkaran, hanya muncul pada card yang dipilih, dan diletakkan di sisi luar/corner
card agar tidak mengambil gap pasangan. Expand/collapse control mengambang pada
ruang antargenerasi yang sudah tersedia.

## 5. Selection dan System State

Ketika card diketuk:

- card mendapat border aksen;
- ukuran dan posisi card tidak berubah;
- inspector terbuka;
- graph tidak bergerak atau disusun ulang;
- tombol tambah hubungan muncul pada node shell terpilih.

Mengetuk card lain memindahkan selection dan memperbarui inspector. Mengetuk area
kosong menutup inspector dan melepaskan selection tanpa mengubah workspace.

Pending sync dan conflict dapat memakai ikon kecil pada node shell. Detail status
dan penyelesaiannya berada di inspector. Critical action tidak boleh bergantung pada
long press atau gesture tersembunyi.

## 6. Couple Unit

Pasangan ditampilkan sebagai dua card yang diletakkan sedekat mungkin tanpa
menabrakkan card, node shell, atau touch target.

```text
┌───────────┐   ⚭   ┌───────────┐
│ Person A  │       │ Person B  │
└───────────┘       └───────────┘
                    │
               child lineage
```

Hubungan couple tidak menggunakan garis horizontal. Ia menggunakan ikon FamilyRoot
berupa dua cincin/loop yang saling terkait. Ikon berada di tengah gap dan menjadi
junction untuk keturunan bersama.

Gap dihitung dari batas visual card, dengan baseline 24–32dp termasuk ikon cincin.
Ikon cincin adalah satu-satunya elemen di dalam gap. Tombol tambah tidak boleh
memisahkan pasangan. Collision detection boleh memperhitungkan touch target kontrol,
tetapi tidak menyediakan ruang untuk shell berbentuk lingkaran besar.

Status visual:

- hubungan berlangsung: cincin saling terkait;
- perceraian: dua cincin terlihat tetapi terpisah;
- berakhir karena kematian: cincin tetap terkait sebagai catatan sejarah tetapi
  dibuat muted;
- alasan dan tanggal lengkap tersedia di inspector;
- simbol patah hati atau coretan tidak digunakan.

## 7. Beberapa Pasangan dan Riwayat Hubungan

- Setiap person hanya mempunyai satu card dalam workspace.
- Setiap partnership mempunyai ikon, status, timeline, dan cabang anak tersendiri.
- Hubungan disusun secara historis.
- Hubungan yang lebih awal berkembang ke kiri.
- Hubungan yang lebih baru atau lebih dekat ke waktu sekarang berkembang ke kanan.
- Posisi tidak menunjukkan pasangan utama, peringkat, atau kedudukan.
- Perceraian, kematian, dan pernikahan baru tidak menghapus riwayat partnership.
- Pasangan baru tidak dihubungkan ke keluarga pasangan sebelumnya.
- Anak tetap berada pada partnership asalnya.

Aturan berlaku tanpa membedakan gender. Tanggal mulai hubungan menjadi dasar utama
urutan. Data yang tidak lengkap harus ditandai sebagai kronologi tidak pasti di
inspector dan tidak boleh diklaim sebagai fakta pasti.

## 8. Konektor Anak

```text
Parent A   ⚭   Parent B
               │
       ────────┼────────
       │       │       │
     Anak 1  Anak 2  Anak 3
```

Aturan connector:

1. garis turun vertikal dari pusat partnership;
2. garis kemudian bercabang horizontal sesuai jumlah anak;
3. setiap cabang turun vertikal menuju card anak;
4. satu anak memakai garis vertikal langsung tanpa cabang horizontal;
5. panjang garis dibuat sependek mungkin sesuai kebutuhan layout;
6. garis cukup tipis dan tenang tetapi tetap jelas serta memiliki contrast memadai;
7. connector tidak boleh menabrak card atau salah mengasosiasikan anak.

Anak dalam satu partnership diurutkan berdasarkan waktu kelahiran:

- tertua di kiri;
- semakin muda menuju kanan;
- status hidup atau meninggal tidak mengubah posisi;
- tanggal adopsi tidak mengubah urutan kelahiran;
- urutan yang tidak diketahui tidak boleh ditampilkan seolah-olah pasti.

## 9. Tipe Parentage dan Nasab

- biological: garis solid;
- adoptive: garis putus-putus;
- step-parent: garis titik-titik;
- tidak ada parentage yang disimpulkan hanya dari partnership.

Jika kedua orang tua mempunyai tipe parentage yang sama, garis dapat turun dari
partnership junction. Jika tipe parentage berbeda, masing-masing orang tua memiliki
garis tersendiri menuju anak.

```text
Ibu biological   ⚭   Ayah adoptive
      │                    ┊
      │ solid              ┊ dashed
      └──────── Anak ──────┘
```

Satu orang tua tidak membutuhkan pasangan dummy. Dua orang tua tidak otomatis
dianggap pasangan. Pernikahan baru tidak otomatis menciptakan hubungan parent-child
terhadap anak pasangan. Step-parent hanya ditampilkan bila relationship tersebut
dicatat secara eksplisit.

Ikon couple menjelaskan partnership. Garis parent-child menjelaskan parentage.
Keduanya tidak boleh saling mengasumsikan.

## 10. Expand dan Collapse

- Panah atas person membuka orang tua person tersebut.
- Panah bawah partnership membuka anak dari partnership tersebut.
- Person tanpa pasangan yang mempunyai anak dapat memiliki kontrol anak sendiri.
- Side control dapat membuka partnership historis tambahan secara kronologis.
- Panah hanya membuka data yang sudah ada.
- Tombol tambah hubungan digunakan untuk membuat data atau relationship baru.
- Satu tindakan pertama membuka satu generasi langsung.
- Panah hanya muncul bila ada relasi tersembunyi pada arah tersebut.
- Kontrol dapat menampilkan jumlah relasi langsung yang tersembunyi.

Collapse menyembunyikan seluruh cabang turunannya tetapi tidak mengubah data. Struktur
internal cabang diingat selama sesi workspace dan dipulihkan ketika dibuka kembali.

Jika selected person ikut tersembunyi:

- selection dilepas;
- inspector ditutup;
- workspace tidak otomatis memilih person lain.

Saat expand/collapse:

- card atau partnership asal tetap menjadi anchor;
- graph tidak melakukan re-center penuh;
- viewport hanya bergeser secukupnya bila node baru keluar layar;
- card dan connector berpindah dengan transisi ringan;
- reduced-motion preference harus dihormati.

## 11. Person Inspector

Tablet landscape memakai side inspector overlay. Layar yang lebih sempit memakai
bottom sheet. Inspector merupakan layer informasi, bukan navigasi yang mengganti
workspace. Lebar ideal tablet adalah 360dp, dengan rentang 320–420dp hanya jika
ukuran layar memungkinkan; graph harus tetap menjadi bagian dominan.

Behaviour:

- hanya satu inspector terbuka pada satu waktu;
- graph tetap dapat disentuh dan digerakkan;
- membuka inspector tidak menghitung ulang layout graph;
- memilih card lain memperbarui isi inspector;
- menutup inspector tidak mengubah pan, zoom, expansion, atau state graph lain;
- tombol Back menutup inspector sebelum meninggalkan workspace;
- perubahan orientasi mempertahankan selected person dan mengubah bentuk inspector;
- data inspector dimuat terpisah sehingga graph tidak diblokir;
- offline menampilkan cache serta freshness/sync state;
- edit yang belum disimpan tidak boleh hilang ketika pengguna berpindah person;
- section yang sedang digunakan dipertahankan ketika selected person berubah agar
  perbandingan data antarperson tidak memerlukan pembukaan section berulang;
- scroll position dipertahankan saat bentuk layout beradaptasi.

Header inspector menampilkan:

- foto lebih besar;
- nama akrab sebagai judul;
- nama lengkap bila berbeda;
- umur dan status kehidupan;
- tanggal serta tempat lahir;
- tanggal serta tempat meninggal bila tersedia;
- tindakan membuka profil lengkap.

Header tetap terlihat dan berubah menjadi versi ringkas saat konten digulir: foto
kecil, nama, dan tombol tutup. Aksi utama dibatasi pada `Lihat profil lengkap` dan
`Edit` sesuai izin. Aksi jarang digunakan masuk ke menu tambahan agar inspector
tetap menjadi tempat membaca, bukan panel administrasi.

## 12. Section Inspector

Inspector menggunakan expandable sections, bukan tab. Section pertama terbuka secara
default. Section lain tertutup dan muncul sesuai data yang telah diinput.

Urutan section:

1. **Identitas & Kehidupan**;
2. **Keluarga & Hubungan**;
3. **Peristiwa Kehidupan**;
4. **Cerita & Catatan**;
5. **Foto Profil & Arsip Eksternal**;
6. **Sumber & Status Informasi**;
7. **Riwayat Kontribusi**.

Urutan relatif tetap stabil meskipun beberapa section tidak tersedia. Pengguna boleh
membuka beberapa section sekaligus. Section kosong tidak memenuhi read mode dengan
pesan berulang; pengguna yang berhak mengedit memperoleh tindakan `Lengkapi profil`
atau `Tambah informasi` pada konteks yang tepat. `Identitas & Kehidupan` serta
`Keluarga & Hubungan` selalu tersedia. Section opsional hanya tampil bila berisi
data; VIEWER tidak melihat section kosong.

## 13. Reading, Editing, dan Profil Lengkap

Inspector terbuka dalam read mode. Pengeditan dilakukan per section.

- OWNER/ADMIN/EDITOR memperoleh tindakan `Edit` atau `Tambah` hanya dalam cakupan
  kewenangannya.
- VIEWER memperoleh tindakan `Usulkan perubahan` hanya pada data yang boleh dilihat.
- Form hanya mengganti section yang sedang diedit.
- Graph dan section lain tetap dapat dilihat.
- Berpindah person saat ada edit belum tersimpan memerlukan konfirmasi.
- Penyimpanan offline mengikuti mutation queue dan conflict contract.
- Setelah simpan, inspector kembali ke read mode dan menampilkan status sync.
- Delete, merge, dan tindakan sensitif tidak menjadi primary action inspector.

Inspector digunakan untuk membaca dan melakukan tindakan cepat. Profil lengkap
digunakan untuk:

- pengelolaan foto profil dan tautan arsip eksternal;
- timeline lengkap;
- cerita panjang;
- before/after proposal;
- histori perubahan;
- conflict kompleks;
- privacy;
- duplicate merge.

Kembali dari profil lengkap harus memulihkan graph, inspector, pan, zoom, expansion,
dan selection sebelumnya.

## 14. Initial Anchor, Selection, dan Exploration Focus

Tiga state dibedakan:

```text
Initial anchor
Profil diri saat workspace pertama dibuat.

Exploration focus
Titik awal pencarian jalur hubungan berikutnya.

Selected person
Person yang sedang ditampilkan di inspector.
```

Profil diri berdasarkan verified claim menjadi initial anchor pada pembukaan pertama.
Setelah itu FamilyRoot memakai continuous workspace tanpa root/focus permanen yang
menyusun ulang graph.

- Selection tidak mengubah layout atau kamera.
- Kunjungan berikutnya memulihkan workspace terakhir.
- Initial anchor digunakan lagi hanya bila tidak ada state valid yang dapat dipulihkan.
- `Temukan saya` menggerakkan kamera ke profil pengguna tanpa mereset workspace.
- Exploration focus berubah melalui hasil pencarian atau tindakan eksplisit, bukan
  melalui ketukan card biasa.

## 15. Viewport dan Minimap

Gesture dan kontrol:

- drag area kosong untuk pan;
- pinch untuk zoom;
- tombol `−/+` sebagai alternatif aksesibel;
- `Fit visible` untuk menampilkan graph yang sedang terbuka;
- minimap yang dapat ditampilkan/disembunyikan;
- `Temukan saya`;
- `Tengahkan card ini` dari inspector bila diminta pengguna.

Minimap:

- muncul pada tablet ketika graph cukup besar;
- tertutup secara default pada ponsel;
- menampilkan representasi node/cabang dan kotak viewport tanpa detail sensitif;
- dapat digunakan untuk memindahkan kamera;
- tidak mengubah graph atau selection.

State workspace disimpan per pengguna dan Family Space, termasuk pan, zoom, cabang
terbuka, kondisi minimap, dan selected person terakhir bila masih relevan.

## 16. Pencarian dan Relationship Path

Pencarian dapat dimulai dari exploration focus mana pun. Pada awal workspace,
exploration focus adalah profil diri. Hasil pencarian menjadi exploration focus baru
untuk pencarian berikutnya.

Breadcrumb hanya menunjukkan riwayat exploration focus:

```text
Saya › Hadi › Nur
```

Relationship path terpendek selalu ditampilkan sebagai teks di inspector:

```text
Hadi
  → ayah dari Budi
  → suami dari Siti
  → anak dari Nur
```

Pencarian tidak otomatis membuka lineage pada graph. Graph baru berubah ketika
pengguna menekan tindakan eksplisit:

```text
[ Tampilkan jalur di pohon ]
```

Setelah tindakan tersebut:

- hanya node minimum pada path yang dibuka;
- connector path disorot;
- cabang lain tidak berubah;
- viewport menyesuaikan agar jalur terlihat;
- inspector tetap terbuka;
- node yang sudah dibuka tidak otomatis ditutup setelah highlight selesai.

Jika tidak ada path, sistem menyatakan bahwa hubungan belum ditemukan dalam data dan
tidak menciptakan relationship otomatis.

## 17. Navigation Shell

Graph menjadi landing utama setelah login dan Family Space aktif.

Destinasi utama:

1. **Pohon**;
2. **Beranda**;
3. **Keluarga**;
4. **Aktivitas**.

Tablet memakai navigation rail. Layar sempit memakai bottom navigation. Profil akun
dan pengaturan Family Space berada di app bar karena tidak perlu memenuhi navigasi
utama.

App bar global memuat:

- identitas/pemilih Family Space;
- pencarian global;
- status sync/offline;
- notifikasi penting;
- avatar akun.

Toolbar zoom, minimap, fit visible, dan `Temukan saya` berada di dalam graph
workspace. Berpindah destinasi atau Family Space tidak menghapus state graph. Setiap
pengguna dan Family Space mempunyai state workspace tersendiri.

## 18. Prinsip Produk yang Dihasilkan

- Graph adalah workspace keluarga yang berkelanjutan.
- Lineage lebih penting daripada kerapian visual semu.
- Partnership tidak boleh mengasumsikan parentage.
- Data historis tidak boleh ditimpa hubungan terbaru.
- Selection tidak boleh merusak konteks eksplorasi.
- Informasi disajikan secara bertahap dari card menuju inspector dan profil penuh.
- Tindakan render atau mutasi berat dilakukan hanya atas permintaan pengguna.
- Offline, conflict, privacy, source, dan audit tetap terlihat tanpa memenuhi card.
- UI tidak membuat asumsi tersembunyi tentang gender, pasangan, parenthood, atau
  struktur keluarga.
- Android tetap client; backend tetap source of truth dan penegak aturan domain.

## 19. Identitas Visual

Karakter merek FamilyRoot adalah:

> Arsip keluarga modern yang hangat, tenang, tepercaya, dan kolaboratif.

Kolaboratif berarti kerja bersama dalam ruang keluarga privat, bukan feed atau
jejaring sosial publik.

Palet awal:

- muted/deep indigo sebagai warna utama;
- honey gold sebagai aksen hangat;
- soft periwinkle sebagai aksen kolaborasi;
- soft ivory sebagai background;
- cool gray untuk informasi historis/deceased.

Nilai warna eksplorasi awal adalah Deep Indigo `#434875`, Muted Indigo `#646A9B`,
Soft Periwinkle `#858CC7`, Honey `#D1A43C`, Pale Honey `#F2E6BC`, Soft Ivory
`#F7F5EF`, dan Cool Gray `#8A8D95`. Nilai tersebut belum menjadi token produksi;
contrast light/dark mode dan pemeriksaan pada perangkat fisik dapat mengubahnya.
Deep teal dan terracotta tidak digunakan sebagai arah tema utama.

Tipografi:

- Source Sans 3 untuk UI, navigation, card, form, dan data;
- Source Serif 4 secara terbatas untuk cerita, memorial, editorial, atau wordmark;
- nama memakai semibold dan tidak memakai all caps;
- font scaling sistem dan kebutuhan aksesibilitas harus tetap bekerja.

Bentuk dan elevation:

- rounded rectangle moderat, bukan pill berlebihan;
- card putih lembut di atas ivory dengan border tipis dan elevation ringan;
- selected state berupa outline indigo tanpa perubahan ukuran;
- inspector memakai permukaan tonal datar dan divider ringan;
- glassmorphism, gradient berat, shadow besar, serta pure black/white dihindari.

Ikon memakai rounded outline style. Ikon pasangan adalah aset FamilyRoot berupa dua
cincin yang jelas pada ukuran kecil. Connector normal netral, path aktif indigo, dan
endpoint pencarian dapat memakai honey. Dark mode memakai canvas deep
indigo-charcoal, card muted indigo-slate, aksi light periwinkle, honey yang diredupkan,
connector gray-lavender, dan teks off-white. Tema mengikuti sistem dengan pilihan
manual.

## 20. Baseline Design Tokens

Angka berikut adalah baseline awal, bukan ukuran kaku. Validasi visual pada tablet
referensi tetap menentukan penyesuaian akhir.

### Person dan graph

- person card: 120dp × 152dp;
- radius card: 14dp;
- internal padding: 12dp;
- foto card: 56dp;
- nama: 14sp semibold, maksimum dua baris;
- umur: 12sp;
- border normal: 1dp; selected: 2dp tanpa mengubah ukuran luar;
- area kontrol atas/bawah: 20–24dp di ruang antargenerasi;
- tombol tambah: visual 32dp di dalam touch target minimum 48dp;
- chevron: 20–24dp;
- couple gap: 24–32dp, termasuk cincin;
- sibling gap: 24–32dp;
- generation gap: 56–72dp;
- connector visual: sekitar 1.5dp;
- ikon cincin: 20–24dp.

### Shell aplikasi dan inspector

- navigation rail tablet: sekitar 80dp;
- app bar: sekitar 64dp;
- bottom navigation: sekitar 64dp;
- inspector tablet: ideal 360dp, minimum 320dp, maksimum 420dp;
- padding header inspector: 20dp;
- padding section: 16dp;
- foto header: 72dp;
- touch target minimum: 48dp;
- spacing memakai grid dasar 4dp.

Ukuran card tidak dikecilkan hanya untuk memaksa seluruh graph masuk layar. Pan,
zoom, progressive disclosure, dan viewport culling menangani graph besar. Exact
wireframe harus dibuat secara deterministik; mockup generatif sebelumnya hanya
referensi proporsi dan tidak menjadi sumber kebenaran lineage.

## 21. Isi Inspector dan Kebijakan Media

### Identitas & Kehidupan

Urutan informasi: nama lengkap, nama panggilan, nama lain, gender, tanggal/tempat
lahir, umur, tanggal/tempat meninggal, umur saat meninggal, dan ringkasan pemakaman
bila ada. Field kosong tidak ditampilkan. Tanggal parsial dan perkiraan harus
didukung serta dilabeli, misalnya `Sekitar 1940`. Umur hanya dihitung dari data yang
cukup andal.

### Keluarga & Hubungan

Orang tua, partnership, dan anak dikelompokkan sesuai struktur graph. Partnership
berurutan historis dan tidak memiliki pasangan “utama”. Anak dikelompokkan menurut
asal parentage/partnership. Parent lain yang belum tercatat tidak menghasilkan
pasangan otomatis. Mengetuk nama membuka inspector tanpa menggerakkan graph; aksi
terpisah `Tampilkan di pohon` membuka cabang seperlunya.

### Peristiwa Kehidupan

Timeline vertikal bergerak dari peristiwa tertua ke terbaru. Item dapat memuat nama,
tanggal/rentang, lokasi, konteks, dan status informasi. Peristiwa bersama seperti
pernikahan adalah satu record yang direferensikan oleh kedua person, bukan salinan
terpisah. Inspector menampilkan ringkasan; timeline penuh berada di profil lengkap.

### Cerita & Catatan

Cerita adalah narasi berjudul; catatan adalah konteks singkat atau informasi yang
masih perlu diperiksa. Hanya akun terverifikasi dengan izin kontribusi yang dapat
menambah cerita. Pembuat asli, waktu, serta setiap penyunting berikutnya wajib
tercatat. Atribusi pembuat tidak hilang ketika cerita diedit.

FamilyRoot tidak memiliki likes, follower, leaderboard, feed media, atau metrik
popularitas. Diskusi digunakan untuk konteks, koreksi, dan sejarah keluarga.

### Foto Profil & Arsip Eksternal

- FamilyRoot hanya menyimpan satu foto profil aktif per person;
- penggantian menghapus file profil lama setelah proses aman selesai, sementara
  metadata audit pergantian tetap ada;
- foto kenangan tidak disimpan atau disalin oleh FamilyRoot;
- foto kenangan diarahkan ke Google Drive, Google Photos, media sosial, atau layanan
  lain melalui tautan eksternal;
- FamilyRoot hanya menyimpan URL, judul, keterangan, kontributor, dan relasi
  person/peristiwa;
- hak akses file eksternal tetap dikelola layanan asal;
- tautan diberi penanda keluar aplikasi dan dapat dilaporkan bila rusak;
- tidak ada internal gallery, unggahan massal, hotlink otomatis, atau cache gambar
  eksternal permanen.

### Larangan dokumen formal

FamilyRoot tidak menyimpan maupun menautkan salinan KTP, paspor, Kartu Keluarga,
akta kelahiran/kematian, buku atau akta nikah/perceraian, ijazah, sertifikat resmi,
surat hukum, atau dokumen pemerintahan lain. Nomor identitas dan nomor dokumen resmi
juga tidak menjadi bagian profil FamilyRoot.

FamilyRoot bukan lembaga pembuktian identitas, legalitas hubungan, atau keaslian
dokumen negara. Informasi keluarga adalah kontribusi pengguna dan tidak boleh diberi
badge `resmi`, `legal`, atau `terverifikasi negara`.

### Sumber & Status Informasi

Status internal yang tersedia:

- `Cerita keluarga`;
- `Belum diperiksa`;
- `Dikonfirmasi keluarga`;
- `Diperdebatkan`;
- `Tidak diketahui`.

Status tersebut mencatat proses internal keluarga, bukan kebenaran hukum. Setiap
informasi dapat mencatat kontributor, narasumber keluarga, waktu pencatatan, konteks,
dan alasan perubahan status.

### Riwayat Kontribusi

Inspector menampilkan 3–5 perubahan terbaru dan tautan ke riwayat lengkap. Catatan
memuat aktor, waktu, jenis perubahan, alasan, serta status diterapkan/pending/ditolak/
conflict. Riwayat mencatat perubahan bermakna per proses simpan, bukan setiap
ketikan. Tidak ada pencatatan aktivitas membaca atau menjelajahi graph. Pemulihan
versi mengikuti izin; nilai sensitif yang sudah dihapus tidak dibuka kembali kepada
anggota yang tidak berhak.

## 22. Privasi Person dan Batas Akses

Seluruh FamilyRoot private by default. Tidak ada person, graph, cerita, hubungan,
atau Family Space yang dapat dicari publik. Bahkan data person yang telah meninggal
tidak otomatis menjadi publik.

Jika record person hidup telah terhubung ke akun pemiliknya, pemilik memiliki kontrol
privasi tertinggi atas informasi dirinya. Admin tidak dapat mengabaikan pilihan itu.
Tingkat tampilan person:

- `Keluarga`: foto, nama panggilan, umur, dan detail yang diizinkan;
- `Terbatas`: nama/posisi hubungan tanpa foto, umur, cerita, atau detail kehidupan;
- `Privat`: label `Anggota keluarga`, avatar netral, dan struktur minimum.

Person hidup yang belum memiliki akun memakai mode Terbatas dan dikelola sementara
oleh pengelola privasi yang ditunjuk. Setelah claim disetujui, kontrol beralih kepada
person tersebut.

Person di bawah umur memakai mode Terbatas secara default. Foto, umur, tanggal lahir,
lokasi, sekolah, kontak, cerita, dan tautan eksternal tidak tampil tanpa persetujuan
pengelola privasi anak. Karena aplikasi tidak memeriksa dokumen resmi, istilah ini
adalah fungsi internal dan bukan klaim wali sah. Ketika person dapat mengambil alih
akunnya sesuai kebijakan yang berlaku, kontrol privasi dialihkan kepadanya.

Izin memakai dua dimensi:

```text
Peran   = tindakan yang boleh dilakukan
Cakupan = person/cabang tempat tindakan itu berlaku
```

Baseline cakupan pilot: seluruh Family Space, cabang tertentu, atau diri sendiri.
Relationship graph tidak pernah otomatis menjadi ACL. Hubungan biologis, adopsi,
step, partnership, guardian, atau foster tidak dengan sendirinya memberi akses.
Admin juga tidak dapat memberikan cakupan lebih luas daripada cakupannya sendiri.

Di luar cakupan, graph dapat menampilkan akses dasar atau placeholder privat agar
lineage tidak terputus. Foto, umur, tanggal, cerita, event, arsip eksternal, sumber,
dan riwayat tidak dikirim ke client yang tidak berhak. Pencarian, autocomplete,
jumlah hasil, breadcrumb, minimap, dan relationship path tidak boleh membocorkan
identitas tersembunyi.

Permintaan detail dikirim kepada person terkait atau pengelola privasinya. Requester
memilih kategori dan alasan; penerima dapat memberi akses penuh, sebagian, sementara,
menolak, atau mencabutnya. Penolakan tidak harus menyertakan alasan. Semua keputusan
tercatat. Hak person hidup untuk menyembunyikan informasi dirinya tidak dapat
dikalahkan voting keluarga.

## 23. Tata Kelola dan Konfirmasi Kolektif

FamilyRoot memerlukan `Piagam Kolaborasi & Tanggung Jawab Data` pada onboarding dan
pengaturan Family Space. Prinsipnya:

- kontribusi selalu mempunyai atribusi;
- kontributor bertanggung jawab memiliki hak dan izin untuk membagikan informasi;
- FamilyRoot tidak menjamin keabsahan hukum atau kebenaran mutlak informasi;
- admin mengelola akses, keamanan, dan proses, bukan menjadi pusat kebenaran data;
- person hidup memegang kendali privasi atas dirinya;
- data tidak boleh dipakai untuk doxing, pelecehan, diskriminasi, atau kerugian;
- disclaimer tidak menghapus kewajiban FamilyRoot menjaga keamanan sistem;
- kebijakan privasi dan ketentuan legal final tetap memerlukan review profesional
  sebelum publikasi.

Konfirmasi Kolektif Keluarga bekerja pada informasi/claim tertentu, bukan memberi
badge kebenaran pada seluruh profil:

1. informasi baru berstatus `Belum diperiksa`;
2. baseline pilot memerlukan dua akun terverifikasi berbeda untuk status
   `Dikonfirmasi keluarga`; pembuat tidak dapat mengonfirmasi sendiri;
3. keberatan beralasan mengubah status menjadi `Diperdebatkan` tanpa menimpa data;
4. versi alternatif dapat hidup berdampingan;
5. anggota terkait diberi notifikasi;
6. kesepakatan dapat memilih versi aktif, sedangkan versi lain tetap dalam riwayat;
7. tanpa kesepakatan, status Diperdebatkan boleh dipertahankan;
8. admin memoderasi proses/pelanggaran dan tindakannya selalu diaudit.

`User terverifikasi` hanya berarti akun dan hak kontribusinya telah diterima dalam
Family Space. Istilah tersebut tidak membuktikan bahwa semua informasinya benar.

## 24. Undangan dan Pencegahan Penyebaran Akses

Model undangan adalah tertarget, scope-bound, least privilege, dan non-transitive.

- OWNER serta ADMIN dengan izin `Kelola Anggota` dapat mengundang;
- EDITOR/VIEWER hanya dapat mengusulkan undangan;
- pengangkatan ADMIN memerlukan konfirmasi OWNER;
- undangan ditujukan ke akun/alamat tertentu, single-use, dapat dibatalkan, dan
  kedaluwarsa setelah tujuh hari;
- tidak ada public join code, public share link, atau pencarian Family Space;
- penerima harus login dengan akun terverifikasi yang sesuai;
- sebelum dikirim, undangan menentukan anchor person, cabang, role, tingkat detail,
  dan durasi akses;
- default adalah VIEWER + cabang tertentu + informasi dasar;
- menerima undangan tidak otomatis mengklaim person di graph;
- claim person merupakan proses terpisah dengan dua konfirmasi anggota relevan;
- memperluas scope, menghubungkan relationship, atau menghubungkan Family Space tidak
  mewariskan atau menggabungkan izin;
- izin membaca tidak otomatis memberi izin edit, export, atau invite;
- akses besar, sementara, dan perubahan role mempunyai audit serta notifikasi.

Ketika akses dicabut, server menghentikan akses dan client menghapus data cache yang
tidak lagi diizinkan pada sinkronisasi berikutnya. Cache sensitif harus terenkripsi
dan memvalidasi versi izin secara berkala. Produk harus jujur bahwa data yang sudah
pernah dilihat dapat saja telah disalin atau di-screenshot; pencegahan utama adalah
pemberian akses minimum sejak awal.

## 25. Siklus Keluar, Penghapusan Akun, dan Data Person

Keputusan penutup untuk siklus hidup data:

- meninggalkan Family Space hanya menghapus membership dan akses, bukan otomatis
  menghapus sejarah keluarga bersama;
- menghapus akun memutus session, membership, invitation, dan claim, tetapi record
  `Person` tetap entitas terpisah;
- sebelum keluar/hapus akun, user memperoleh ringkasan dampak dan kesempatan export
  atas data yang memang boleh dibawanya;
- fakta terstruktur dan relationship yang melibatkan banyak person tidak hilang
  sepihak; user dapat menyembunyikan data dirinya, mengajukan koreksi, atau membuka
  dispute;
- cerita tetap mempunyai pembuat asli. Pembuat dapat meminta cerita miliknya
  ditarik; konten kemudian hilang dari pembaca dan audit hanya menyimpan tombstone
  minimum tanpa mengungkap isi;
- kontribusi fakta yang tetap dipakai dapat diberi atribusi `Kontributor terdahulu`
  atau identitas yang disetujui user;
- person hidup dapat meminta detail dirinya disembunyikan, dianonimkan, atau dihapus.
  Bila node diperlukan untuk struktur keluarga lain, graph memakai placeholder
  privat tanpa detail personal;
- penghapusan relation yang juga menyatakan hubungan person lain menggunakan proses
  koreksi/dispute, bukan hard delete sepihak;
- cache perangkat, indeks pencarian, dan turunan media harus ikut dipurge setelah
  keputusan penghapusan tersinkronisasi;
- backup mempunyai retention terbatas dan prosedur purge yang didokumentasikan;
- admin tidak boleh menghalangi penghapusan akun, tetapi proses keamanan dapat
  memastikan request benar-benar berasal dari pemilik akun.

Aturan tersebut menjaga hak person tanpa membuat arsip kolektif dan lineage rusak
diam-diam. Detail periode retention dan redaksi legal ditentukan sebelum produksi.

## 26. Relasi Foster, Guardian, dan Kasus Khusus

Relasi tidak boleh dipaksa menjadi biological/adoptive/step hanya agar mudah dirender.
Baseline domain visual:

- `BIOLOGICAL`: parentage solid;
- `ADOPTIVE`: parentage dashed dan dapat menjadi cabang lineage yang eksplisit;
- `STEP`: parentage dotted, hanya jika dicatat eksplisit;
- `FOSTER`: hubungan pengasuhan sementara, bukan parentage otomatis;
- `GUARDIAN`: tanggung jawab pengasuhan/perwalian dalam konteks keluarga, bukan
  klaim wali legal atau hubungan darah.

Foster dan guardian tampil sebagai label jelas di inspector. Pada graph, keduanya
berada pada optional care-relationship overlay atau muncul saat person terkait
difokuskan; keduanya tidak mengubah generation level dan tidak memakai junction
couple. Guardian dapat memakai ikon shield kecil, sedangkan foster memakai pola
dash-dot yang berbeda dari adoptive. Semua pola juga harus dibedakan lewat legend
dan accessibility semantics, bukan warna saja.

Satu person dapat memiliki beberapa tipe hubungan pada periode berbeda. Setiap relasi
mempunyai waktu dan konteksnya sendiri; tidak ada inferensi partnership, parentage,
hak akses, atau legalitas. Donor, surrogacy, dan tipe sangat khusus tidak dipaksakan
ke baseline; sampai domainnya dibahas, gunakan relationship berlabel eksplisit yang
tidak memengaruhi nasab utama.

## 27. Adaptasi Tablet Portrait dan Ponsel

Graph tetap menjadi landing dan bidang dominan pada semua ukuran layar.

### Tablet landscape

- navigation rail kiri dan app bar atas;
- inspector overlay kanan;
- minimap tersedia jika graph cukup besar;
- graph tidak reflow saat inspector dibuka.

### Tablet portrait

- navigation rail berubah menjadi bottom navigation bila ruang tidak cukup;
- inspector menjadi bottom sheet dengan posisi ringkas, setengah, dan penuh;
- minimap tertutup secara default;
- toolbar graph diringkas tetapi zoom, fit, dan Temukan Saya tetap aksesibel.

### Ponsel

- bottom navigation dan compact app bar;
- inspector muncul sebagai bottom sheet; swipe penuh membuka profil lengkap;
- card tidak diperkecil di bawah baseline keterbacaan;
- couple tetap horizontal dan graph dapat dipan, bukan ditumpuk menjadi struktur
  yang mengubah makna lineage;
- minimap tidak tampil default;
- breadcrumb relationship path memakai bentuk horizontal yang dapat digulir;
- Back menutup edit, lalu inspector, baru meninggalkan workspace.

Perubahan orientasi dan ukuran mempertahankan pan, zoom, expansion, selection,
exploration focus, section inspector, draft edit, serta status sync. Daftar hubungan
tekstual yang aksesibel tetap tersedia sebagai alternatif graph, bukan pengganti
layout utama.

## 28. Loading, Offline, Conflict, dan Graph Besar

### Loading, empty, dan error

- workspace shell tampil segera; loading per branch memakai skeleton ringan;
- spinner global tidak menutup graph yang sudah dapat digunakan;
- empty state membedakan belum ada person, belum sinkron, dan tidak punya izin;
- error branch bersifat lokal dengan retry; graph lain tidak hilang;
- pesan tidak boleh membedakan “tidak ada” dan “disembunyikan” bila hal itu dapat
  membocorkan data privat.

### Offline dan sinkronisasi

- cache yang sah tetap dapat dibaca offline dengan penanda freshness;
- status offline global tenang dan tidak menutupi workspace;
- mutation pending memakai ikon kecil pada node shell/inspector;
- sinkronisasi sukses menghilangkan indikator tanpa recenter;
- tindakan yang memerlukan approval atau data sensitif dapat menunggu koneksi;
- revocation dan perubahan scope memicu purge cache pada sinkronisasi berikutnya.

### Conflict

- conflict tidak menimpa versi mana pun secara diam-diam;
- inspector menunjukkan field yang berbeda, kontributor, dan waktu;
- pilihan dapat berupa gunakan versi server, pertahankan usulan saya, atau gabungkan
  field yang tidak bertentangan;
- conflict kompleks berpindah ke profil lengkap;
- penyelesaian tidak mengubah viewport graph.

### Strategi graph besar

- hanya node/connector di sekitar viewport yang dirender penuh;
- expansion membuka satu generasi dan branch secara progresif;
- layout branch, ukuran node, dan connector path di-cache;
- label, foto, dan detail dimuat sesuai tingkat zoom dan visibility;
- minimap memakai agregat tanpa data sensitif;
- animasi dibatasi saat perubahan node besar dan reduced motion dihormati;
- pencarian menampilkan path teks terlebih dahulu; path graph hanya dirender setelah
  tindakan `Tampilkan jalur di pohon`;
- renderer tidak pernah membuka seluruh keluarga besar secara otomatis;
- fallback tekstual tetap dapat digunakan bila renderer mencapai batas perangkat.

Analitik performa dan crash log tidak boleh membawa nama, tanggal lahir, relationship,
cerita, URL arsip, atau data keluarga lain.

## 29. Dampak terhadap Implementasi yang Ada

Dokumen ini adalah target produk, bukan pernyataan bahwa seluruh domain telah ada.
Gap yang harus dipetakan sebelum implementasi antara lain:

- field-level privacy dan role × scope authorization;
- claim dengan konfirmasi kolektif dan pengelola privasi;
- invitation scope, expiry, dan non-transitive access;
- status informasi, versi alternatif, dispute, dan audit terperinci;
- timeline event bersama;
- satu binary foto profil dan lifecycle penggantian/purge;
- external archive links tanpa mirroring;
- penghapusan/penyamaran account, contribution, person, dan cache;
- Foster/Guardian dan care-relationship overlay;
- renderer viewport-culling dan adaptive inspector;
- penghapusan target penyimpanan dokumen formal serta field nomor identitas dari
  pengalaman produk.

Kontrak backend, Room schema, migration, UI production, Blueprint v2, dan kebijakan
legal tidak berubah otomatis oleh risalah ini. Masing-masing memerlukan perencanaan,
review, implementasi, migration, serta pengujian tersendiri.

## 30. Status Penutupan dan Implementasi

Pembahasan arah frontend graph workspace dinyatakan selesai sebagai baseline acuan.
Keputusan di dalam dokumen ini menggantikan agenda terbuka pada versi risalah
sebelumnya. Ukuran token dan nilai warna tetap boleh disesuaikan melalui wireframe,
contrast check, serta usability test tanpa mengubah prinsip produk.

Implementasi dimulai setelah persetujuan eksplisit pengguna pada 19 Juli 2026.
Vertical slice Tahap 1 yang telah selesai mencakup:

- custom light/dark color scheme FamilyRoot dan baseline typography;
- graph sebagai landing setelah login dan Family Space aktif;
- person card portrait dengan avatar fallback, nama singkat, umur hidup, serta
  treatment muted untuk deceased;
- couple gap ringkas dengan ikon cincin dan tanpa spouse line;
- connector lineage tipis dengan pola biological/adoptive/step;
- expand/collapse satu generasi pada arah orang tua dan anak;
- pan, pinch zoom, tombol zoom, fit/recenter, serta clipping viewport;
- initial anchor terpisah dari selected person;
- selection outline dan tombol tambah di sisi luar couple;
- inspector tablet sebagai overlay tanpa reflow atau perubahan viewport;
- expandable inspector sections untuk data yang sudah tersedia;
- adaptive bottom-sheet form factor untuk layar di bawah breakpoint tablet lebar.

Validasi Tahap 1:

- `testDebugUnitTest`, `lintDebug`, dan `assembleDebug` lulus;
- 13 connected instrumentation tests lulus pada Samsung SM-T225 Android 14;
- APK dipasang dan smoke-tested melalui USB ADB saja;
- lineage seed Hadi→Budi, Nur→Siti, Budi–Siti→Raka, serta Raka–Alya tampil benar;
- membuka inspector tidak menyusun ulang graph dan tidak menghasilkan crash log.

Tahap 2 yang selesai pada 19 Juli 2026 mencakup:

- navigation shell adaptif dengan rail kiri pada tablet landscape dan bottom
  navigation pada layar sempit;
- empat destinasi utama sesuai baseline: Pohon, Beranda, Keluarga, dan Aktivitas;
- penghapusan tombol navigasi lama yang menduplikasi fungsi shell;
- state collapse, zoom, dan posisi viewport dipertahankan ketika pengguna berpindah
  destinasi melalui shell lalu kembali ke Pohon;
- collapse cabang anak tidak lagi menampilkan placeholder atau label `hidden`;
- panah arah atas, bawah, kiri, atau kanan hanya muncul bila data relasi pada arah
  tersebut memang tersedia;
- saudara dengan orang tua bersama dideteksi dari relationship yang tersimpan,
  diurutkan menurut kelahiran, lalu dapat di-expand/collapse dari sisi center node;
- tombol tambah pada node terpilih tetap menjadi jalur untuk menambah atau mengelola
  relasi ketika data pada suatu arah belum tersedia;
- gesture pan memakai state transform stabil dan pembaruan langsung pada graphics
  layer, sehingga handler tidak dibuat ulang pada setiap frame pergeseran.

Validasi Tahap 2:

- `testDebugUnitTest`, `lintDebug`, dan `assembleDebug` lulus, termasuk regression
  test untuk deteksi saudara dari orang tua bersama;
- 13 connected instrumentation tests kembali lulus pada Samsung SM-T225 Android 14;
- APK dipasang melalui USB ADB dan diperiksa pada portrait serta landscape;
- collapse orang tua dan anak sama-sama menghilangkan card sepenuhnya;
- status collapse anak tetap tersimpan setelah berpindah Pohon ke Beranda dan kembali;
- regression instrumentation test satu-jari memastikan swipe menggeser graph tanpa
  salah membuka inspector atau memilih person;
- tidak ditemukan crash `AndroidRuntime` pada smoke test.

Tahap 3 yang selesai pada 19 Juli 2026 mencakup:

- global app bar adaptif yang menampilkan identitas Family Space, status
  online/offline atau jumlah antrean sync, dan avatar singkat akun;
- pencarian person global berbasis cache lokal yang sudah tersedia;
- hasil pencarian dibatasi dan ditampilkan sebagai daftar ringkas tanpa membuka
  cabang lineage secara otomatis;
- memilih hasil membawa pengguna ke graph dan membuka inspector person tersebut,
  tetapi tidak mengganti initial center, zoom, pan, atau expansion state workspace;
- field pencarian compact otomatis memperoleh fokus, sedangkan tablet landscape
  menampilkan field pencarian langsung;
- toolbar internal graph diringkas agar penambahan global app bar tidak mengambil
  porsi berlebihan dari workspace.

Validasi Tahap 3:

- `testDebugUnitTest`, `lintDebug`, dan `assembleDebug` lulus;
- seluruh 15 connected instrumentation tests lulus pada Samsung SM-T225 Android 14;
- pencarian diuji pada compact dan landscape, termasuk pemilihan hasil hingga
  inspector terbuka;
- app bar dan graph diperiksa pada portrait serta landscape;
- tidak ada perubahan endpoint, kontrak API, tabel, migration, atau backend NestJS.

Tahap 4 yang selesai pada 19 Juli 2026 mencakup:

- pencarian menghitung jalur hubungan terpendek dari fokus eksplorasi terakhir
  menggunakan data relationship pada cache lokal;
- hasil jalur selalu disajikan lebih dahulu sebagai teks di inspector, sehingga
  pencarian tidak otomatis membuka cabang atau memindahkan viewport graph;
- fokus eksplorasi berpindah saat hasil pencarian dipilih, sedangkan memilih card
  biasa hanya membuka inspector dan tidak mengubah fokus tersebut;
- riwayat fokus eksplorasi ditampilkan sebagai breadcrumb horizontal yang dapat
  digulir tanpa mengambil area utama graph secara berlebihan;
- breadcrumb memiliki aksi tutup yang hanya menyembunyikan tampilannya; fokus
  eksplorasi tetap tersimpan dan breadcrumb muncul kembali pada pencarian berikutnya;
- tombol eksplisit `Tampilkan jalur di pohon` membuka cabang relevan yang tersedia,
  menyorot person dan connector pada jalur, lalu menyesuaikan viewport agar jalur
  terlihat;
- cabang lain, state collapse/expand, dan inspector yang sedang terbuka tetap
  dipertahankan;
- hubungan parent-child biological/adoptive dan pasangan dibaca dua arah untuk
  menemukan jalur minimum; kondisi tidak terhubung ditampilkan sebagai informasi,
  bukan error navigasi.
- field pencarian landscape menggunakan ukuran ringkas 220 × 44 dp sebagai baseline;
- toolbar sekunder `Workspace graph` dihapus agar graph langsung mengisi area di bawah
  app bar; PDF, PNG, dan Atur ulang ditempatkan di navigation rail setelah Aktivitas,
  dengan padanan menu `Alat` pada layout bottom navigation.
- card, connector, dan cincin pasangan yang termasuk jalur pencarian memakai token
  tertiary orange terang (`#E66A00` pada light theme dan `#FFB36B` pada dark theme),
  sedangkan elemen di luar jalur tetap memakai warna netral atau relationship biasa;
- dropdown hasil pencarian tidak mengambil fokus input, sehingga keypad tetap terbuka
  selama pengguna mengetik dan baru ditutup setelah hasil dipilih, pencarian dibatalkan,
  atau daftar hasil ditutup.
- header landscape menampilkan nama akun dan label `Akun saya` di samping avatar;
  avatar membuka ringkasan nama serta email akun, sementara Pengaturan Family Space
  dan Keluar menjadi aksi terpisah di dalam menu tersebut.

Validasi Tahap 4:

- `testDebugUnitTest`, `lintDebug`, dan `assembleDebug` lulus;
- tes unit mencakup arah jalur forward/reverse, perpindahan fokus eksplorasi, dan
  kondisi person yang tidak terhubung;
- tes instrumentasi khusus memastikan graph belum berubah sebelum aksi opt-in;
- seluruh 17 connected instrumentation tests lulus pada Samsung SM-T225 Android 14;
- tidak ada perubahan endpoint, kontrak API, tabel, migration, atau backend NestJS.

Tahap 5 yang selesai pada 19 Juli 2026 mencakup vertical slice renderer lintas
generasi untuk relationship path:

- aksi `Tampilkan jalur di pohon` menambahkan hanya person pada jalur terpendek yang
  belum berada di neighborhood graph awal;
- parent ditempatkan pada rank di atas, child pada rank di bawah, dan pasangan pada
  rank yang sama dengan marker cincin tanpa spouse line;
- penempatan tambahan menghindari tabrakan dengan card yang sudah terbuka, memperluas
  batas kanvas seperlunya, lalu memakai viewport-fit jalur yang sudah ada;
- connector biological/adoptive/step tetap mempertahankan pola masing-masing dan
  seluruh elemen path memakai highlight orange tema;
- card tambahan dapat dipilih seperti card biasa dan hilang kembali saat konteks
  relationship path dibersihkan; cabang lain tidak dibuka otomatis.

Validasi Tahap 5:

- `testDebugUnitTest`, `lintDebug`, dan `assembleDebug` lulus;
- regression instrumentation test Budi → Hadi → Mbah memastikan generasi kedua
  belum dirender sebelum aksi opt-in dan muncul sesudahnya;
- seluruh 18 connected instrumentation tests lulus pada Samsung SM-T225 Android 14;
- tidak ada perubahan endpoint, kontrak API, tabel, migration, atau backend NestJS.

Belum dikerjakan setelah Tahap 5: expand/collapse rekursif bebas pada setiap node di
luar konteks relationship path, multiple historical partnership lengkap, foto profil
binary, privacy role × scope, approval, collective confirmation, lifecycle purge,
dan perubahan kontrak backend. Blueprint v1, database, backend NestJS, serta
deployment tidak diubah.
