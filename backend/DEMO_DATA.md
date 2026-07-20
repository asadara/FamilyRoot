# FamilyRoot Development Demo Data

Data ini hanya untuk pengembangan lokal dan pengujian. Seluruh alamat memakai domain
khusus `.test` dan password tidak boleh digunakan ulang untuk akun nyata.

Manifest kanoniknya berada di `src/dev/demo-data.ts`; `src/dev/seed-dev.ts` membaca
manifest tersebut untuk membangun database pengembangan secara deterministik.

| Peran | Email | Password | Membership |
|---|---|---|---|
| Ayah | `ayah@example.test` | `Test123456!` | Owner |
| Ibu | `ibu@example.test` | `Test123456!` | Admin |
| Anak | `anak@example.test` | `Test123456!` | Editor |
| Kakek | `kakek@example.test` | `Test123456!` | Viewer |

Seed membuat Family Space `Keluarga Demo`, person yang diklaim oleh keempat akun,
dua kerabat tambahan, relationship biological dan married, serta provenance/media/
proposal dummy yang dipakai untuk pengujian UI.

## Membuat data demo

```powershell
Set-Location backend
npm ci
Copy-Item .env.example .env
npm run seed:dev
```

Seed bersifat idempotent untuk data utama: menjalankannya lagi memperbarui profil akun
demo dan memastikan membership, claim, serta relationship yang diperlukan tersedia.
Seed menolak berjalan ketika `NODE_ENV=production`.

`dev.sqlite` tidak termasuk paket demo Git. File database runtime tersebut juga dapat
menampung akun lokal, refresh session, invitation, mutation queue, dan data e2e yang
tidak boleh diterbitkan. PC baru harus membangun database bersih melalui seed ini.
