# Risalah Keputusan Cloud Pilot FamilyRoot

> **Status:** Disepakati sebagai arah pengujian awal
> **Tanggal keputusan:** 18 Juli 2026
> **Status implementasi:** Tahap 7 berjalan; fondasi repository siap, provisioning
> dan verifikasi cloud menunggu tindakan pemilik akun
> **Penjadwalan:** Ditetapkan sebagai arah Tahap 7 setelah kompleksitas lineage
> Tahap 6 ditutup
> **Ruang lingkup:** Hosting backend, database cloud, dan penyimpanan media untuk pilot
> **Tidak mengubah:** Arsitektur domain FamilyRoot, Blueprint v1, atau status Fase 1–4

## 1. Latar Belakang

FamilyRoot memerlukan lingkungan cloud untuk menguji backend melalui internet,
persistensi lintas perangkat, sinkronisasi offline, dan penyimpanan binary media.
Tahap pertama harus dapat memakai free plan atau free quota agar kelayakan teknis
dapat dibuktikan sebelum biaya infrastruktur produksi disetujui.

Mengganti backend NestJS dengan platform atau runtime lain ditolak sebagai arah
pilot karena akan memundurkan progres yang sudah selesai pada autentikasi, role,
aturan lineage, audit, idempotency, conflict resolution, dan kontrak API Android.

## 2. Keputusan

Pilot akan memakai kombinasi berikut:

```text
Android FamilyRoot
        ↓ HTTPS
Google Cloud Run — backend NestJS
Region: Singapore (`asia-southeast1`)
        ↓ koneksi PostgreSQL
Supabase Free — PostgreSQL
Region: Singapore
        ↓
Supabase Storage — private bucket
```

Cloud Run dipilih untuk menjalankan backend NestJS yang sudah ada. Supabase dipilih
sebagai penyedia PostgreSQL dan object storage pada tahap pilot. Kedua layanan
ditempatkan di Singapore agar runtime backend, database, dan storage berada di
kawasan yang sama.

Keputusan ini belum menetapkan platform produksi final. Setelah pilot, akan
dievaluasi apakah production tetap menggunakan kombinasi tersebut atau memindahkan
backend, PostgreSQL, dan object storage bersama-sama ke Google Cloud Jakarta.

## 3. Batas Arsitektur yang Dipertahankan

- Android hanya berkomunikasi dengan backend NestJS melalui HTTPS.
- Backend tetap menjadi source of truth dan penegak seluruh aturan bisnis.
- Supabase Auth, Edge Functions, dan Realtime tidak menggantikan mekanisme FamilyRoot.
- Model `User`, `Person`, `Family Space`, membership, dan role tetap dimiliki backend.
- JWT, rotating refresh session, audit, optimistic concurrency, dan idempotency tetap
  mengikuti implementasi FamilyRoot.
- Credential Supabase berprivilege tinggi tidak boleh berada di APK, source code,
  repository, log, atau export pengguna.
- PostgreSQL diakses melalui data layer/backend; Android tidak mengakses database
  Supabase secara langsung.

## 4. Arah Penyimpanan Media

Supabase Storage akan digunakan hanya melalui bucket privat. Alur target:

```text
Android meminta izin upload/download
        ↓
NestJS memvalidasi session, Family Space, dan role
        ↓
Backend memberikan otorisasi terbatas atau signed URL
        ↓
Android mentransfer file
        ↓
Backend mencatat metadata media dan audit yang relevan
```

Binary foto atau dokumen tidak disimpan sebagai blob di PostgreSQL. Tabel
`media_items` tetap menjadi metadata dan referensi terhadap object storage.

## 5. Adaptasi yang Diantisipasi

Implementasi baru boleh dimulai hanya setelah persetujuan terpisah. Adaptasi yang
diperkirakan diperlukan:

- menambahkan konfigurasi PostgreSQL melalui environment/secret;
- menyediakan TypeORM migration yang eksplisit dan menjaga `synchronize: false`
  untuk environment production-like;
- mengemas backend NestJS sebagai container Cloud Run yang stateless;
- membatasi jumlah instance dan connection pool agar tidak menghabiskan koneksi
  database Supabase;
- menyimpan JWT secret, database URL, dan credential storage di secret configuration;
- menambahkan abstraction/service penyimpanan media di belakang backend;
- menyediakan konfigurasi API base URL HTTPS untuk Android pilot;
- menyiapkan backup manual data uji karena free plan bukan perlindungan arsip
  keluarga produksi.

