import type { AxiosInstance, AxiosResponse } from 'axios';
import type { ApiResponse, PageResponse } from '@/types/api';
import type {
  BookingDetail,
  BookingStatus,
  BookingSummary,
  CancelBookingResponse,
  CreateBookingRequest,
  CreateBookingResponse,
} from '@/types/booking';
import { unwrapApiResponse } from './http';

export const IDEMPOTENCY_KEY_HEADER = 'Idempotency-Key';
export const IDEMPOTENCY_REPLAYED_HEADER = 'Idempotency-Replayed';

export interface CreateBookingResult {
  data: CreateBookingResponse;
  idempotencyReplayed: boolean;
}

export interface ListBookingsParams {
  status?: BookingStatus;
  page?: number;
  size?: number;
  sort?: string;
}

export function createBookingsApi(http: AxiosInstance) {
  return {
    async createBooking(
      payload: CreateBookingRequest,
      idempotencyKey: string,
    ): Promise<CreateBookingResult> {
      const response: AxiosResponse<ApiResponse<CreateBookingResponse>> =
        await http.post('/bookings', payload, {
          headers: {
            [IDEMPOTENCY_KEY_HEADER]: idempotencyKey,
          },
        });
      const replayedHeader = response.headers[IDEMPOTENCY_REPLAYED_HEADER];
      return {
        data: unwrapApiResponse(response),
        idempotencyReplayed: String(replayedHeader).toLowerCase() === 'true',
      };
    },

    async listMyBookings(
      params: ListBookingsParams = {},
    ): Promise<PageResponse<BookingSummary>> {
      const response = await http.get('/bookings', { params });
      return unwrapApiResponse(response);
    },

    async getBookingDetail(bookingNumber: string): Promise<BookingDetail> {
      const response = await http.get(`/bookings/${bookingNumber}`);
      return unwrapApiResponse(response);
    },

    async cancelBooking(bookingNumber: string): Promise<CancelBookingResponse> {
      const response = await http.post(`/bookings/${bookingNumber}/cancellation`);
      return unwrapApiResponse(response);
    },
  };
}

export type BookingsApi = ReturnType<typeof createBookingsApi>;
