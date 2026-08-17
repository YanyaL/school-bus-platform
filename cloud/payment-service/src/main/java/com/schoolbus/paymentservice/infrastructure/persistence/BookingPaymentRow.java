package com.schoolbus.paymentservice.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class BookingPaymentRow {

    private long id;
    private String orderNo;
    private long tripId;
    private String seatNumber;
    private BigDecimal priceSnapshot;
    private String status;
    private LocalDateTime expiresAt;
    private long version;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public long getTripId() { return tripId; }
    public void setTripId(long tripId) { this.tripId = tripId; }
    public String getSeatNumber() { return seatNumber; }
    public void setSeatNumber(String seatNumber) { this.seatNumber = seatNumber; }
    public BigDecimal getPriceSnapshot() { return priceSnapshot; }
    public void setPriceSnapshot(BigDecimal priceSnapshot) { this.priceSnapshot = priceSnapshot; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}
