package com.odcc.tienda.modules.reports.adapter.in.rest;

import com.odcc.tienda.modules.reports.application.exception.InvalidReportFilterException;
import com.odcc.tienda.shared.web.response.ApiResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ReportController.class)
public class ReportExceptionHandler {

    @ExceptionHandler(InvalidReportFilterException.class)
    public ResponseEntity<ApiResponseDto<Void>> invalidFilter(
        InvalidReportFilterException exception,
        HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(ApiResponseDto.error(
            status,
            "INVALID_REPORT_FILTER",
            exception.getMessage(),
            null,
            request.getRequestURI()
        ));
    }
}
