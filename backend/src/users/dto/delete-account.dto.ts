import { Equals } from 'class-validator';

export class DeleteAccountDto {
  @Equals('HAPUS AKUN')
  confirmation!: 'HAPUS AKUN';
}
