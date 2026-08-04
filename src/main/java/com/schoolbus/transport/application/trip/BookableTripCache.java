package com.schoolbus.transport.application.trip;

import java.util.List;
import java.util.Optional;

public interface BookableTripCache {

    Optional<List<BookableTripView>> findAll();

    void replaceAll(List<BookableTripView> trips);

    void evict();
}
