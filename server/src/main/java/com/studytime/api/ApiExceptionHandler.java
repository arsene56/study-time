package com.studytime.api;

import com.studytime.api.ApiModels.ApiMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.format.DateTimeParseException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({
            IllegalArgumentException.class,
            DateTimeParseException.class,
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiMessage badRequest(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException validationException
                && validationException.getBindingResult().getFieldError() != null) {
            return new ApiMessage(validationException.getBindingResult().getFieldError().getDefaultMessage());
        }
        if (exception instanceof HttpMessageNotReadableException) {
            return new ApiMessage("请求内容格式不正确或包含不支持的字段");
        }
        return new ApiMessage(exception.getMessage());
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    ApiMessage unauthorized(AuthenticationCredentialsNotFoundException exception) {
        return new ApiMessage("请先完成身份验证");
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    ApiMessage forbidden(AccessDeniedException exception) {
        return new ApiMessage(exception.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.CONTENT_TOO_LARGE)
    ApiMessage uploadTooLarge(MaxUploadSizeExceededException exception) {
        return new ApiMessage("作业照片大小超过限制");
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    ApiMessage serviceUnavailable(IllegalStateException exception) {
        return new ApiMessage(exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    ApiMessage unexpected(Exception exception) {
        LOGGER.error("Unhandled API exception", exception);
        return new ApiMessage("系统暂时开了个小差，请稍后重试");
    }
}
