package com.schoolbus.transport.infrastructure.persistence.route;

import com.schoolbus.transport.domain.route.Campus;
import com.schoolbus.transport.domain.route.Route;
import com.schoolbus.transport.domain.route.RouteCode;
import com.schoolbus.transport.domain.route.RouteId;
import com.schoolbus.transport.domain.route.RouteNumber;
import com.schoolbus.transport.domain.route.RouteRepository;
import com.schoolbus.transport.domain.route.RouteStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
@Profile("!test")
public class MyBatisRouteRepository implements RouteRepository {

    private static final ZoneOffset DATABASE_ZONE = ZoneOffset.UTC;

    private final RouteMapper routeMapper;

    public MyBatisRouteRepository(RouteMapper routeMapper) {
        this.routeMapper = Objects.requireNonNull(
                routeMapper,
                "routeMapper must not be null"
        );
    }

    @Override
    public Route save(Route route) {
        Route validatedRoute = Objects.requireNonNull(
                route,
                "route must not be null"
        );
        if (validatedRoute.isNew()) {
            RouteDataObject dataObject = toDataObject(validatedRoute);
            int insertedRows = routeMapper.insertRoute(dataObject);
            if (insertedRows != 1 || dataObject.getId() == null) {
                throw new IllegalStateException(
                        "failed to insert route"
                );
            }
            return validatedRoute.withId(
                    RouteId.of(dataObject.getId())
            );
        }

        RouteDataObject dataObject = toDataObject(validatedRoute);
        long expectedVersion = validatedRoute.version() - 1L;
        int updatedRows = routeMapper.updateWithVersion(
                dataObject,
                expectedVersion
        );
        if (updatedRows != 1) {
            throw new OptimisticLockingFailureException(
                    "route was modified by another request"
            );
        }
        return validatedRoute;
    }

    @Override
    public Optional<Route> findById(RouteId routeId) {
        RouteId validatedId = Objects.requireNonNull(
                routeId,
                "routeId must not be null"
        );
        RouteDataObject dataObject = routeMapper.selectById(
                validatedId.value()
        );
        return Optional.ofNullable(dataObject).map(this::toDomain);
    }

    @Override
    public Optional<Route> findByRouteCode(RouteCode routeCode) {
        RouteCode validatedCode = Objects.requireNonNull(
                routeCode,
                "routeCode must not be null"
        );
        RouteDataObject dataObject = routeMapper.selectByRouteCode(
                validatedCode.value()
        );
        return Optional.ofNullable(dataObject).map(this::toDomain);
    }

    @Override
    public List<Route> findAll(
            RouteStatus status,
            int offset,
            int limit
    ) {
        String statusFilter = status == null ? null : status.name();
        return routeMapper
                .selectAll(statusFilter, offset, limit)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public int count(RouteStatus status) {
        String statusFilter = status == null ? null : status.name();
        return routeMapper.count(statusFilter);
    }

    private RouteDataObject toDataObject(Route route) {
        RouteDataObject dataObject = new RouteDataObject();
        if (!route.isNew()) {
            dataObject.setId(route.id().value());
        }
        dataObject.setRouteNumber(route.routeNumber().value());
        dataObject.setRouteCode(route.routeCode().value());
        dataObject.setDepartureCampus(route.departureCampus().name());
        dataObject.setArrivalCampus(route.arrivalCampus().name());
        dataObject.setEstimatedDurationMinutes(
                route.estimatedDurationMinutes()
        );
        dataObject.setStatus(route.status().name());
        dataObject.setVersion(route.version());
        dataObject.setCreatedAt(toLocalDateTime(route.createdAt()));
        dataObject.setUpdatedAt(toLocalDateTime(route.updatedAt()));
        return dataObject;
    }

    private Route toDomain(RouteDataObject dataObject) {
        return Route.restore(
                RouteId.of(dataObject.getId()),
                RouteNumber.of(dataObject.getRouteNumber()),
                RouteCode.of(dataObject.getRouteCode()),
                Campus.valueOf(dataObject.getDepartureCampus()),
                Campus.valueOf(dataObject.getArrivalCampus()),
                dataObject.getEstimatedDurationMinutes(),
                RouteStatus.valueOf(dataObject.getStatus()),
                dataObject.getVersion(),
                toInstant(dataObject.getCreatedAt()),
                toInstant(dataObject.getUpdatedAt())
        );
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, DATABASE_ZONE);
    }

    private static Instant toInstant(LocalDateTime localDateTime) {
        return localDateTime.toInstant(DATABASE_ZONE);
    }
}
