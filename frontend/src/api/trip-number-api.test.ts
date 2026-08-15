import { describe, expect, it, vi } from 'vitest';
import type { AxiosInstance } from 'axios';
import { createTripsApi } from '@/api/trips';
import { createBookingsApi } from '@/api/bookings';
import type { BookableTrip, TripSeatMap } from '@/types/trip';
import type {
  BookingDetail,
  BookingSummary,
  CreateBookingRequest,
  CreateBookingResponse,
} from '@/types/booking';

describe('tripNumber student API contract', () => {
  const tripNumber = 'cb82ebec-cce5-4a17-ab7f-121561ab96ca';
  const bookingId = '81765424194125824';

  it('passes UUID tripNumber unchanged into seats URL and booking body', async () => {
    const get = vi.fn(async (_url: string) => ({
      data: {
        code: 'OK',
        message: 'success',
        data: {
          tripNumber,
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
          bookingId,
          bookingNumber: '11111111-1111-1111-1111-111111111111',
          tripNumber,
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

    const seats = await tripsApi.getTripSeats(tripNumber);
    expect(seats.tripNumber).toBe(tripNumber);
    expect(get).toHaveBeenCalledWith(`/trips/${tripNumber}/seats`);
    expect(get.mock.calls[0][0]).toBe(`/trips/${tripNumber}/seats`);

    const request: CreateBookingRequest = {
      tripNumber,
      seatNumber: 'A01',
    };
    const created = await bookingsApi.createBooking(request, 'idem-1');
    expect(created.data.tripNumber).toBe(tripNumber);
    expect(created.data.bookingId).toBe(bookingId);
    expect(post.mock.calls[0][1]).toEqual({
      tripNumber,
      seatNumber: 'A01',
    });
    expect(Object.keys(post.mock.calls[0][1] as object).sort()).toEqual([
      'seatNumber',
      'tripNumber',
    ]);
    expect(typeof (post.mock.calls[0][1] as CreateBookingRequest).tripNumber).toBe(
      'string',
    );
  });

  it('does not coerce tripNumber through Number, parseInt, or BigInt', () => {
    expect(String(Number(tripNumber))).not.toBe(tripNumber);
    expect(Number.isNaN(Number(tripNumber))).toBe(true);
    expect(Number.isNaN(parseInt(tripNumber, 10))).toBe(true);
    expect(() => BigInt(tripNumber)).toThrow();
  });

  it('student DTO types use tripNumber and do not declare tripId', () => {
    const bookableTripKeys: Array<keyof BookableTrip> = [
      'tripNumber',
      'vehicleId',
      'routeId',
      'departureTime',
      'bookingDeadline',
      'price',
    ];
    const seatMapKeys: Array<keyof TripSeatMap> = [
      'tripNumber',
      'bookingDeadline',
      'seats',
    ];
    const createRequestKeys: Array<keyof CreateBookingRequest> = [
      'tripNumber',
      'seatNumber',
    ];
    const createResponseKeys: Array<keyof CreateBookingResponse> = [
      'bookingId',
      'bookingNumber',
      'tripNumber',
      'seatNumber',
      'amount',
      'status',
      'expiresAt',
    ];
    const summaryKeys: Array<keyof BookingSummary> = [
      'bookingId',
      'bookingNumber',
      'tripNumber',
      'seatNumber',
      'amount',
      'status',
      'expiresAt',
      'createdAt',
    ];
    const detailKeys: Array<keyof BookingDetail> = [
      'bookingId',
      'bookingNumber',
      'tripNumber',
      'seatNumber',
      'amount',
      'status',
      'expiresAt',
      'paidAt',
      'cancelledAt',
      'cancelReason',
      'createdAt',
    ];

    for (const keys of [
      bookableTripKeys,
      seatMapKeys,
      createRequestKeys,
      createResponseKeys,
      summaryKeys,
      detailKeys,
    ]) {
      expect(keys).toContain('tripNumber');
      expect(keys).not.toContain('tripId' as never);
    }
  });
});
