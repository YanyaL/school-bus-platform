package com.schoolbus.transport.infrastructure.persistence.route;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface RouteMapper {

    int insertRoute(RouteDataObject route);

    int updateWithVersion(
            @Param("route") RouteDataObject route,
            @Param("expectedVersion") long expectedVersion
    );

    RouteDataObject selectById(@Param("id") long id);

    RouteDataObject selectByRouteCode(@Param("routeCode") String routeCode);

    List<RouteDataObject> selectAll(
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int count(@Param("status") String status);
}
