package com.schoolbus.transport.infrastructure.persistence.route;

import com.schoolbus.transport.domain.route.Campus;
import com.schoolbus.transport.domain.route.Route;
import com.schoolbus.transport.domain.route.RouteCode;
import com.schoolbus.transport.domain.route.RouteId;
import com.schoolbus.transport.domain.route.RouteNumber;
import com.schoolbus.transport.domain.route.RouteStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyBatisRouteRepositoryTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-08-12T00:00:00Z");
    private static final LocalDateTime CREATED_AT_LOCAL =
            LocalDateTime.ofInstant(CREATED_AT, ZoneOffset.UTC);

    @Mock
    private RouteMapper routeMapper;

    private MyBatisRouteRepository repository;

    @BeforeEach
    void setUp() {
        repository = new MyBatisRouteRepository(routeMapper);
    }

    @Test
    void shouldInsertRouteAndReturnAssignedId() {
        Route route = Route.create(
                RouteNumber.of(
                        "11111111-1111-1111-1111-111111111111"
                ),
                RouteCode.of("MAIN-EAST-01"),
                Campus.MAIN,
                Campus.EAST,
                40,
                CREATED_AT
        );
        when(routeMapper.insertRoute(any())).thenAnswer(invocation -> {
            RouteDataObject dataObject = invocation.getArgument(0);
            dataObject.setId(2001L);
            return 1;
        });

        Route saved = repository.save(route);

        assertThat(saved.id()).isEqualTo(RouteId.of(2001L));
        ArgumentCaptor<RouteDataObject> captor =
                ArgumentCaptor.forClass(RouteDataObject.class);
        verify(routeMapper).insertRoute(captor.capture());
        assertThat(captor.getValue().getRouteCode())
                .isEqualTo("MAIN-EAST-01");
        assertThat(captor.getValue().getDepartureCampus())
                .isEqualTo("MAIN");
    }

    @Test
    void shouldUpdateRouteWithOptimisticLock() {
        Route route = Route.restore(
                RouteId.of(2001L),
                RouteNumber.of(
                        "11111111-1111-1111-1111-111111111111"
                ),
                RouteCode.of("MAIN-EAST-01"),
                Campus.MAIN,
                Campus.EAST,
                40,
                RouteStatus.DISABLED,
                1L,
                CREATED_AT,
                CREATED_AT.plusSeconds(10)
        );
        when(routeMapper.updateWithVersion(any(), eq(0L)))
                .thenReturn(1);

        Route saved = repository.save(route);

        assertThat(saved.version()).isEqualTo(1L);
        verify(routeMapper).updateWithVersion(
                any(RouteDataObject.class),
                eq(0L)
        );
    }

    @Test
    void shouldThrowWhenVersionConflictOccurs() {
        Route route = Route.restore(
                RouteId.of(2001L),
                RouteNumber.of(
                        "11111111-1111-1111-1111-111111111111"
                ),
                RouteCode.of("MAIN-EAST-01"),
                Campus.MAIN,
                Campus.EAST,
                40,
                RouteStatus.DISABLED,
                1L,
                CREATED_AT,
                CREATED_AT.plusSeconds(10)
        );
        when(routeMapper.updateWithVersion(any(), eq(0L)))
                .thenReturn(0);

        assertThatThrownBy(() -> repository.save(route))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void shouldFindRouteById() {
        RouteDataObject dataObject = dataObject();
        when(routeMapper.selectById(2001L)).thenReturn(dataObject);

        Optional<Route> route = repository.findById(RouteId.of(2001L));

        assertThat(route).isPresent();
        assertThat(route.orElseThrow().routeCode().value())
                .isEqualTo("MAIN-EAST-01");
    }

    @Test
    void shouldFindRouteByRouteCode() {
        RouteDataObject dataObject = dataObject();
        when(routeMapper.selectByRouteCode("MAIN-EAST-01"))
                .thenReturn(dataObject);

        Optional<Route> route = repository.findByRouteCode(
                RouteCode.of("MAIN-EAST-01")
        );

        assertThat(route).isPresent();
        assertThat(route.orElseThrow().departureCampus())
                .isEqualTo(Campus.MAIN);
    }

    private RouteDataObject dataObject() {
        RouteDataObject dataObject = new RouteDataObject();
        dataObject.setId(2001L);
        dataObject.setRouteNumber(
                "11111111-1111-1111-1111-111111111111"
        );
        dataObject.setRouteCode("MAIN-EAST-01");
        dataObject.setDepartureCampus("MAIN");
        dataObject.setArrivalCampus("EAST");
        dataObject.setEstimatedDurationMinutes(40);
        dataObject.setStatus(RouteStatus.ENABLED.name());
        dataObject.setVersion(0L);
        dataObject.setCreatedAt(CREATED_AT_LOCAL);
        dataObject.setUpdatedAt(CREATED_AT_LOCAL);
        return dataObject;
    }
}
