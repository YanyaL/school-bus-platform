package com.schoolbus.transport.infrastructure.persistence.route;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RouteReferenceMapper {

    RouteReferenceDataObject selectByRouteNo(
            @Param("routeNo") String routeNo
    );

    RouteReferenceDataObject selectById(
            @Param("id") Long id
    );
}
