package io.gnomon.booking.api.response;

import io.gnomon.booking.application.port.in.AdminAppointmentPage;
import java.util.List;

public record PageResponse<T>(
    List<T> content, int page, int size, long totalElements, int totalPages, boolean last) {
  public static PageResponse<AdminAppointmentResponse> from(AdminAppointmentPage page) {
    return new PageResponse<>(
        page.content().stream().map(AdminAppointmentResponse::summaryFrom).toList(),
        page.page(),
        page.size(),
        page.totalElements(),
        page.totalPages(),
        page.last());
  }
}
