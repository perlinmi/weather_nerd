package com.weathernerd.weather.api;

import com.weathernerd.weather.client.PermissionsServiceException;
import com.weathernerd.weather.service.ForecastAccessDeniedException;
import com.weathernerd.weather.service.ForecastQuotaExceededException;
import com.weathernerd.weather.service.ForecastUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class WeatherApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(WeatherApiExceptionHandler.class);

    @ExceptionHandler(ForecastAccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(ForecastAccessDeniedException exception) {
        return problem(HttpStatus.FORBIDDEN, "Permission denied",
                "The caller is not permitted to access this forecast.");
    }

    @ExceptionHandler(PermissionsServiceException.class)
    public ResponseEntity<ProblemDetail> handlePermissionsFailure(
            PermissionsServiceException exception) {
        LOGGER.warn("Permissions service request failed", exception);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Permissions service unavailable",
                "Caller permissions could not be verified.");
    }

    @ExceptionHandler(ForecastQuotaExceededException.class)
    public ResponseEntity<ProblemDetail> handleQuotaExceeded(
            ForecastQuotaExceededException exception) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "Forecast quota exhausted",
                "The forecast provider's daily request limit has been reached.");
    }

    @ExceptionHandler(ForecastUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleForecastUnavailable(
            ForecastUnavailableException exception) {
        LOGGER.warn("Forecast provider response was unusable or unavailable", exception);
        return problem(HttpStatus.BAD_GATEWAY, "Forecast unavailable",
                "A current forecast could not be retrieved.");
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return ResponseEntity.status(status).body(problem);
    }
}
