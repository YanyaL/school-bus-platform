import type { AxiosInstance } from 'axios';
import type { BookableTrip, TripSeatMap } from '@/types/trip';
import { unwrapApiResponse } from './http';

export function createTripsApi(http: AxiosInstance) {
  return {
    async listBookableTrips(limit = 20): Promise<BookableTrip[]> {
      const response = await http.get('/trips', { params: { limit } });
      return unwrapApiResponse(response);
    },

    async getTripSeats(tripId: number): Promise<TripSeatMap> {
      const response = await http.get(`/trips/${tripId}/seats`);
      return unwrapApiResponse(response);
    },
  };
}

export type TripsApi = ReturnType<typeof createTripsApi>;
