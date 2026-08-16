package com.schoolbus.transportquery.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TripReadDataObject {

    private Long id;
    private String tripNumber;
    private Long vehicleId;
    private Long routeId;
    private LocalDateTime departureTime;
    private LocalDateTime bookingDeadline;
    private BigDecimal price;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTripNumber() {
        return tripNumber;
    }

    public void setTripNumber(String tripNumber) {
        this.tripNumber = tripNumber;
    }

    public Long getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(Long vehicleId) {
        this.vehicleId = vehicleId;
    }

    public Long getRouteId() {
        return routeId;
    }

    public void setRouteId(Long routeId) {
        this.routeId = routeId;
    }

    public LocalDateTime getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(LocalDateTime departureTime) {
        this.departureTime = departureTime;
    }

    public LocalDateTime getBookingDeadline() {
        return bookingDeadline;
    }

    public void setBookingDeadline(LocalDateTime bookingDeadline) {
        this.bookingDeadline = bookingDeadline;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}
