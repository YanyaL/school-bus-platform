export interface BookableTrip {
  tripId: string;
  tripNumber: string;
  vehicleId: string;
  routeId: string;
  departureTime: string;
  bookingDeadline: string;
  price: number;
}

export type TripSeatStatus = 'AVAILABLE' | 'LOCKED' | 'SOLD';

export interface TripSeat {
  seatNumber: string;
  status: TripSeatStatus;
}

export interface TripSeatMap {
  tripId: string;
  bookingDeadline: string;
  seats: TripSeat[];
}

export function isSelectableSeat(seat: TripSeat): boolean {
  return seat.status === 'AVAILABLE';
}

export function seatStatusLabel(status: TripSeatStatus): string {
  switch (status) {
    case 'AVAILABLE':
      return '可选';
    case 'LOCKED':
      return '锁定';
    case 'SOLD':
      return '已售';
    default:
      return status;
  }
}
