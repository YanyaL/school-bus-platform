import { describe, expect, it } from 'vitest';
import { IdempotencySession } from '@/utils/idempotency';

describe('idempotency session', () => {
  it('keeps the same key during one submit flow', () => {
    const session = new IdempotencySession();
    const first = session.begin();
    const second = session.begin();

    expect(first).toBe(second);
  });

  it('generates a new key after reset', () => {
    const session = new IdempotencySession();
    const first = session.begin();
    session.reset();
    const second = session.begin();

    expect(second).not.toBe(first);
  });
});
