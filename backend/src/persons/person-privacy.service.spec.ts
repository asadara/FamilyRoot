import { privacyAccessForVisibility } from './person-privacy.service';

describe('person privacy visibility policy', () => {
  it('maps family data to full access', () => {
    expect(privacyAccessForVisibility('FAMILY')).toBe('FULL');
  });

  it('maps limited data to structure-only access', () => {
    expect(privacyAccessForVisibility('LIMITED')).toBe('STRUCTURE');
  });

  it('maps private data to the minimum graph identity', () => {
    expect(privacyAccessForVisibility('PRIVATE')).toBe('MINIMUM');
  });
});
