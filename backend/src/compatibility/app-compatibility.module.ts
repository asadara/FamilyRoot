import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AppCompatibilityController } from './app-compatibility.controller';
import { AppCompatibilityService } from './app-compatibility.service';
import { AppReleasePolicyAuditEntity } from './app-release-policy-audit.entity';
import { AppReleasePolicyEntity } from './app-release-policy.entity';
import { SystemAdminGuard } from './system-admin.guard';
import { AppCompatibilityGuard } from './app-compatibility.guard';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      AppReleasePolicyEntity,
      AppReleasePolicyAuditEntity,
    ]),
  ],
  controllers: [AppCompatibilityController],
  providers: [AppCompatibilityService, AppCompatibilityGuard, SystemAdminGuard],
  exports: [AppCompatibilityService, AppCompatibilityGuard],
})
export class AppCompatibilityModule {}
