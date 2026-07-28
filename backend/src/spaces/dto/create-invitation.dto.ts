import { Transform } from 'class-transformer';
import {
  IsEmail,
  IsIn,
  IsInt,
  IsOptional,
  IsUUID,
  Max,
  MaxLength,
  Min,
} from 'class-validator';

export class CreateInvitationDto {
  @IsUUID()
  spaceId!: string;

  @IsIn(['ADMIN', 'EDITOR', 'VIEWER'])
  role!: 'ADMIN' | 'EDITOR' | 'VIEWER';

  @IsOptional()
  @IsInt()
  @Min(1)
  @Max(30)
  expiresInDays?: number;

  @IsOptional()
  @Transform(({ value }: { value: unknown }) =>
    typeof value === 'string' ? value.trim().toLowerCase() : value,
  )
  @IsEmail()
  @MaxLength(254)
  targetEmail?: string;
}
