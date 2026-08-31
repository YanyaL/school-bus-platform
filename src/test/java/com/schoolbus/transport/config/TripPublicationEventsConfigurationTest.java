package com.schoolbus.transport.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolbus.payment.infrastructure.outbox.OutboxMapper;
import com.schoolbus.transport.application.trip.TripPublicationOutboxPort;
import com.schoolbus.transport.infrastructure.outbox.MyBatisTripPublicationOutbox;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TripPublicationEventsConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TripPublicationEventsConfiguration.class);

    @Test
    void isDisabledByDefaultWithoutDatabaseOrBrokerDependencies() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(TripPublicationOutboxPort.class);
            assertThat(context).doesNotHaveBean(MyBatisTripPublicationOutbox.class);
            assertThat(context).doesNotHaveBean(Declarables.class);
        });
    }

    @Test
    void explicitlyDisabledKeepsLegacyPublicationPath() {
        runner.withPropertyValues("school-bus.transport.publication-events.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(TripPublicationOutboxPort.class);
                    assertThat(context).doesNotHaveBean(Declarables.class);
                });
    }

    @Test
    void enablingCreatesOneOutboxAndBoundedDurableShadowTopology() {
        runner.withPropertyValues("school-bus.transport.publication-events.enabled=true")
                .withBean(OutboxMapper.class, () -> mock(OutboxMapper.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(TripPublicationOutboxPort.class);
                    assertThat(context.getBean(TripPublicationOutboxPort.class)).isInstanceOf(MyBatisTripPublicationOutbox.class);
                    Queue queue = context.getBean(Declarables.class).getDeclarablesByType(Queue.class).getFirst();
                    assertThat(queue.isDurable()).isTrue();
                    assertThat(queue.getName()).endsWith(".shadow");
                    assertThat(queue.getArguments()).containsEntry("x-overflow", "reject-publish")
                            .containsEntry("x-max-length", 10000L);
                });
    }
}
