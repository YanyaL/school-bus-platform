package com.schoolbus.transportquery.application;

import java.util.List;
import java.util.Optional;

public interface BookableTripCache {

    Optional<List<BookableTripView>> findAll();

    void replaceAll(List<BookableTripView> trips);

    void evict();
}
