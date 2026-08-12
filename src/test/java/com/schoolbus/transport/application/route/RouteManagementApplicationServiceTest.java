package com.schoolbus.transport.application.route;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;
import com.schoolbus.transport.domain.route.Campus;
import com.schoolbus.transport.domain.route.Route;
import com.schoolbus.transport.domain.route.RouteCode;
import com.schoolbus.transport.domain.route.RouteId;
import com.schoolbus.transport.domain.route.RouteNumber;
import com.schoolbus.transport.domain.route.RouteRepository;
import com.schoolbus.transport.domain.route.RouteStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteManagementApplicationServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-08-12T00:00:00Z");

    @Mock
    private RouteRepository routeRepository;

    private RouteManagementApplicationService service;

    @BeforeEach
    void setUp() {
        service = new RouteManagementApplicationService(
                routeRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldCreateRoute() {
        when(routeRepository.findByRouteCode(RouteCode.of("MAIN-EAST-01")))
                .thenReturn(Optional.empty());
        when(routeRepository.save(any())).thenAnswer(invocation -> {
            Route route = invocation.getArgument(0);
            return route.withId(RouteId.of(2001L));
        });

        RouteView view = service.createRoute(
                new CreateRouteCommand(
                        "MAIN-EAST-01",
                        "MAIN",
                        "EAST",
                        40
                )
        );

        assertThat(view.routeId()).isEqualTo(2001L);
        assertThat(view.routeCode()).isEqualTo("MAIN-EAST-01");
        assertThat(view.departureCampus()).isEqualTo(Campus.MAIN);
        assertThat(view.status()).isEqualTo(RouteStatus.ENABLED);
        verify(routeRepository).save(any(Route.class));
    }

    @Test
    void shouldRejectDuplicateRouteCodeBeforeInsert() {
        when(routeRepository.findByRouteCode(RouteCode.of("MAIN-EAST-01")))
                .thenReturn(Optional.of(sampleRoute(
                        RouteStatus.ENABLED,
                        0L
                )));

        assertThatThrownBy(() -> service.createRoute(
                new CreateRouteCommand(
                        "MAIN-EAST-01",
                        "MAIN",
                        "EAST",
                        40
                )
        )).isInstanceOf(DuplicateRouteCodeException.class);
    }

    @Test
    void shouldMapDuplicateRouteCodeFromDatabase() {
        when(routeRepository.findByRouteCode(any())).thenReturn(Optional.empty());
        when(routeRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException(
                        "Duplicate entry 'MAIN-EAST-01' for key "
                                + "'transport_route.uk_transport_route_code'"
                ));

        assertThatThrownBy(() -> service.createRoute(
                new CreateRouteCommand(
                        "MAIN-EAST-01",
                        "MAIN",
                        "EAST",
                        40
                )
        )).isInstanceOf(DuplicateRouteCodeException.class);
    }

    @Test
    void shouldRejectSameCampusInRouteDefinition() {
        assertThatThrownBy(() -> service.createRoute(
                new CreateRouteCommand(
                        "MAIN-MAIN-01",
                        "MAIN",
                        "MAIN",
                        40
                )
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).errorCode()
                )
                .isEqualTo(ErrorCode.INVALID_ROUTE_DEFINITION);
    }

    @Test
    void shouldFindRouteById() {
        when(routeRepository.findById(RouteId.of(2001L)))
                .thenReturn(Optional.of(sampleRoute(
                        RouteStatus.ENABLED,
                        0L
                )));

        RouteView view = service.findById(2001L);

        assertThat(view.routeCode()).isEqualTo("MAIN-EAST-01");
    }

    @Test
    void shouldListRoutes() {
        when(routeRepository.findAll(RouteStatus.ENABLED, 0, 20))
                .thenReturn(List.of(sampleRoute(
                        RouteStatus.ENABLED,
                        0L
                )));

        assertThat(service.listRoutes(RouteStatus.ENABLED, 0, 20))
                .hasSize(1);
    }

    @Test
    void shouldDisableRouteWithMatchingVersion() {
        Route route = sampleRoute(RouteStatus.ENABLED, 0L);
        when(routeRepository.findById(RouteId.of(2001L)))
                .thenReturn(Optional.of(route));
        when(routeRepository.save(any())).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        RouteView view = service.updateStatus(
                new UpdateRouteStatusCommand(
                        2001L,
                        "DISABLED",
                        0L
                )
        );

        assertThat(view.status()).isEqualTo(RouteStatus.DISABLED);
        assertThat(view.version()).isEqualTo(1L);
    }

    @Test
    void shouldRejectStatusUpdateWhenVersionMismatch() {
        when(routeRepository.findById(RouteId.of(2001L)))
                .thenReturn(Optional.of(sampleRoute(
                        RouteStatus.ENABLED,
                        2L
                )));

        assertThatThrownBy(() -> service.updateStatus(
                new UpdateRouteStatusCommand(
                        2001L,
                        "DISABLED",
                        1L
                )
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).errorCode()
                )
                .isEqualTo(ErrorCode.VERSION_CONFLICT);
    }

    @Test
    void shouldMapOptimisticLockFailureToVersionConflict() {
        Route route = sampleRoute(RouteStatus.ENABLED, 0L);
        when(routeRepository.findById(RouteId.of(2001L)))
                .thenReturn(Optional.of(route));
        when(routeRepository.save(any()))
                .thenThrow(new OptimisticLockingFailureException(
                        "conflict"
                ));

        assertThatThrownBy(() -> service.updateStatus(
                new UpdateRouteStatusCommand(
                        2001L,
                        "DISABLED",
                        0L
                )
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).errorCode()
                )
                .isEqualTo(ErrorCode.VERSION_CONFLICT);
    }

    private Route sampleRoute(RouteStatus status, long version) {
        return Route.restore(
                RouteId.of(2001L),
                RouteNumber.of(
                        "11111111-1111-1111-1111-111111111111"
                ),
                RouteCode.of("MAIN-EAST-01"),
                Campus.MAIN,
                Campus.EAST,
                40,
                status,
                version,
                NOW,
                NOW
        );
    }
}
