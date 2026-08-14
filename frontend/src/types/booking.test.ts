import { describe, expect, it } from 'vitest';
import { bookingStatusMeta } from '@/types/booking';

describe('booking status labels', () => {
  it('maps pending payment to Chinese label', () => {
    expect(bookingStatusMeta('PENDING_PAYMENT')).toEqual({
      label: '待支付',
      type: 'warning',
    });
  });

  it('maps paid to Chinese label', () => {
    expect(bookingStatusMeta('PAID')).toEqual({
      label: '已支付',
      type: 'success',
    });
  });
});
