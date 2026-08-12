package com.schoolbus.transport.infrastructure.persistence.seat;

public class TripSeatStatusDataObject {

    private String seatNumber;
    private String status;

    public String getSeatNumber() {
        return seatNumber;
    }

    public void setSeatNumber(String seatNumber) {
        this.seatNumber = seatNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
