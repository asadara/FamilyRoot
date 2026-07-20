import { ServiceUnavailableException } from '@nestjs/common';
import { ObjectStorage } from './object-storage';

export class DisabledObjectStorage implements ObjectStorage {
  private unavailable(): never {
    throw new ServiceUnavailableException(
      'Object storage is not configured in this environment',
    );
  }

  putObject(): Promise<void> {
    this.unavailable();
  }

  deleteObject(): Promise<void> {
    this.unavailable();
  }

  createSignedReadUrl(): Promise<string> {
    this.unavailable();
  }
}
