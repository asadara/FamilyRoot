import { mutationReceiptCopy } from './mutation-receipt.interceptor';

describe('mutationReceiptCopy', () => {
  it('creates safe success copy without family values', () => {
    expect(
      mutationReceiptCopy('POST', '/proposals/:proposalId/comments', 201),
    ).toEqual(
      expect.objectContaining({
        kind: 'SUCCESS',
        code: 'COMMENT_SAVED',
      }),
    );
  });

  it('classifies conflicts and ignores reads or notification mutations', () => {
    expect(
      mutationReceiptCopy('PATCH', '/persons/:personId/profile', 409),
    ).toEqual(
      expect.objectContaining({ kind: 'WARNING', code: 'ACTION_CONFLICT' }),
    );
    expect(mutationReceiptCopy('GET', '/persons', 200)).toBeNull();
    expect(
      mutationReceiptCopy('POST', '/notifications/read-all', 200),
    ).toBeNull();
  });
});
