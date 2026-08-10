package com.schoolbus.payment.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.schoolbus.payment.application.refund.PaymentRefundApplicationService;
import com.schoolbus.payment.application.refund.PaymentRefundRequiredMessage;
import com.schoolbus.payment.application.refund.RefundMessageConflictException;
import com.schoolbus.payment.application.refund.RefundMessageEnvelope;
import com.schoolbus.payment.application.refund.RefundPaymentNotFoundException;
import com.schoolbus.payment.application.refund.RefundProcessingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Objects;

@Component
@Profile("local")
public class PaymentRefundListener {

    private static final Logger log = LoggerFactory.getLogger(
            PaymentRefundListener.class
    );

    private final PaymentRefundApplicationService applicationService;
    private final ObjectMapper objectMapper;

    public PaymentRefundListener(
            PaymentRefundApplicationService applicationService,
            ObjectMapper objectMapper
    ) {
        this.applicationService = Objects.requireNonNull(
                applicationService,
                "applicationService must not be null"
        );
        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "objectMapper must not be null"
        );
    }

    @RabbitListener(
            queues = "${school-bus.messaging.payment.refund-queue}"
    )
    public void consume(Message message, Channel channel)
            throws IOException {
        long deliveryTag = message.getMessageProperties()
                .getDeliveryTag();
        try {
            RefundProcessingResult result = applicationService.process(
                    toEnvelope(message)
            );
            channel.basicAck(deliveryTag, false);
            log.info(
                    "Refund event consumed: paymentNumber={}, outcome={}",
                    result.paymentNumber(),
                    result.outcome()
            );
        } catch (MalformedRefundMessageException
                 | RefundPaymentNotFoundException
                 | RefundMessageConflictException exception) {
            log.error("Refund event rejected as non-retryable", exception);
            channel.basicReject(deliveryTag, false);
        } catch (RuntimeException exception) {
            log.error("Refund event processing failed", exception);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private RefundMessageEnvelope toEnvelope(Message message) {
        String eventId = message.getMessageProperties().getMessageId();
        try {
            PaymentRefundRequiredMessage payload = objectMapper.readValue(
                    message.getBody(),
                    PaymentRefundRequiredMessage.class
            );
            return new RefundMessageEnvelope(eventId, payload);
        } catch (IOException | IllegalArgumentException exception) {
            throw new MalformedRefundMessageException(
                    "invalid payment refund message",
                    exception
            );
        }
    }
}
