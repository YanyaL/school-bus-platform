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
import com.schoolbus.transport.config.ConditionalOnEmbeddedTransportAdmin;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

@Service
@ConditionalOnEmbeddedTransportAdmin
@Profile("!test")
public class RouteManagementApplicationService {

    private final RouteRepository routeRepository;
    private final Clock clock;

    public RouteManagementApplicationService(
            RouteRepository routeRepository,
            Clock clock
    ) {
        this.routeRepository = Objects.requireNonNull(
                routeRepository,
                "routeRepository must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public RouteView createRoute(CreateRouteCommand command) {
        CreateRouteCommand validated = Objects.requireNonNull(
                command,
                "command must not be null"
        );
        RouteCode routeCode = RouteCode.of(validated.routeCode());
        if (routeRepository.findByRouteCode(routeCode).isPresent()) {
            throw new DuplicateRouteCodeException(routeCode.value());
        }

        Route route;
        try {
            route = Route.create(
                    RouteNumber.generate(),
                    routeCode,
                    parseCampus(validated.departureCampus(), "departureCampus"),
                    parseCampus(validated.arrivalCampus(), "arrivalCampus"),
                    validated.estimatedDurationMinutes(),
                    clock.instant()
            );
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.INVALID_ROUTE_DEFINITION,
                    exception.getMessage()
            );
        }

        try {
            return toView(routeRepository.save(route));
        } catch (DataIntegrityViolationException exception) {
            throw mapIntegrityViolation(exception, routeCode.value());
        }
    }

    public RouteView findById(long routeId) {
        Route route = routeRepository
                .findById(RouteId.of(routeId))
                .orElseThrow(() -> new RouteNotFoundException(routeId));
        return toView(route);
    }

    public List<RouteView> listRoutes(
            RouteStatus status,
            int page,
            int size
    ) {
        int offset = page * size;
        return routeRepository
                .findAll(status, offset, size)
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public RouteView updateStatus(UpdateRouteStatusCommand command) {
        UpdateRouteStatusCommand validated = Objects.requireNonNull(
                command,
                "command must not be null"
        );
        Route route = routeRepository
                .findById(RouteId.of(validated.routeId()))
                .orElseThrow(() -> new RouteNotFoundException(
                        validated.routeId()
                ));
        if (route.version() != validated.expectedVersion()) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }

        RouteStatus targetStatus = parseStatus(validated.status());
        try {
            applyStatusChange(route, targetStatus);
        } catch (IllegalStateException exception) {
            throw new RouteStatusConflictException(
                    exception.getMessage()
            );
        }

        try {
            return toView(routeRepository.save(route));
        } catch (OptimisticLockingFailureException exception) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
    }

    private void applyStatusChange(
            Route route,
            RouteStatus targetStatus
    ) {
        if (targetStatus == RouteStatus.ENABLED) {
            route.enable(clock.instant());
            return;
        }
        if (targetStatus == RouteStatus.DISABLED) {
            route.disable(clock.instant());
            return;
        }
        throw new IllegalArgumentException(
                "unsupported route status: " + targetStatus
        );
    }

    private Campus parseCampus(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(
                    ErrorCode.INVALID_ROUTE_DEFINITION,
                    fieldName + " must not be blank"
            );
        }
        try {
            return Campus.valueOf(value.strip().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.INVALID_ROUTE_DEFINITION,
                    "unsupported " + fieldName + ": " + value
            );
        }
    }

    private RouteStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException(
                    "status must not be blank"
            );
        }
        return RouteStatus.valueOf(status.strip().toUpperCase());
    }

    private RuntimeException mapIntegrityViolation(
            RuntimeException exception,
            String routeCode
    ) {
        String message = exception.getMessage();
        if (message != null && message.contains("uk_transport_route_code")) {
            return new DuplicateRouteCodeException(routeCode);
        }
        return exception;
    }

    private RouteView toView(Route route) {
        return new RouteView(
                route.id().value(),
                route.routeNumber().value(),
                route.routeCode().value(),
                route.departureCampus(),
                route.arrivalCampus(),
                route.estimatedDurationMinutes(),
                route.status(),
                route.version(),
                route.createdAt(),
                route.updatedAt()
        );
    }
}
