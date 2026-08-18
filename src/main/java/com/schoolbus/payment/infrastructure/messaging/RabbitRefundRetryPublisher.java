package com.schoolbus.payment.infrastructure.messaging;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import com.schoolbus.payment.config.ConditionalOnEmbeddedRefundMessaging;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnEmbeddedRefundMessaging
public class RabbitRefundRetryPublisher
        implements RefundRetryPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final RefundRetryProperties properties;

    public RabbitRefundRetryPublisher(
            RabbitTemplate rabbitTemplate,
            RefundRetryProperties properties
    ) {
        this.rabbitTemplate = Objects.requireNonNull(
                rabbitTemplate,
                "rabbitTemplate must not be null"
        );
        this.properties = Objects.requireNonNull(
                properties,
                "properties must not be null"
        );
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
            throw new OutboxPublishException(
                    "refund retry message was returned: "
                            + returned.getReplyText()
            );
        }
        if (!confirm.isAck()) {
            throw new OutboxPublishException(
                    "RabbitMQ rejected refund retry message: "
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
            throw new OutboxPublishException(
                    "interrupted while confirming refund retry message",
                    exception
            );
        } catch (ExecutionException | TimeoutException exception) {
            throw new OutboxPublishException(
                    "failed to confirm refund retry message",
                    exception
            );
        }
    }

    private String correlationId(Message message) {
        String messageId = message.getMessageProperties().getMessageId();
        String prefix = messageId == null ? "refund" : messageId;
        return prefix + ":retry:" + UUID.randomUUID();
    }
}
