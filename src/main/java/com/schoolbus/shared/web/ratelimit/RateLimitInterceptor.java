package com.schoolbus.shared.web.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.Objects;

public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String PERMIT_ATTRIBUTE =
            RateLimitInterceptor.class.getName() + ".permit";

    private static final Map<RequestKey, String> PROTECTED_RESOURCES = Map.of(
            new RequestKey("POST", "/api/v1/auth/login"),
            RateLimitResource.LOGIN,
            new RequestKey("POST", "/api/v1/bookings"),
            RateLimitResource.CREATE_BOOKING,
            new RequestKey("POST", "/api/v1/payments/callback"),
            RateLimitResource.PAYMENT_CALLBACK
    );

    private final TrafficGuard trafficGuard;

    public RateLimitInterceptor(TrafficGuard trafficGuard) {
        this.trafficGuard = Objects.requireNonNull(trafficGuard);
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        String resource = PROTECTED_RESOURCES.get(
                new RequestKey(request.getMethod(), request.getRequestURI())
        );
        if (resource == null) {
            return true;
        }
        TrafficPermit permit = trafficGuard.acquire(resource);
        request.setAttribute(PERMIT_ATTRIBUTE, permit);
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception
    ) {
        Object permit = request.getAttribute(PERMIT_ATTRIBUTE);
        if (permit instanceof TrafficPermit trafficPermit) {
            trafficPermit.close();
        }
    }

    private record RequestKey(String method, String path) {
    }
}
