package com.schoolbus.shared.web.ratelimit;

import com.schoolbus.shared.api.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RateLimitInterceptorTest {

    @Test
    void shouldAcquireAndReleasePermitForProtectedEndpoint()
            throws Exception {
        TrafficGuard guard = mock(TrafficGuard.class);
        TrafficPermit permit = mock(TrafficPermit.class);
        when(guard.acquire(RateLimitResource.CREATE_BOOKING))
                .thenReturn(permit);
        RateLimitInterceptor interceptor = new RateLimitInterceptor(guard);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/bookings");

        interceptor.preHandle(request, response, new Object());
        verify(request).setAttribute(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(permit)
        );
        when(request.getAttribute(
                org.mockito.ArgumentMatchers.anyString()
        )).thenReturn(permit);
        interceptor.afterCompletion(
                request,
                response,
                new Object(),
                null
        );

        verify(guard).acquire(RateLimitResource.CREATE_BOOKING);
        verify(permit).close();
    }

    @Test
    void shouldIgnoreUnprotectedEndpoint() throws Exception {
        TrafficGuard guard = mock(TrafficGuard.class);
        RateLimitInterceptor interceptor = new RateLimitInterceptor(guard);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/system/ping");

        interceptor.preHandle(
                request,
                mock(HttpServletResponse.class),
                new Object()
        );

        verifyNoInteractions(guard);
    }

    @Test
    void shouldReturnStandard429ResponseWhenGuardBlocks() throws Exception {
        TrafficGuard guard = resource -> {
            throw new RateLimitExceededException(resource);
        };
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new RateLimitInterceptor(guard))
                .build();

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.message")
                        .value("too many requests for resource "
                                + RateLimitResource.LOGIN));
    }

    @RestController
    static class TestController {

        @PostMapping("/api/v1/auth/login")
        String login() {
            return "ok";
        }
    }
}
