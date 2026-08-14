import { describe, expect, it } from 'vitest';
import { isSelectableSeat } from '@/types/trip';

describe('seat selection rules', () => {
  it('allows only AVAILABLE seats', () => {
    expect(isSelectableSeat({ seatNumber: 'A01', status: 'AVAILABLE' })).toBe(true);
    expect(isSelectableSeat({ seatNumber: 'A02', status: 'LOCKED' })).toBe(false);
    expect(isSelectableSeat({ seatNumber: 'A03', status: 'SOLD' })).toBe(false);
  });
});
