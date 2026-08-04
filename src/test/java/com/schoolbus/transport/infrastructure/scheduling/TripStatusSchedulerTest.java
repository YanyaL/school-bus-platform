package com.schoolbus.transport.infrastructure.scheduling;

import com.schoolbus.transport.application.trip.TripStatusApplicationService;
import com.schoolbus.transport.application.trip.TripStatusUpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripStatusSchedulerTest {

    @Mock
    private TripStatusApplicationService applicationService;

    private TripStatusScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new TripStatusScheduler(applicationService);
    }

    @Test
    void shouldDelegateStatusUpdateToApplicationService() {
        when(applicationService.updateDueTripStatuses())
                .thenReturn(new TripStatusUpdateResult(2, 1, 0));

        scheduler.updateDueTripStatuses();

        verify(applicationService).updateDueTripStatuses();
    }

    @Test
    void shouldKeepSchedulerAliveWhenOneExecutionFails() {
        when(applicationService.updateDueTripStatuses())
                .thenThrow(new IllegalStateException("database down"));

        assertThatCode(scheduler::updateDueTripStatuses)
                .doesNotThrowAnyException();
        verify(applicationService).updateDueTripStatuses();
    }
}
