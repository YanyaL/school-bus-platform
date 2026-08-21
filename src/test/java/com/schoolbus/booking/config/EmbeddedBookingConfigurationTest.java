package com.schoolbus.booking.config;

import com.schoolbus.booking.api.BookingController;
import com.schoolbus.booking.application.booking.BookingApplicationService;
import com.schoolbus.booking.application.booking.BookingCancellationApplicationService;
import com.schoolbus.booking.application.booking.BookingCancellationTransaction;
import com.schoolbus.booking.application.booking.BookingCreationTransaction;
import com.schoolbus.booking.application.booking.BookingExpirationApplicationService;
import com.schoolbus.booking.application.booking.BookingExpirationMessageApplicationService;
import com.schoolbus.booking.application.booking.BookingExpirationTransaction;
import com.schoolbus.booking.application.booking.BookingQueryApplicationService;
import com.schoolbus.booking.application.payment.PaymentSucceededBookingTransaction;
import com.schoolbus.booking.application.tripcancellation.TripCancellationBookingTransaction;
import com.schoolbus.booking.infrastructure.messaging.BookingExpirationListener;
import com.schoolbus.booking.infrastructure.messaging.BookingExpirationRabbitConfiguration;
import com.schoolbus.booking.infrastructure.messaging.PaymentSucceededListener;
import com.schoolbus.booking.infrastructure.messaging.PaymentSucceededRetryAttemptResolver;
import com.schoolbus.booking.infrastructure.messaging.RabbitBookingExpirationEventPublisher;
import com.schoolbus.booking.infrastructure.messaging.RabbitPaymentSucceededRetryPublisher;
import com.schoolbus.booking.infrastructure.messaging.TripCancellationRequestedListener;
import com.schoolbus.booking.infrastructure.outbox.BookingExpirationOutboxRelay;
import com.schoolbus.booking.infrastructure.outbox.BookingExpirationOutboxRelayScheduler;
import com.schoolbus.booking.infrastructure.outbox.MyBatisBookingExpirationOutbox;
import com.schoolbus.booking.infrastructure.payment.LocalRefundedBookingAdapter;
import com.schoolbus.booking.infrastructure.persistence.order.BookingOrderMapper;
import com.schoolbus.booking.infrastructure.persistence.order.MyBatisBookingOrderRepository;
import com.schoolbus.booking.infrastructure.scheduling.BookingExpirationScheduler;
import com.schoolbus.booking.infrastructure.transport.LocalBookableTripGateway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EmbeddedBookingConfigurationTest {

    private static final List<Class<?>> BOOKING_OWNED_TYPES = List.of(
            BookingController.class,
            BookingApplicationService.class,
            BookingCancellationApplicationService.class,
            BookingCancellationTransaction.class,
            BookingCreationTransaction.class,
            BookingExpirationApplicationService.class,
            BookingExpirationMessageApplicationService.class,
            BookingExpirationTransaction.class,
            BookingQueryApplicationService.class,
            PaymentSucceededBookingTransaction.class,
            TripCancellationBookingTransaction.class,
            BookingExpirationListener.class,
            BookingExpirationRabbitConfiguration.class,
            PaymentSucceededListener.class,
            PaymentSucceededRetryAttemptResolver.class,
            RabbitBookingExpirationEventPublisher.class,
            RabbitPaymentSucceededRetryPublisher.class,
            TripCancellationRequestedListener.class,
            BookingExpirationOutboxRelay.class,
            BookingExpirationOutboxRelayScheduler.class,
            MyBatisBookingExpirationOutbox.class,
            LocalRefundedBookingAdapter.class,
            BookingExpirationScheduler.class,
            LocalBookableTripGateway.class
    );

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withBean(
                            BookingApplicationService.class,
                            () -> mock(BookingApplicationService.class)
                    )
                    .withBean(
                            BookingQueryApplicationService.class,
                            () -> mock(BookingQueryApplicationService.class)
                    )
                    .withBean(
                            BookingCancellationApplicationService.class,
                            () -> mock(BookingCancellationApplicationService.class)
                    )
                    .withBean(
                            BookingOrderMapper.class,
                            () -> mock(BookingOrderMapper.class)
                    )
                    .withPropertyValues("spring.profiles.active=local")
                    .withUserConfiguration(ControllerAndRepositoryImport.class);

    @Test
    void keepsEmbeddedBookingEnabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(BookingController.class);
            assertThat(context).hasSingleBean(MyBatisBookingOrderRepository.class);
        });
    }

    @Test
    void removesBookingControllerWhenCloudBookingOwnsRoute() {
        contextRunner
                .withPropertyValues("school-bus.booking.embedded.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(BookingController.class);
                    assertThat(context).hasSingleBean(MyBatisBookingOrderRepository.class);
                });
    }

    @Test
    void removesEveryBookingOwnedBeanWhenCloudBookingOwnsRuntime() {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "spring.profiles.active=local",
                        "school-bus.booking.embedded.enabled=false"
                )
                .withUserConfiguration(AllBookingOwnedTypesImport.class)
                .run(context -> BOOKING_OWNED_TYPES.forEach(type ->
                        assertThat(context).doesNotHaveBean(type)
                ));
    }

    @Test
    void everyBookingOwnedTypeUsesTheOwnershipCondition() {
        BOOKING_OWNED_TYPES.forEach(type -> assertThat(
                type.isAnnotationPresent(ConditionalOnEmbeddedBooking.class)
        ).as(type.getName() + " must be guarded").isTrue());
    }

    @Configuration(proxyBeanMethods = false)
    @Import({BookingController.class, MyBatisBookingOrderRepository.class})
    static class ControllerAndRepositoryImport {
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            BookingController.class,
            BookingApplicationService.class,
            BookingCancellationApplicationService.class,
            BookingCancellationTransaction.class,
            BookingCreationTransaction.class,
            BookingExpirationApplicationService.class,
            BookingExpirationMessageApplicationService.class,
            BookingExpirationTransaction.class,
            BookingQueryApplicationService.class,
            PaymentSucceededBookingTransaction.class,
            TripCancellationBookingTransaction.class,
            BookingExpirationListener.class,
            BookingExpirationRabbitConfiguration.class,
            PaymentSucceededListener.class,
            PaymentSucceededRetryAttemptResolver.class,
            RabbitBookingExpirationEventPublisher.class,
            RabbitPaymentSucceededRetryPublisher.class,
            TripCancellationRequestedListener.class,
            BookingExpirationOutboxRelay.class,
            BookingExpirationOutboxRelayScheduler.class,
            MyBatisBookingExpirationOutbox.class,
            LocalRefundedBookingAdapter.class,
            BookingExpirationScheduler.class,
            LocalBookableTripGateway.class
    })
    static class AllBookingOwnedTypesImport {
    }
}
