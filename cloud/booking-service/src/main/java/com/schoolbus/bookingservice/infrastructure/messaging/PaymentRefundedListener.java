package com.schoolbus.bookingservice.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.schoolbus.bookingservice.application.payment.PaymentRefundedBookingTransaction;
import com.schoolbus.bookingservice.application.payment.PaymentRefundedEnvelope;
import com.schoolbus.bookingservice.application.payment.PaymentRefundedMessage;
import com.schoolbus.bookingservice.application.payment.PaymentRefundedMessageConflictException;
import com.schoolbus.bookingservice.application.payment.PaymentRefundedResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Objects;

@Component
@Profile("!test")
public class PaymentRefundedListener {

    private static final Logger log = LoggerFactory.getLogger(
            PaymentRefundedListener.class
    );

    private final PaymentRefundedBookingTransaction transaction;
    private final ObjectMapper objectMapper;
    private final PaymentRefundedRetryPublisher retryPublisher;
    private final PaymentSucceededRetryAttemptResolver attemptResolver;
    private final PaymentRefundedRetryProperties retryProperties;

    public PaymentRefundedListener(
            PaymentRefundedBookingTransaction transaction,
            ObjectMapper objectMapper,
            PaymentRefundedRetryPublisher retryPublisher,
            PaymentSucceededRetryAttemptResolver attemptResolver,
            PaymentRefundedRetryProperties retryProperties
    ) {
        this.transaction = Objects.requireNonNull(transaction);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.retryPublisher = Objects.requireNonNull(retryPublisher);
        this.attemptResolver = Objects.requireNonNull(attemptResolver);
        this.retryProperties = Objects.requireNonNull(retryProperties);
    }

    @RabbitListener(
            queues = "${school-bus.messaging.payment.refunded-queue}"
    )
    public void consume(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            PaymentRefundedResult result = transaction.process(
                    toEnvelope(message)
            );
            channel.basicAck(deliveryTag, false);
            log.info(
                    "PaymentRefunded consumed: bookingNumber={}, outcome={}",
                    result.bookingNumber(),
                    result.outcome()
            );
        } catch (MalformedPaymentRefundedMessageException
                 | PaymentRefundedMessageConflictException exception) {
            log.error(
                    "Invalid PaymentRefunded rejected to DLQ",
                    exception
            );
            channel.basicReject(deliveryTag, false);
        } catch (RuntimeException exception) {
            scheduleRetryOrReject(
                    message,
                    channel,
                    deliveryTag,
                    exception
            );
        }
    }

    private void scheduleRetryOrReject(
            Message message,
            Channel channel,
            long deliveryTag,
            RuntimeException processingFailure
    ) throws IOException {
        int completedRetries = attemptResolver.completedRetries(
                message,
                retryProperties.queue()
        );
        if (completedRetries >= retryProperties.maximumRetries()) {
            log.error(
                    "PaymentRefunded exhausted {} retries and will be dead-lettered",
                    retryProperties.maximumRetries(),
                    processingFailure
            );
            channel.basicReject(deliveryTag, false);
            return;
        }
        try {
            retryPublisher.scheduleRetry(message);
            channel.basicAck(deliveryTag, false);
            log.warn(
                    "PaymentRefunded scheduled for retry {}/{} after {}",
                    completedRetries + 1,
                    retryProperties.maximumRetries(),
                    retryProperties.delay(),
                    processingFailure
            );
        } catch (RuntimeException retryPublishFailure) {
            processingFailure.addSuppressed(retryPublishFailure);
            log.error(
                    "Failed to publish PaymentRefunded retry; original will be requeued",
                    processingFailure
            );
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private PaymentRefundedEnvelope toEnvelope(Message message) {
        String eventId = message.getMessageProperties().getMessageId();
        try {
            return new PaymentRefundedEnvelope(
                    eventId,
                    objectMapper.readValue(
                            message.getBody(),
                            PaymentRefundedMessage.class
                    )
            );
        } catch (IOException | IllegalArgumentException exception) {
            throw new MalformedPaymentRefundedMessageException(
                    "invalid PaymentRefunded message",
                    exception
            );
        }
    }
}
