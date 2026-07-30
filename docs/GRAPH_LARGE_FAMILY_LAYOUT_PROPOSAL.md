# Proposal Layout Graph Keluarga Besar

> **Status:** Proposal teknis untuk dibahas sebelum implementasi algoritme besar
> berikutnya  
> **Tanggal:** 30 Juli 2026  
> **Ruang lingkup:** Workspace graph Android; tidak mengubah relationship atau
> kontrak backend

## 1. Masalah yang Harus Diantisipasi

Jumlah person bukan satu-satunya ukuran kompleksitas. Layout lebih cepat menjadi
sulit ketika data mempunyai:

- banyak anak pada satu pasangan;
- beberapa pasangan historis pada person yang sama;
- anak dari ayah atau ibu yang berbeda;
- keluarga biologis pasangan yang sama-sama dibuka;
- beberapa blok keturunan lebar pada generasi yang sama;
- jalur graph yang bertemu kembali pada person yang sama;
- data orang tua atau tanggal lahir yang belum lengkap.

Karena itu, target algoritme tidak boleh hanya berupa “mampu merender N card”.
Targetnya adalah menjaga asosiasi visual keluarga tetap benar ketika graph melebar,
mendalam, dan mempunyai banyak partnership.

## 2. Invariant yang Tidak Boleh Dilanggar

1. Satu `personId` hanya mempunyai satu card.
2. Jarak dasar antarkartu konsisten di seluruh workspace.
3. Pasangan dan seluruh cabangnya diperlakukan sebagai unit, bukan card terpisah.
4. Garis anak bersama selalu turun dari junction/cincin pasangan yang benar.
5. Setiap keluarga anak memakai satu level garis horizontal pada gap generasinya;
   solusi tidak memakai garis horizontal bertingkat.
6. Jika dua koridor keluarga akan berpotongan, seluruh blok keluarga digeser
   horizontal ke interval kosong.
7. Pertukaran posisi pasangan menjadi constraint layout dan membawa blok leluhur
   masing-masing.
8. Penambahan data hanya merender ulang blok yang terdampak dan mempertahankan
   anchor viewport pengguna.
9. Urutan yang tidak didukung data tidak boleh ditampilkan sebagai fakta.
10. Data relationship tidak pernah diubah hanya untuk memperbaiki tampilan.

## 3. Unit Layout

Algoritme menggunakan empat unit logis:

- **Person card:** ukuran visual tetap.
- **Partnership unit:** seluruh pasangan terlihat yang terhubung pada satu person.
- **Birth-family block:** satu parent group, semua anaknya, pasangan anak, serta
  descendant block yang sedang terbuka.
- **Lineage corridor:** interval horizontal yang dipakai garis satu keluarga pada
  gap antara dua generasi.

Card tidak ditempatkan satu per satu secara global. Workspace lebih dahulu mengukur
unit dan blok, kemudian menempatkan blok tersebut.

## 4. Pipeline Layout yang Diusulkan

### A. Normalisasi graph

- indeks parent, child, dan partnership satu kali;
- kelompokkan anak berdasarkan parent set yang benar-benar tercatat;
- deteksi cycle dan hubungan rancu sebelum placement;
- deduplikasi person yang dicapai dari beberapa jalur.

### B. Pengukuran bottom-up

Untuk setiap birth-family block, hitung lebar:

```text
max(
  lebar pasangan/orang tua,
  jumlah lebar child block + gap konsisten
)
```

Pengukuran dimulai dari descendant terdalam menuju ancestor. In-law ancestry hanya
masuk ukuran saat cabangnya sedang terbuka.

### C. Constraint posisi

Constraint keras:

- generasi/y person;
- pasangan yang harus bersebelahan bila memungkinkan;
- urutan anak berbasis tanggal lahir;
- preferensi switch kiri/kanan pasangan;
- card yang dipertahankan sebagai viewport anchor.