Adaptasi tersebut merupakan perubahan infrastruktur dan data provider, bukan
penggantian arsitektur produk atau penulisan ulang backend.

## 6. Acceptance Criteria Pilot

Pilot dianggap berhasil bila dapat membuktikan:

1. endpoint backend dan `/health` dapat diakses melalui HTTPS;
2. register, login, refresh rotation, logout, dan pemulihan session bekerja;
3. data PostgreSQL bertahan setelah Cloud Run restart atau scale-to-zero;
4. seed demo menghasilkan enam profil dan lineage yang benar;
5. dua perangkat/session melihat perubahan Family Space yang sama;
6. mutation queue Android dapat tersinkron kembali setelah offline;
7. conflict resolution dan idempotent retry tetap bekerja;
8. satu media dummy dapat disimpan di bucket privat dan hanya diakses melalui alur
   yang diotorisasi backend;
9. tidak ada secret atau data sensitif pada APK, repository, atau log;
10. data uji dapat diekspor sebelum project free dihentikan atau dihapus.

Pengujian tablet tetap dilakukan melalui USB debugging. Wireless ADB tidak digunakan.
Ketika backend sudah berada di cloud, ADB reverse tidak diperlukan untuk koneksi API,
tetapi kebijakan USB untuk install, instrumentation, logcat, dan smoke test tetap
berlaku.

## 7. Batas Free Plan dan Risiko Pilot

- Cloud Run mempunyai free usage quota, tetapi tetap memerlukan billing account dan
  penggunaan di luar quota dapat dikenai biaya.
- Supabase Free menyediakan PostgreSQL dan storage terbatas, dapat mem-pause project
  dengan aktivitas rendah, serta tidak menyediakan automated backup atau PITR.
- Lokasi free storage Google Cloud tidak mencakup Jakarta atau Singapore; karena itu
  object storage pilot dipilih dari Supabase Free.
- Traffic keluar Cloud Run menuju Supabase dapat menimbulkan biaya jaringan kecil.
- Hanya data seed atau dummy yang boleh digunakan sampai backup, retention, restore,
  privacy, dan konfigurasi produksi disetujui serta diuji.

Referensi batas layanan saat keputusan dibuat:

- [Google Cloud Free Program](https://docs.cloud.google.com/free/docs/free-cloud-features)
- [Cloud Run locations](https://docs.cloud.google.com/run/docs/locations)
- [Supabase pricing](https://supabase.com/pricing)
- [Supabase available regions](https://supabase.com/docs/guides/platform/regions)
- [Supabase free project pausing](https://supabase.com/docs/guides/platform/free-project-pausing)

## 8. Portabilitas dan Exit Strategy

- Supabase digunakan sebagai PostgreSQL standar; fitur proprietary database tidak
  boleh menjadi syarat inti domain FamilyRoot tanpa keputusan baru.
- Schema harus dikelola oleh TypeORM migration milik repository.
- Akses object storage ditempatkan di balik interface backend agar provider dapat
  diganti tanpa mengubah kontrak Android.
- Jalur migrasi produksi yang diantisipasi adalah PostgreSQL ke PostgreSQL dan object
  storage ke object storage.
- Migrasi ke Cloud SQL dan Cloud Storage Jakarta tetap menjadi opsi tanpa mengganti
  NestJS atau aturan domain.

## 9. Hal yang Belum Diputuskan

- Google Cloud project dan Supabase organization yang akan digunakan;
- batas anggaran dan billing alert;
- domain/subdomain API pilot;
- durasi pilot dan jumlah tester;
- ukuran serta jenis media dummy;
- konfigurasi connection pooling dan batas instance final;
- apakah dan bagaimana dua representasi Person milik User yang sama pada Family
  Space berbeda dapat dihubungkan dengan consent, field sharing opt-in, pencabutan,
  serta audit tanpa membuka lineage atau membership ruang lain;
- provider, region, SLA, backup retention, dan biaya production final.

Setiap implementasi, perubahan Blueprint, deployment, atau penggunaan data keluarga
nyata memerlukan persetujuan lanjutan.

Runbook implementasi dan checklist penutupan berada di
[`STEP7_CLOUD_PILOT_RUNBOOK.md`](STEP7_CLOUD_PILOT_RUNBOOK.md).
