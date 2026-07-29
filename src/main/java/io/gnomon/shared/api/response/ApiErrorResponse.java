package io.gnomon.shared.api.response;

/** Top-level error envelope defined by ADR 0014. */
public record ApiErrorResponse(ApiError error) {

  public static ApiErrorResponse of(String code, String message, Object details) {
    return new ApiErrorResponse(new ApiError(code, message, details));
  }
}
