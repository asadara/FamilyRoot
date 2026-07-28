# FamilyRoot — Runbook Operasi Production

> Status: baseline operasional; bukti pelaksanaan tetap wajib sebelum production.
> Dokumen ini tidak memberi izin deploy, migration cloud, atau aktivasi enforcement.

## 1. Batas dan penanggung jawab

Sebelum go-live, isi dan verifikasi:

- pemilik produk, database, cloud runtime, domain/DNS, Android signing, dan incident;
- jalur eskalasi serta kontak cadangan;
- region layanan, jam dukungan, target pemulihan, dan anggaran;
- lokasi backup terenkripsi yang hanya dapat diakses pemilik yang ditunjuk.

Jangan menaruh email privat, nomor telepon, token, password, credential database,
service-account key, atau signing material di repository.

## 2. Gate sebelum perubahan production

1. source, migration, backend contract, dan client kompatibel sudah ditinjau;
2. CI lint/build/unit/e2e/security lulus pada commit yang sama;
3. backup pre-migration selesai dan checksum/evidence disimpan di lokasi aman;
4. restore drill versi backup yang setara sudah pernah lulus;
5. rollback aplikasi dan database telah ditulis untuk perubahan tersebut;
6. policy versi masih menerima APK aktif selama rollout;
7. dashboard error, latency, database, storage, billing, dan quota siap dipantau;
8. release owner menyetujui jendela perubahan.

## 3. Urutan rollout

Urutan wajib:

```text
backup → migration kompatibel → backend → smoke test → APK → acceptance
→ minimum version/enforcement (terakhir)
```

Migration harus backward-compatible dengan APK yang masih aktif. P4 Foster/Guardian
adalah pengecualian penting: backend tidak boleh mulai mengirim metadata care kepada
build lama yang akan membacanya sebagai lineage. Gunakan policy minimum version hanya
setelah APK kompatibel tersedia dan acceptance lulus.

Jangan mengubah beberapa variabel besar sekaligus. Catat revision, image digest,
commit, migration terakhir, policy versi, waktu mulai/selesai, dan aktor.

## 4. Backup dan restore drill

Untuk setiap drill:

1. buat dataset dummy yang tidak memuat data keluarga nyata;
2. buat backup database dan inventaris object storage;
3. pulihkan ke environment terisolasi;
4. jalankan migration hingga versi target;
5. verifikasi jumlah tabel, foreign key, RLS/revoke, row penting, signed media access,
   login, daftar space, person, relationship, audit, dan export;
6. rekam durasi pemulihan aktual serta kehilangan data maksimum;
7. hapus environment drill setelah evidence aman disimpan.

Backup tanpa restore drill bukan bukti pemulihan.

## 5. Monitoring yang aman untuk privasi

Metric yang boleh dikumpulkan:

- health, status HTTP agregat, latency, restart, CPU/memory, pool database;
- ukuran database/storage, error rate, queue retry, quota, dan biaya;
- request ID acak untuk korelasi insiden.

Jangan mencatat body/query, token, email, nama person, `spaceId`, `personId`, foto,
cerita, relationship path, istilah pencarian, atau aktivitas membaca. Retensi log
harus minimum dan aksesnya diaudit.

## 6. Respons insiden

Klasifikasi awal:

- `SEV-1`: akses lintas keluarga, credential/signing bocor, kehilangan/korupsi data;
- `SEV-2`: login atau sinkronisasi mayor gagal, deployment tidak stabil;
- `SEV-3`: fungsi terbatas gagal tanpa risiko data;
- `SEV-4`: defect kosmetik/dokumentasi.

Respons minimum:

1. hentikan perubahan dan tunjuk incident lead;
2. pertahankan evidence tanpa membuka data sensitif;
3. batasi akses/revoke credential atau rollback bila aman;
4. verifikasi tenant boundary, audit, backup, dan dampak pengguna;
5. komunikasikan fakta yang sudah terverifikasi;
6. pulihkan layanan lalu lakukan post-incident review;
7. rotasi secret/signing material yang mungkin terekspos;
8. buat tindakan pencegahan dengan owner dan tenggat.

## 7. Evidence penutupan P13

P13 baru dapat dinyatakan selesai bila repository atau lokasi evidence aman mencatat:

- target SLA/RPO/RTO dan owner;
- backup terjadwal serta satu restore drill lulus;
- rollback drill;
- dashboard/alert dan uji notifikasi;
- billing budget alert;
- incident tabletop;
- retention log/database/media;
- hasil Security Advisor setelah migration;
- acceptance pada minimal dua perangkat fisik.

P14 release/signing/enforcement dimulai hanya setelah bukti ini diterima.
