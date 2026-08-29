package com.schoolbus.transport.api.admin;

import com.schoolbus.shared.api.GlobalExceptionHandler;
import com.schoolbus.shared.config.SecurityConfig;
import com.schoolbus.transport.application.route.DuplicateRouteCodeException;
import com.schoolbus.transport.application.route.RouteManagementApplicationService;
import com.schoolbus.transport.application.route.RouteNotFoundException;
import com.schoolbus.transport.application.route.RouteView;
import com.schoolbus.transport.domain.route.Campus;
import com.schoolbus.transport.domain.route.RouteStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("web-test")
@WebMvcTest(AdminRouteController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class
})
class AdminRouteControllerTest {

    private static final String ROUTE_NUMBER =
            "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RouteManagementApplicationService applicationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldCreateRouteForAdmin() throws Exception {
        when(applicationService.createRoute(any()))
                .thenReturn(routeView(RouteStatus.ENABLED, 0L));

        mockMvc.perform(
                        post("/api/v1/admin/routes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "routeCode": "MAIN-EAST-01",
                                          "departureCampus": "MAIN",
                                          "arrivalCampus": "EAST",
                                          "estimatedDurationMinutes": 40
                                        }
                                        """)
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_ADMIN"
                                        )
                                ))
                )
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "/api/v1/admin/routes/2001"
                ))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.routeId").value("2001"))
                .andExpect(jsonPath("$.data.routeCode")
                        .value("MAIN-EAST-01"))
                .andExpect(jsonPath("$.data.departureCampus")
                        .value("MAIN"))
                .andExpect(jsonPath("$.data.status").value("ENABLED"));

        verify(applicationService).createRoute(any());
    }

    @Test
    void shouldRejectStudentAccessWithForbidden() throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/routes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "routeCode": "MAIN-EAST-01",
                                          "departureCampus": "MAIN",
                                          "arrivalCampus": "EAST",
                                          "estimatedDurationMinutes": 40
                                        }
                                        """)
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_STUDENT"
                                        )
                                ))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void shouldRejectUnauthenticatedAccess() throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/routes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "routeCode": "MAIN-EAST-01",
                                          "departureCampus": "MAIN",
                                          "arrivalCampus": "EAST",
                                          "estimatedDurationMinutes": 40
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(applicationService);
    }

    @Test
    void shouldRejectInvalidCreateRequest() throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/routes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "routeCode": "",
                                          "departureCampus": "MAIN",
                                          "arrivalCampus": "EAST",
                                          "estimatedDurationMinutes": 0
                                        }
                                        """)
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_ADMIN"
                                        )
                                ))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void shouldRejectSameCampusRequest() throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/routes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "routeCode": "MAIN-MAIN-01",
                                          "departureCampus": "MAIN",
                                          "arrivalCampus": "MAIN",
                                          "estimatedDurationMinutes": 40
                                        }
                                        """)
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_ADMIN"
                                        )
                                ))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_ROUTE_DIRECTION"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void shouldFindRouteForAdmin() throws Exception {
        when(applicationService.findById(2001L))
                .thenReturn(routeView(RouteStatus.ENABLED, 0L));

        mockMvc.perform(
                        get("/api/v1/admin/routes/2001")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_ADMIN"
                                        )
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routeId").value("2001"));
    }

    @Test
    void shouldListRoutesForAdmin() throws Exception {
        when(applicationService.listRoutes(
                RouteStatus.ENABLED,
                0,
                20
        )).thenReturn(List.of(
                routeView(RouteStatus.ENABLED, 0L)
        ));

        mockMvc.perform(
                        get("/api/v1/admin/routes")
                                .param("status", "ENABLED")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_ADMIN"
                                        )
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].routeId").value("2001"));
    }

    @Test
    void shouldUpdateRouteStatusForAdmin() throws Exception {
        when(applicationService.updateStatus(any()))
                .thenReturn(routeView(RouteStatus.DISABLED, 1L));

        mockMvc.perform(
                        patch("/api/v1/admin/routes/2001/status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "status": "DISABLED",
                                          "version": 0
                                        }
                                        """)
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_ADMIN"
                                        )
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.version").value(1));
    }

    @Test
    void shouldReturnNotFoundWhenRouteMissing() throws Exception {
        when(applicationService.findById(9999L))
                .thenThrow(new RouteNotFoundException(9999L));

        mockMvc.perform(
                        get("/api/v1/admin/routes/9999")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_ADMIN"
                                        )
                                ))
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROUTE_NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abc",
            "0",
            "9223372036854775808"
    })
    void shouldRejectInvalidRouteId(String routeId) throws Exception {
        mockMvc.perform(
                        get("/api/v1/admin/routes/" + routeId)
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_ADMIN"
                                        )
                                ))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void shouldReturnConflictWhenRouteCodeDuplicated() throws Exception {
        when(applicationService.createRoute(any()))
                .thenThrow(new DuplicateRouteCodeException("MAIN-EAST-01"));

        mockMvc.perform(
                        post("/api/v1/admin/routes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "routeCode": "MAIN-EAST-01",
                                          "departureCampus": "MAIN",
                                          "arrivalCampus": "EAST",
                                          "estimatedDurationMinutes": 40
                                        }
                                        """)
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_ADMIN"
                                        )
                                ))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("ROUTE_CODE_ALREADY_EXISTS"));
    }

    private RouteView routeView(RouteStatus status, long version) {
        Instant timestamp = Instant.parse("2026-08-12T00:00:00Z");
        return new RouteView(
                2001L,
                ROUTE_NUMBER,
                "MAIN-EAST-01",
                Campus.MAIN,
                Campus.EAST,
                40,
                status,
                version,
                timestamp,
                timestamp
        );
    }
}
