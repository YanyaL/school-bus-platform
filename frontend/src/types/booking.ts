export type BookingStatus =
  'PENDING_PAYMENT' | 'PAID' | 'REFUND_PENDING' | 'CANCELLED' | 'REFUNDED';

export type CancellationReason =
  'USER_CANCELLED' | 'PAYMENT_TIMEOUT' | 'TRIP_CANCELLED';

export interface CreateBookingRequest {
  tripNumber: string;
  seatNumber: string;
}

export interface CreateBookingResponse {
  bookingId: string;
  bookingNumber: string;
  tripNumber: string;
  seatNumber: string;
  amount: number;
  status: BookingStatus;
  expiresAt: string;
}

export interface BookingSummary {
  bookingId: string;
  bookingNumber: string;
  tripNumber: string;
  seatNumber: string;
  amount: number;
  status: BookingStatus;
  expiresAt: string;
  createdAt: string;
}

export interface BookingDetail {
  bookingId: string;
  bookingNumber: string;
  tripNumber: string;
  seatNumber: string;
  amount: number;
  status: BookingStatus;
  expiresAt: string;
  paidAt: string | null;
  cancelledAt: string | null;
  cancelReason: CancellationReason | null;
  createdAt: string;
}

export interface CancelBookingResponse {
  bookingNumber: string;
  status: BookingStatus;
  cancelReason: CancellationReason | null;
  cancelledAt: string | null;
}

export interface BookingStatusMeta {
  label: string;
  type: 'success' | 'warning' | 'info' | 'danger';
}

export function bookingStatusMeta(status: BookingStatus): BookingStatusMeta {
  switch (status) {
    case 'PENDING_PAYMENT':
      return { label: '待支付', type: 'warning' };
    case 'PAID':
      return { label: '已支付', type: 'success' };
    case 'REFUND_PENDING':
      return { label: '退款中', type: 'info' };
    case 'CANCELLED':
      return { label: '已取消', type: 'info' };
    case 'REFUNDED':
      return { label: '已退款', type: 'info' };
    default:
      return { label: status, type: 'info' };
  }
}

export function cancellationReasonLabel(reason: CancellationReason | null): string {
  if (!reason) {
    return '-';
  }
  switch (reason) {
    case 'USER_CANCELLED':
      return '用户取消';
    case 'PAYMENT_TIMEOUT':
      return '支付超时';
    case 'TRIP_CANCELLED':
      return '班次取消';
    default:
      return reason;
  }
}
