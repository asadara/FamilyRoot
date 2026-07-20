export const OBJECT_STORAGE = Symbol('OBJECT_STORAGE');

export type StoredObject = {
  path: string;
  contentType: string;
  body: Buffer;
};

export interface ObjectStorage {
  putObject(object: StoredObject): Promise<void>;
  deleteObject(path: string): Promise<void>;
  createSignedReadUrl(path: string, expiresInSeconds: number): Promise<string>;
}
