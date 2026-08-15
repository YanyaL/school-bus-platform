import { describe, expect, it, vi } from 'vitest';
import type { AxiosInstance } from 'axios';
import { createTripsApi } from '@/api/trips';
import { createBookingsApi } from '@/api/bookings';

describe('HTTP resource ID precision protection', () => {
  const snowflakeId = '81765424194125824';

  it('keeps snowflake tripId intact through seats URL and booking body', async () => {
    const get = vi.fn(async (_url: string) => ({
      data: {
        code: 'OK',
        message: 'success',
        data: {
          tripId: snowflakeId,
          bookingDeadline: '2026-08-15T01:00:00.000Z',
          seats: [],
        },
        traceId: 't1',
        timestamp: '2026-08-15T00:00:00.000Z',
      },
    }));
    const post = vi.fn(async (_url: string, _body: unknown) => ({
      data: {
        code: 'OK',
        message: 'success',
        data: {
          bookingId: snowflakeId,
          bookingNumber: '11111111-1111-1111-1111-111111111111',
          tripId: snowflakeId,
          seatNumber: 'A01',
          amount: 5.5,
          status: 'PENDING_PAYMENT',
          expiresAt: '2026-08-15T01:00:00.000Z',
        },
        traceId: 't2',
        timestamp: '2026-08-15T00:00:00.000Z',
      },
      headers: {},
    }));

    const http = { get, post } as unknown as AxiosInstance;
    const tripsApi = createTripsApi(http);
    const bookingsApi = createBookingsApi(http);

    const seats = await tripsApi.getTripSeats(snowflakeId);
    expect(seats.tripId).toBe(snowflakeId);
    expect(get).toHaveBeenCalledWith(`/trips/${snowflakeId}/seats`);
    expect(get.mock.calls[0][0]).toContain(snowflakeId);
    expect(get.mock.calls[0][0]).not.toContain('81765424194125820');

    const created = await bookingsApi.createBooking(
      { tripId: snowflakeId, seatNumber: 'A01' },
      'idem-1',
    );
    expect(created.data.tripId).toBe(snowflakeId);
    expect(created.data.bookingId).toBe(snowflakeId);
    expect(post.mock.calls[0][1]).toEqual({
      tripId: snowflakeId,
      seatNumber: 'A01',
    });
    expect(typeof (post.mock.calls[0][1] as { tripId: string }).tripId).toBe('string');
  });

  it('does not coerce snowflake id through Number', () => {
    // JS Number cannot represent this Snowflake value exactly.
    expect(String(Number(snowflakeId))).not.toBe(snowflakeId);
    expect(snowflakeId).toBe('81765424194125824');
  });
});
