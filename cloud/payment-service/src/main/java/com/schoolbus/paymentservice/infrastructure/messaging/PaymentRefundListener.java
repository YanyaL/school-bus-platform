package com.schoolbus.paymentservice.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.schoolbus.paymentservice.application.refund.PaymentRefundApplicationService;
import com.schoolbus.paymentservice.application.refund.PaymentRefundRequiredMessage;
import com.schoolbus.paymentservice.application.refund.RefundMessageConflictException;
import com.schoolbus.paymentservice.application.refund.RefundMessageEnvelope;
import com.schoolbus.paymentservice.application.refund.RefundPaymentNotFoundException;
import com.schoolbus.paymentservice.application.refund.RefundProcessingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Objects;

@Component
public class PaymentRefundListener {

    private static final Logger log = LoggerFactory.getLogger(
            PaymentRefundListener.class
    );

    private final PaymentRefundApplicationService applicationService;
    private final ObjectMapper objectMapper;
    private final RefundRetryPublisher retryPublisher;
    private final RefundRetryAttemptResolver attemptResolver;
    private final RefundRetryProperties retryProperties;

    public PaymentRefundListener(
            PaymentRefundApplicationService applicationService,
            ObjectMapper objectMapper,
            RefundRetryPublisher retryPublisher,
            RefundRetryAttemptResolver attemptResolver,
            RefundRetryProperties retryProperties
    ) {
        this.applicationService = Objects.requireNonNull(
                applicationService,
                "applicationService must not be null"
        );
        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "objectMapper must not be null"
        );
        this.retryPublisher = Objects.requireNonNull(
                retryPublisher,
                "retryPublisher must not be null"
        );
        this.attemptResolver = Objects.requireNonNull(
                attemptResolver,
                "attemptResolver must not be null"
        );
        this.retryProperties = Objects.requireNonNull(
                retryProperties,
                "retryProperties must not be null"
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
                    "Refund event exhausted {} retries and will be dead-lettered",
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
                    "Refund event scheduled for retry {}/{} after {}",
                    completedRetries + 1,
                    retryProperties.maximumRetries(),
                    retryProperties.delay(),
                    processingFailure
            );
        } catch (RuntimeException retryPublishFailure) {
            processingFailure.addSuppressed(retryPublishFailure);
            log.error(
                    "Failed to publish refund retry; original message will be requeued",
                    processingFailure
            );
            channel.basicNack(deliveryTag, false, true);
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
