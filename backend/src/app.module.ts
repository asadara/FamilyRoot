import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { UsersModule } from './users/users.module';
import { SpacesModule } from './spaces/spaces.module';
import { PersonsModule } from './persons/persons.module';
import { ClaimsModule } from './claims/claims.module';
import { ChangesModule } from './changes/changes.module';
import { ExportModule } from './export/export.module';
import { AuthModule } from './auth/auth.module';
import { ArchiveModule } from './archive/archive.module';
import { APP_FILTER, APP_GUARD, APP_INTERCEPTOR } from '@nestjs/core';
import { JwtAuthGuard } from './auth/jwt-auth.guard';
import { SpaceMemberGuard } from './common/space-member.guard';
import { CommonModule } from './common/common.module';
import { AppController } from './app.controller';
import { AppService } from './app.service';
import { ApiExceptionFilter } from './common/api-exception.filter';
import { RequestObservabilityInterceptor } from './common/request-observability.interceptor';

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true }),
    TypeOrmModule.forRoot({
      type: 'sqlite',
      database: process.env.DB_DATABASE ?? 'dev.sqlite',
      autoLoadEntities: true,
      synchronize: process.env.NODE_ENV !== 'production',
    }),
    UsersModule,
    SpacesModule,
    PersonsModule,
    ClaimsModule,
    ChangesModule,
    ExportModule,
    AuthModule,
    ArchiveModule,
    CommonModule,
  ],
  controllers: [AppController],
  providers: [
    AppService,
    { provide: APP_GUARD, useClass: JwtAuthGuard },
    { provide: APP_GUARD, useExisting: SpaceMemberGuard },
    { provide: APP_FILTER, useClass: ApiExceptionFilter },
    { provide: APP_INTERCEPTOR, useClass: RequestObservabilityInterceptor },
  ],
})
export class AppModule {}
