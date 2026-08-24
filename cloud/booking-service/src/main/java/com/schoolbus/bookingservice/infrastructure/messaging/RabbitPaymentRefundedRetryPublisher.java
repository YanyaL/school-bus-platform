package com.schoolbus.bookingservice.infrastructure.messaging;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@Profile("!test")
public class RabbitPaymentRefundedRetryPublisher
        implements PaymentRefundedRetryPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final PaymentRefundedRetryProperties properties;

    public RabbitPaymentRefundedRetryPublisher(
            RabbitTemplate rabbitTemplate,
            PaymentRefundedRetryProperties properties
    ) {
        this.rabbitTemplate = Objects.requireNonNull(rabbitTemplate);
        this.properties = Objects.requireNonNull(properties);
    }

    @Override
    public void scheduleRetry(Message message) {
        Message checked = Objects.requireNonNull(
                message,
                "message must not be null"
        );
        Message retryMessage = MessageBuilder
                .fromClonedMessage(checked)
                .build();
        CorrelationData correlation = new CorrelationData(
                correlationId(checked)
        );
        rabbitTemplate.send(
                properties.exchange(),
                properties.routingKey(),
                retryMessage,
                correlation
        );
        CorrelationData.Confirm confirm = waitForConfirm(correlation);
        ReturnedMessage returned = correlation.getReturned();
        if (returned != null) {
            throw new PaymentRefundedRetryPublishException(
                    "payment refunded retry was returned: "
                            + returned.getReplyText()
            );
        }
        if (!confirm.isAck()) {
            throw new PaymentRefundedRetryPublishException(
                    "RabbitMQ rejected payment refunded retry: "
                            + confirm.getReason()
            );
        }
    }

    private CorrelationData.Confirm waitForConfirm(
            CorrelationData correlation
    ) {
        try {
            return correlation.getFuture().get(
                    properties.confirmTimeout().toMillis(),
                    TimeUnit.MILLISECONDS
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PaymentRefundedRetryPublishException(
                    "interrupted while confirming payment refunded retry",
                    exception
            );
        } catch (ExecutionException | TimeoutException exception) {
            throw new PaymentRefundedRetryPublishException(
                    "failed to confirm payment refunded retry",
                    exception
            );
        }
    }

    private String correlationId(Message message) {
        String messageId = message.getMessageProperties().getMessageId();
        String prefix = messageId == null
                ? "payment-refunded"
                : messageId;
        return prefix + ":retry:" + UUID.randomUUID();
    }
}
