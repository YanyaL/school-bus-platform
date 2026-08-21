package com.schoolbus.cdcsync.canal;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.protocol.Message;
import com.schoolbus.cdcsync.config.CanalConnectionProperties;
import com.schoolbus.cdcsync.event.CdcEvent;
import com.schoolbus.cdcsync.messaging.CdcEventPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(
        prefix = "school-bus.cdc.canal",
        name = "enabled",
        havingValue = "true"
)
public class CanalCdcWorker implements SmartLifecycle {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(CanalCdcWorker.class);

    private final CanalConnector connector;
    private final CanalConnectionProperties properties;
    private final CanalChangeMapper changeMapper;
    private final CdcEventPublisher eventPublisher;
    private final Counter publishedEvents;
    private final Counter failedBatches;
    private final AtomicBoolean running = new AtomicBoolean();
    private ExecutorService executor;

    public CanalCdcWorker(
            CanalConnector connector,
            CanalConnectionProperties properties,
            CanalChangeMapper changeMapper,
            CdcEventPublisher eventPublisher,
            MeterRegistry meterRegistry
    ) {
        this.connector = connector;
        this.properties = properties;
        this.changeMapper = changeMapper;
        this.eventPublisher = eventPublisher;
        this.publishedEvents = Counter.builder("school_bus_cdc_events_total")
                .tag("result", "published")
                .register(meterRegistry);
        this.failedBatches = Counter.builder("school_bus_cdc_batches_total")
                .tag("result", "failed")
                .register(meterRegistry);
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "canal-cdc-worker");
            thread.setDaemon(false);
            return thread;
        });
        executor.submit(this::runLoop);
    }

    private void runLoop() {
        while (running.get()) {
            try {
                consumeConnected();
            } catch (RuntimeException exception) {
                if (!running.get()) {
                    break;
                }
                failedBatches.increment();
                LOGGER.error("Canal CDC loop failed; reconnecting", exception);
                disconnectSafely();
                pause(properties.retryDelay());
            }
        }
        disconnectSafely();
    }

    private void consumeConnected() {
        connector.connect();
        connector.subscribe(properties.filter());
        connector.rollback();
        LOGGER.info(
                "Canal CDC connected to {}:{}/{} with filter {}",
                properties.host(),
                properties.port(),
                properties.destination(),
                properties.filter()
        );

        while (running.get()) {
            consumeOneBatch();
        }
    }

    private void consumeOneBatch() {
        Message message = connector.getWithoutAck(properties.batchSize());
        long batchId = message.getId();
        if (batchId == -1 || message.getEntries().isEmpty()) {
            pause(properties.idleDelay());
            return;
        }

        try {
            List<CdcEvent> events = changeMapper.map(message);
            for (CdcEvent event : events) {
                eventPublisher.publish(event);
                publishedEvents.increment();
            }
            connector.ack(batchId);
        } catch (RuntimeException exception) {
            rollbackSafely(batchId);
            throw exception;
        }
    }

    private void rollbackSafely(long batchId) {
        try {
            connector.rollback(batchId);
        } catch (RuntimeException rollbackException) {
            LOGGER.warn(
                    "Cannot roll back Canal batch {}",
                    batchId,
                    rollbackException
            );
        }
    }

    private void disconnectSafely() {
        try {
            connector.disconnect();
        } catch (RuntimeException exception) {
            LOGGER.debug("Canal connector was already disconnected", exception);
        }
    }

    private void pause(Duration duration) {
        try {
            TimeUnit.MILLISECONDS.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            running.set(false);
        }
    }

    @Override
    public void stop() {
        running.set(false);
        disconnectSafely();
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }
}
