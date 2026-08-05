package io.gnomon.customers.api.response;

import java.util.List;

public record CustomerPageResponse(
    List<CustomerResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean last) {}
