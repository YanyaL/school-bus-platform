package com.schoolbus.bookingservice.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.schoolbus.bookingservice.application.payment.PaymentSucceededBookingTransaction;
import com.schoolbus.bookingservice.application.payment.PaymentSucceededEnvelope;
import com.schoolbus.bookingservice.application.payment.PaymentSucceededMessage;
import com.schoolbus.bookingservice.application.payment.PaymentSucceededResult;
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
public class PaymentSucceededListener {

    private static final Logger log = LoggerFactory.getLogger(
            PaymentSucceededListener.class
    );

    private final PaymentSucceededBookingTransaction transaction;
    private final ObjectMapper objectMapper;
    private final PaymentSucceededRetryPublisher retryPublisher;
    private final PaymentSucceededRetryAttemptResolver attemptResolver;
    private final PaymentSucceededRetryProperties retryProperties;

    public PaymentSucceededListener(
            PaymentSucceededBookingTransaction transaction,
            ObjectMapper objectMapper,
            PaymentSucceededRetryPublisher retryPublisher,
            PaymentSucceededRetryAttemptResolver attemptResolver,
            PaymentSucceededRetryProperties retryProperties
    ) {
        this.transaction = Objects.requireNonNull(transaction);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.retryPublisher = Objects.requireNonNull(retryPublisher);
        this.attemptResolver = Objects.requireNonNull(attemptResolver);
        this.retryProperties = Objects.requireNonNull(retryProperties);
    }

    @RabbitListener(
            queues = "${school-bus.messaging.payment.succeeded-queue}"
    )
    public void consume(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            PaymentSucceededResult result = transaction.process(
                    toEnvelope(message)
            );
            channel.basicAck(deliveryTag, false);
            log.info(
                    "PaymentSucceeded consumed: bookingNumber={}, outcome={}",
                    result.bookingNumber(),
                    result.outcome()
            );
        } catch (MalformedPaymentSucceededMessageException exception) {
            log.error(
                    "Malformed PaymentSucceeded rejected to DLQ",
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
                    "PaymentSucceeded exhausted {} retries and will be dead-lettered",
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
                    "PaymentSucceeded scheduled for retry {}/{} after {}",
                    completedRetries + 1,
                    retryProperties.maximumRetries(),
                    retryProperties.delay(),
                    processingFailure
            );
        } catch (RuntimeException retryPublishFailure) {
            processingFailure.addSuppressed(retryPublishFailure);
            log.error(
                    "Failed to publish PaymentSucceeded retry; original will be requeued",
                    processingFailure
            );
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private PaymentSucceededEnvelope toEnvelope(Message message) {
        String eventId = message.getMessageProperties().getMessageId();
        try {
            return new PaymentSucceededEnvelope(
                    eventId,
                    objectMapper.readValue(
                            message.getBody(),
                            PaymentSucceededMessage.class
                    )
            );
        } catch (IOException | IllegalArgumentException exception) {
            throw new MalformedPaymentSucceededMessageException(
                    "invalid PaymentSucceeded message",
                    exception
            );
        }
    }
}
