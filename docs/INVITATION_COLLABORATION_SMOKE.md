# Smoke Test Undangan dan Kolaborasi

Tanggal uji: 21 Juli 2026

## Cakupan

Smoke test ini memverifikasi alur backend sebenarnya untuk:

1. pengelola ruang membuat undangan;
2. calon anggota melihat detail undangan;
3. calon anggota menerima undangan;
4. peran baru muncul pada daftar Family Space anggota;
5. hak kolaborasi setiap peran diterapkan; dan
6. token undangan yang sudah dipakai tidak dapat digunakan ulang.

Data pengujian undangan dibuat pada database SQLite in-memory dan dihapus saat
proses Jest selesai. Dengan demikian, pengujian dapat diulang tanpa menambah akun,
anggota, atau undangan pada database pilot.

## Cara menjalankan

Dari folder `backend`:

```powershell
npm run smoke:invitation:roles
```

## Matriks hasil

| Peran | Bergabung lewat undangan | Kolaborasi yang dibuktikan | Pembatasan yang dibuktikan | Hasil |
| --- | --- | --- | --- | --- |
| Pembaca (`VIEWER`) | Pratinjau dan menerima undangan | Membaca profil dan mengirim usulan perubahan | Tidak dapat membuat/mengubah profil secara langsung | BERHASIL |
| Kontributor (`EDITOR`) | Pratinjau dan menerima undangan | Membuat profil secara langsung | Tidak dapat mengekspor data atau membuat undangan | BERHASIL |
| Pengelola (`ADMIN`) | Pratinjau dan menerima undangan | Menyetujui usulan pembaca, mengekspor data, dan mengundang pembaca | Tidak dapat mengundang pengelola lain; hanya pemilik yang boleh | BERHASIL |
| Semua peran | Keanggotaan terlihat pada daftar ruang | Token hanya berlaku satu kali | Pemakaian ulang ditolak dengan HTTP 409 | BERHASIL |

Hasil eksekusi lokal:

- smoke undangan/peran: 1 test lulus;
- seluruh E2E backend: 9 test lulus;
- seluruh unit test backend: 13 test lulus; dan
- pemeriksaan lint: lulus.

## Verifikasi deployment pilot

Smoke kolaborasi reversibel juga dijalankan terhadap Cloud Run pilot untuk akun
dummy kontributor dan pengelola. Kedua skenario berhasil pada snapshot yang sama
berisi delapan profil: login dua sesi, perubahan lintas sesi, idempotensi,
konflik versi HTTP 409, rebase/retry, dan pemulihan data semuanya lulus. Semua
perubahan sementara dipulihkan dan semua sesi smoke ditutup.

Alur penerimaan undangan lengkap sengaja tidak dijalankan pada database pilot
karena backend belum menyediakan cleanup keanggotaan/undangan khusus smoke.
Kontrak alur tersebut diuji end-to-end melalui aplikasi Nest asli dengan database
in-memory. Pengujian UI Android untuk mengetik kode undangan tetap merupakan
lapisan terpisah dari smoke backend ini.
