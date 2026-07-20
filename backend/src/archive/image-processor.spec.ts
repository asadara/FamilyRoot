import { BadRequestException } from '@nestjs/common';
import { detectImageFormat, processUploadedImage } from './image-processor';

describe('image upload security', () => {
  it('detects supported formats from magic bytes rather than a filename', () => {
    expect(detectImageFormat(Buffer.from([0xff, 0xd8, 0xff, 0x00]))).toBe(
      'jpeg',
    );
    expect(
      detectImageFormat(
        Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
      ),
    ).toBe('png');
    expect(detectImageFormat(Buffer.from('not-an-image'))).toBeNull();
  });

  it('rejects content whose bytes are not a supported image', async () => {
    await expect(
      processUploadedImage(Buffer.from('fake jpeg')),
    ).rejects.toBeInstanceOf(BadRequestException);
  });

  it('decodes and re-encodes a valid image', async () => {
    const onePixelPng = Buffer.from(
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
      'base64',
    );

    const result = await processUploadedImage(onePixelPng);

    expect(result.contentType).toBe('image/png');
    expect(detectImageFormat(result.body)).toBe('png');
  });
});
