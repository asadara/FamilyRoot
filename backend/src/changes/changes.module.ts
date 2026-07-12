import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ChangeLogEntity } from './change-log.entity';
import { ChangesService } from './changes.service';
import { ChangesController } from './changes.controller';

@Module({
  imports: [TypeOrmModule.forFeature([ChangeLogEntity])],
  providers: [ChangesService],
  controllers: [ChangesController],
  exports: [TypeOrmModule, ChangesService],
})
export class ChangesModule {}
