export type VehicleStatus = 'ENABLED' | 'DISABLED';
export type RouteStatus = 'ENABLED' | 'DISABLED';
export type Campus = 'MAIN' | 'EAST' | 'WEST' | 'NORTH';
export type TripStatus =
  | 'DRAFT'
  | 'OPEN_FOR_BOOKING'
  | 'CLOSED'
  | 'CANCELLATION_PENDING'
  | 'DEPARTED'
  | 'COMPLETED'
  | 'CANCELLED';

export interface Vehicle {
  vehicleId: string;
  vehicleNumber: string;
  licensePlate: string;
  seatCount: number;
  status: VehicleStatus;
  version: number;
}

export interface Route {
  routeId: string;
  routeNumber: string;
  routeCode: string;
  departureCampus: Campus;
  arrivalCampus: Campus;
  estimatedDurationMinutes: number;
  status: RouteStatus;
  version: number;
}

export interface Trip {
  tripId: string;
  tripNumber: string;
  vehicleId: string;
  routeId: string;
  departureTime: string;
  bookingDeadline: string;
  price: number;
  status: TripStatus;
  version: number;
}

export interface ApiEnvelope<T> {
  code: string;
  message: string;
  data: T;
  traceId: string;
  timestamp: string;
}
