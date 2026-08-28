import axios, { type AxiosInstance } from 'axios';
import type {
  ApiEnvelope,
  Campus,
  Route,
  RouteStatus,
  Trip,
  Vehicle,
  VehicleStatus,
} from '@/types/admin';

export function adminApiErrorMessage(error: unknown, fallback: string): string {
  if (axios.isAxiosError(error)) {
    const response = error.response?.data as Partial<ApiEnvelope<unknown>> | undefined;
    if (typeof response?.message === 'string' && response.message.trim()) {
      return response.message;
    }
  }
  return error instanceof Error && error.message ? error.message : fallback;
}

export function createAdminApi(getAccessToken: () => string | null) {
  const http: AxiosInstance = axios.create({ baseURL: '/api/v1/admin', timeout: 8000 });
  http.interceptors.request.use((config) => {
    const token = getAccessToken();
    if (token) config.headers.Authorization = `Bearer ${token}`;
    return config;
  });

  const data = async <T>(request: Promise<{ data: ApiEnvelope<T> }>): Promise<T> =>
    (await request).data.data;

  return {
    listVehicles: () => data<Vehicle[]>(http.get('/vehicles')),
    createVehicle: (payload: { licensePlate: string; seatCount: number }) =>
      data<Vehicle>(http.post('/vehicles', payload)),
    updateVehicleStatus: (vehicle: Vehicle, status: VehicleStatus) =>
      data<Vehicle>(
        http.patch(`/vehicles/${vehicle.vehicleId}/status`, {
          status,
          version: vehicle.version,
        }),
      ),
    listRoutes: () => data<Route[]>(http.get('/routes')),
    createRoute: (payload: {
      routeCode: string;
      departureCampus: Campus;
      arrivalCampus: Campus;
      estimatedDurationMinutes: number;
    }) => data<Route>(http.post('/routes', payload)),
    updateRouteStatus: (route: Route, status: RouteStatus) =>
      data<Route>(
        http.patch(`/routes/${route.routeId}/status`, {
          status,
          version: route.version,
        }),
      ),
    listTrips: () => data<Trip[]>(http.get('/trips')),
    createTrip: (payload: {
      vehicleId: string;
      routeId: string;
      departureTime: string;
      bookingDeadline: string;
      price: number;
    }) => data<Trip>(http.post('/trips', payload)),
    publishTrip: (trip: Trip) =>
      data<Trip>(
        http.post(`/trips/${trip.tripId}/publication`, { version: trip.version }),
      ),
    cancelTrip: (trip: Trip) =>
      data<Trip>(
        http.post(`/trips/${trip.tripId}/cancellation`, { version: trip.version }),
      ),
  };
}
