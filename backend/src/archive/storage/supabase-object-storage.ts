import { InternalServerErrorException } from '@nestjs/common';
import { createClient, SupabaseClient } from '@supabase/supabase-js';
import { ObjectStorage, StoredObject } from './object-storage';

type StorageOnlyDatabase = {
  public: {
    Tables: Record<string, never>;
    Views: Record<string, never>;
    Functions: Record<string, never>;
    Enums: Record<string, never>;
    CompositeTypes: Record<string, never>;
  };
};

export class SupabaseObjectStorage implements ObjectStorage {
  private readonly client: SupabaseClient<StorageOnlyDatabase>;

  constructor(
    projectUrl: string,
    secretKey: string,
    private readonly bucket: string,
  ) {
    this.client = createClient<StorageOnlyDatabase>(projectUrl, secretKey, {
      auth: {
        autoRefreshToken: false,
        persistSession: false,
        detectSessionInUrl: false,
      },
    });
  }

  async putObject(object: StoredObject): Promise<void> {
    const { error } = await this.client.storage
      .from(this.bucket)
      .upload(object.path, object.body, {
        contentType: object.contentType,
        cacheControl: '3600',
        upsert: false,
      });

    if (error) {
      throw new InternalServerErrorException('Media storage upload failed');
    }
  }

  async deleteObject(path: string): Promise<void> {
    const { error } = await this.client.storage
      .from(this.bucket)
      .remove([path]);
    if (error) {
      throw new InternalServerErrorException('Media storage cleanup failed');
    }
  }

  async createSignedReadUrl(
    path: string,
    expiresInSeconds: number,
  ): Promise<string> {
    const { data, error } = await this.client.storage
      .from(this.bucket)
      .createSignedUrl(path, expiresInSeconds);

    if (error || !data?.signedUrl) {
      throw new InternalServerErrorException(
        'Media access URL creation failed',
      );
    }
    return data.signedUrl;
  }
}
