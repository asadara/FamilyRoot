import {
  collectiveClaimStatus,
  REQUIRED_CLAIM_CONFIRMATIONS,
} from './claims.service';

describe('collective claim confirmation policy', () => {
  it('requires two distinct confirmations', () => {
    expect(REQUIRED_CLAIM_CONFIRMATIONS).toBe(2);
    expect(collectiveClaimStatus(0)).toBe('PENDING');
    expect(collectiveClaimStatus(1)).toBe('PENDING');
    expect(collectiveClaimStatus(2)).toBe('VERIFIED');
  });

  it('keeps the claim verified after the threshold', () => {
    expect(collectiveClaimStatus(3)).toBe('VERIFIED');
  });
});
