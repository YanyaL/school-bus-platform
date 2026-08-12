package com.schoolbus.transport.infrastructure.persistence.route;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("integration-test")
@Transactional
class RouteMapperIntegrationTest {

    private static final LocalDateTime NOW =
            LocalDateTime.ofInstant(
                    java.time.Instant.parse("2026-08-12T00:00:00Z"),
                    ZoneOffset.UTC
            );

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(
                    DockerImageName.parse("mysql:8.4.0")
            )
                    .withDatabaseName("school_bus_route_test")
                    .withUsername("school_bus")
                    .withPassword("school_bus");

    @Autowired
    private RouteMapper routeMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTable() {
        jdbcTemplate.update("DELETE FROM transport_route");
    }

    @Test
    void shouldInsertAndQueryRouteById() {
        RouteDataObject route = newRoute(
                "11111111-1111-1111-1111-111111111111",
                "MAIN-EAST-01"
        );

        assertThat(routeMapper.insertRoute(route)).isEqualTo(1);
        assertThat(route.getId()).isNotNull();

        RouteDataObject loaded = routeMapper.selectById(route.getId());

        assertThat(loaded.getRouteCode()).isEqualTo("MAIN-EAST-01");
        assertThat(loaded.getDepartureCampus()).isEqualTo("MAIN");
        assertThat(loaded.getEstimatedDurationMinutes()).isEqualTo(40);
    }

    @Test
    void shouldQueryRouteByRouteCode() {
        RouteDataObject route = newRoute(
                "11111111-1111-1111-1111-111111111111",
                "MAIN-EAST-01"
        );
        routeMapper.insertRoute(route);

        RouteDataObject loaded = routeMapper.selectByRouteCode(
                "MAIN-EAST-01"
        );

        assertThat(loaded.getArrivalCampus()).isEqualTo("EAST");
    }

    @Test
    void shouldRejectDuplicateRouteCode() {
        routeMapper.insertRoute(newRoute(
                "11111111-1111-1111-1111-111111111111",
                "MAIN-EAST-01"
        ));

        assertThatThrownBy(() -> routeMapper.insertRoute(newRoute(
                "22222222-2222-2222-2222-222222222222",
                "MAIN-EAST-01"
        ))).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void shouldUpdateRouteWithVersionCheck() {
        RouteDataObject route = newRoute(
                "11111111-1111-1111-1111-111111111111",
                "MAIN-EAST-01"
        );
        routeMapper.insertRoute(route);

        route.setStatus("DISABLED");
        route.setVersion(1L);
        route.setUpdatedAt(NOW.plusSeconds(30));

        assertThat(routeMapper.updateWithVersion(route, 0L))
                .isEqualTo(1);
        assertThat(routeMapper.updateWithVersion(route, 0L))
                .isZero();
    }

    @Test
    void shouldPageRoutesByStatus() {
        routeMapper.insertRoute(newRoute(
                "11111111-1111-1111-1111-111111111111",
                "MAIN-EAST-01"
        ));
        RouteDataObject disabled = newRoute(
                "22222222-2222-2222-2222-222222222222",
                "MAIN-WEST-01"
        );
        disabled.setDepartureCampus("MAIN");
        disabled.setArrivalCampus("WEST");
        disabled.setStatus("DISABLED");
        routeMapper.insertRoute(disabled);

        List<RouteDataObject> enabled = routeMapper.selectAll(
                "ENABLED",
                0,
                20
        );

        assertThat(enabled).hasSize(1);
        assertThat(enabled.getFirst().getRouteCode())
                .isEqualTo("MAIN-EAST-01");
        assertThat(routeMapper.count("ENABLED")).isEqualTo(1);
    }

    private RouteDataObject newRoute(
            String routeNumber,
            String routeCode
    ) {
        RouteDataObject route = new RouteDataObject();
        route.setRouteNumber(routeNumber);
        route.setRouteCode(routeCode);
        route.setDepartureCampus("MAIN");
        route.setArrivalCampus("EAST");
        route.setEstimatedDurationMinutes(40);
        route.setStatus("ENABLED");
        route.setVersion(0L);
        route.setCreatedAt(NOW);
        route.setUpdatedAt(NOW);
        return route;
    }
}
