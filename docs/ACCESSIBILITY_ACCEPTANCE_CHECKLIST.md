# TRêdhAH — Checklist Acceptance Accessibility

> Status: checklist wajib sebelum release; centang hanya berdasarkan pengujian nyata.

## Matrix perangkat

Uji minimal:

- ponsel compact portrait;
- ponsel landscape;
- tablet 7–8 inci;
- tablet besar;
- light dan dark theme;
- font sistem 100%, 150%, dan ukuran terbesar yang didukung;
- TalkBack aktif;
- keyboard/D-pad bila perangkat mendukung;
- opsi remove/reduce animation aktif.

## Alur kritis

Untuk setiap konfigurasi, selesaikan:

1. register/login email dan Google;
2. pilih, buat, dan gabung Family Space;
3. buka graph, pan/zoom, cari person, ganti fokus, dan gunakan fallback tekstual;
4. tambah/edit person dan hubungan biologis/adopsi/tiri/asuh/wali;
5. tinjau inspector/profil, foto, status privacy, claim, dan activity;
6. selesaikan conflict/retry offline;
7. buat/cabut undangan, ubah role, transfer ownership, leave;
8. ekspor data, arsipkan/pulihkan silsilah, dan buka dialog penghapusan;
9. buka Petunjuk dan Tentang.

## Kriteria penerimaan

- seluruh aksi mempunyai label TalkBack bermakna dan urutan fokus logis;
- informasi tidak dibedakan hanya dengan warna; care edge mempunyai pola/legend;
- teks tidak terpotong atau menimpa kontrol pada font besar;
- target sentuh minimum 48 dp untuk aksi interaktif;
- focus indicator terlihat dengan keyboard/D-pad;
- dialog berfokus pada judul/aksi aman dan dapat dibatalkan;
- status loading, sukses, error, konflik, offline, dan read-only diumumkan;
- graph mempunyai jalur daftar tekstual yang setara;
- reduced-motion tidak memicu animasi baru yang tidak diperlukan;
- contrast teks/ikon/control memenuhi baseline WCAG AA yang relevan;
- screenshot tidak membuka token, email penuh, data privat, atau identifier teknis.

## Bukti

Simpan per build:

- version code/name, commit, device, Android version, theme, font scale;
- hasil pass/fail per alur;
- screenshot/video yang sudah disanitasi;
- daftar defect dan severity;
- nama tester serta waktu acceptance.

Checklist belum `DONE` sampai seluruh alur kritis lulus pada tablet referensi dan
sekurangnya satu perangkat fisik kedua.