Constraint lunak:

- perubahan posisi sekecil mungkin dari render sebelumnya;
- panjang garis sependek mungkin;
- blok keluarga tetap simetris terhadap junction jika ruang memungkinkan.

### D. Interval packing horizontal

Setiap generasi mempunyai interval terpakai. Blok ditempatkan dekat junction
orang tuanya, kemudian diperiksa terhadap interval blok lain.

Jika terjadi tabrakan:

1. hitung perpindahan horizontal minimum;
2. geser seluruh block beserta descendant atau ancestry yang dimilikinya;
3. perbarui interval terpakai;
4. jangan memindahkan card tunggal keluar dari unit partnership.

### E. Reservasi lineage corridor

Setiap parent group mereservasi interval garis horizontal pada satu nilai `y` di
tengah gap generasi. Dua keluarga pada gap yang sama tidak boleh memakai interval
yang saling menimpa.

Jika interval bertabrakan, layout menggeser birth-family block secara horizontal.
Nilai `y` garis tidak ditambah atau dibuat bertingkat.

### F. Incremental reflow

Ketika person atau relationship ditambahkan:

- tandai partnership, birth-family block, ancestor chain, dan generation interval
  yang terdampak;
- ukur ulang hanya bagian tersebut;
- pack ulang dari blok terdampak menuju luar;
- pertahankan posisi card pilihan sebagai anchor kamera.

## 5. Strategi Rendering

- Progressive expand/collapse tetap menjadi mekanisme utama.
- Node di luar viewport tidak perlu dikomposisikan, tetapi tetap mempunyai geometri
  ringan untuk minimap dan collision.
- Detail card turun sesuai zoom: penuh, compact, lalu minimal.
- Batas fallback 800 card tidak dinaikkan sebelum benchmark tablet membuktikan
  layout, gesture, export, dan accessibility tetap aman.
- Untuk graph sangat besar, `Fit visible` hanya menghitung cabang yang sedang
  terbuka, bukan seluruh person dalam Family Space.

## 6. Matriks Stress Test Minimum

Sebelum algoritme dinyatakan stabil, test sintetis harus mencakup:

- tujuh generasi dengan lebih dari 2.000 person;
- satu pasangan dengan 12 anak;
- satu person dengan tiga pasangan dan anak pada setiap partnership;
- kedua pasangan sama-sama pernah menikah dan membawa anak;
- dua blok in-law ancestry lebar yang dibuka bersamaan;
- anak tunggal, single parent, adoptive, dan step-parent;
- jalur graph yang bertemu kembali tanpa menduplikasi card;
- switch posisi pasangan saat seluruh ancestor dan descendant dibuka;
- penambahan person di tengah graph tanpa recenter penuh;
- tidak ada card overlap dan tidak ada corridor overlap.

Stress test awal tujuh generasi dengan 2.186 person sudah berjalan di bawah budget
fase pada mesin pengembangan. Test berikutnya harus memusatkan diri pada kepadatan
partnership dan benturan corridor, bukan hanya jumlah person.

## 7. Urutan Implementasi yang Direkomendasikan

1. **P1 — Corridor reservation:** model interval koridor dan perpindahan seluruh
   birth-family block.
2. **P2 — Incremental reflow:** cache ukuran blok dan hitung ulang bagian terdampak.
3. **P3 — Dense partnership stress:** kasus beberapa pasangan pada kedua pihak dan
   graph yang bertemu kembali.
4. **P4 — Tablet performance gate:** evaluasi batas 800 card, frame time, memory,
   minimap, export, dan accessibility.

Proposal ini mempertahankan keputusan produk untuk tidak memakai garis horizontal
bertingkat. Konsekuensinya, graph boleh menjadi lebih lebar ketika banyak keluarga
berada pada generasi yang sama; progressive expansion, minimap, dan viewport menjadi
alat utama untuk mengelola luas tersebut.
