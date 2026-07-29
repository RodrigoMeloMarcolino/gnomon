package io.gnomon.catalog.api.controller;

import io.gnomon.catalog.api.response.PublicCalendarResponse;
import io.gnomon.catalog.application.port.in.PublicCalendarUseCase;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/public/tenants/{tenantSlug}/calendars")
public class PublicCalendarController {

  private final PublicCalendarUseCase calendars;

  public PublicCalendarController(PublicCalendarUseCase calendars) {
    this.calendars = calendars;
  }

  @GetMapping
  List<PublicCalendarResponse> list(@PathVariable String tenantSlug) {
    return calendars.listActive(tenantSlug).stream().map(PublicCalendarResponse::from).toList();
  }
}
