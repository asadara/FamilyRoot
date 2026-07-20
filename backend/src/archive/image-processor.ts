import { BadRequestException } from '@nestjs/common';
import sharp from 'sharp';

export const MAX_IMAGE_UPLOAD_BYTES = 2 * 1024 * 1024;
const MAX_IMAGE_PIXELS = 25_000_000;

type SafeImageFormat = 'jpeg' | 'png' | 'webp';

export type ProcessedImage = {
  body: Buffer;
  contentType: 'image/jpeg' | 'image/png' | 'image/webp';
  extension: 'jpg' | 'png' | 'webp';
};

export function detectImageFormat(buffer: Buffer): SafeImageFormat | null {
  if (
    buffer.length >= 3 &&
    buffer[0] === 0xff &&
    buffer[1] === 0xd8 &&
    buffer[2] === 0xff
  ) {
    return 'jpeg';
  }

  if (
    buffer.length >= 8 &&
    buffer
      .subarray(0, 8)
      .equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))
  ) {
    return 'png';
  }

  if (
    buffer.length >= 12 &&
    buffer.subarray(0, 4).toString('ascii') === 'RIFF' &&
    buffer.subarray(8, 12).toString('ascii') === 'WEBP'
  ) {
    return 'webp';
  }

  return null;
}

export async function processUploadedImage(
  input: Buffer,
): Promise<ProcessedImage> {
  if (input.length === 0 || input.length > MAX_IMAGE_UPLOAD_BYTES) {
    throw new BadRequestException('Image must be between 1 byte and 2 MB');
  }

  const format = detectImageFormat(input);
  if (!format) {
    throw new BadRequestException(
      'Only valid JPEG, PNG, or WebP images are allowed',
    );
  }

  try {
    let pipeline = sharp(input, {
      failOn: 'warning',
      limitInputPixels: MAX_IMAGE_PIXELS,
    })
      .autoOrient()
      .resize({
        width: 2048,
        height: 2048,
        fit: 'inside',
        withoutEnlargement: true,
      });

    if (format === 'jpeg') pipeline = pipeline.jpeg({ quality: 82 });
    if (format === 'png') pipeline = pipeline.png({ compressionLevel: 9 });
    if (format === 'webp') pipeline = pipeline.webp({ quality: 82 });

    const body = await pipeline.toBuffer();
    if (body.length > MAX_IMAGE_UPLOAD_BYTES) {
      throw new BadRequestException('Processed image exceeds the 2 MB limit');
    }

    if (format === 'jpeg') {
      return { body, contentType: 'image/jpeg', extension: 'jpg' };
    }
    if (format === 'png') {
      return { body, contentType: 'image/png', extension: 'png' };
    }
    return { body, contentType: 'image/webp', extension: 'webp' };
  } catch (error) {
    if (error instanceof BadRequestException) throw error;
    throw new BadRequestException('Image content is malformed or unsafe');
  }
}
