package io.gnomon.customers.application.port.in;

import java.util.List;

public record CustomerPage(
    List<CustomerResult> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean last) {}
