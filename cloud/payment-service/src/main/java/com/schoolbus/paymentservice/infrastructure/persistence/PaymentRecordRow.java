package com.schoolbus.paymentservice.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentRecordRow {

    private long id;
    private String paymentNo;
    private String requestNo;
    private String orderNo;
    private BigDecimal amount;
    private String status;
    private String failureReason;
    private LocalDateTime completedAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getPaymentNo() { return paymentNo; }
    public void setPaymentNo(String paymentNo) { this.paymentNo = paymentNo; }
    public String getRequestNo() { return requestNo; }
    public void setRequestNo(String requestNo) { this.requestNo = requestNo; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
