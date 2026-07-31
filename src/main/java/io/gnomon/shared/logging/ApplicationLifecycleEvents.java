package io.gnomon.shared.logging;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Emits lifecycle events once without coupling application modules to the logging backend. */
@Component
public final class ApplicationLifecycleEvents {

  private static final StructuredEventLogger LOGGER =
      StructuredEventLogger.getLogger(ApplicationLifecycleEvents.class);
  private final AtomicBoolean ready = new AtomicBoolean();
  private final AtomicBoolean shutdown = new AtomicBoolean();

  @EventListener(ApplicationReadyEvent.class)
  void applicationReady() {
    if (ready.compareAndSet(false, true)) {
      LOGGER.info("application.started", "application started", Map.of());
      LOGGER.info("application.ready", "application ready", Map.of());
    }
  }

  @EventListener(ContextClosedEvent.class)
  void applicationShutdown() {
    if (shutdown.compareAndSet(false, true)) {
      LOGGER.info("application.shutdown", "application shutdown", Map.of());
    }
  }
}
