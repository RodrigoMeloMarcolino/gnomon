package io.gnomon.catalog.application;

import java.util.List;

public interface PublicCalendarUseCase {

  List<PublicCalendarResult> listActive(String tenantSlug);
}
