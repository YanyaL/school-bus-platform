package com.schoolbus.transport.infrastructure.persistence.trip;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TripCancellationReconciliationMapper {

    List<Long> selectSettledAwaitingFinalization(
            @Param("settledBefore") LocalDateTime settledBefore,
            @Param("limit") int limit
    );
}
