package io.gnomon.catalog.application.port.in;

import io.gnomon.catalog.application.port.in.result.PublicCalendarResult;
import java.util.List;

public interface PublicCalendarUseCase {

  List<PublicCalendarResult> listActive(String tenantSlug);
}
