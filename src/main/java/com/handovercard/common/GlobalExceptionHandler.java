package com.handovercard.common;

import com.handovercard.auth.DuplicateEmailException;
import com.handovercard.auth.InvalidCredentialsException;
import com.handovercard.auth.InvalidTokenException;
import com.handovercard.auth.oauth2.SocialLoginException;
import com.handovercard.auth.oauth2.UnverifiedSocialEmailException;
import com.handovercard.card.InvalidCardStateException;
import com.handovercard.storage.StorageException;
import com.handovercard.team.TeamOperationException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.util.List;

// SSR 화면(@Controller)의 예외까지 JSON으로 바꿔버리지 않도록 REST 컨트롤러로만 범위를 제한한다.
@RestControllerAdvice(annotations = RestController.class)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(HttpStatus.NOT_FOUND.value(), "Not Found", ex.getMessage()));
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateEmail(DuplicateEmailException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(HttpStatus.CONFLICT.value(), "Conflict", ex.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.of(HttpStatus.UNAUTHORIZED.value(), "Unauthorized", ex.getMessage()));
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidToken(InvalidTokenException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.of(HttpStatus.UNAUTHORIZED.value(), "Unauthorized", ex.getMessage()));
    }

    /** 공급자가 이메일을 확인해 주지 않아 계정을 잇지 못한 경우. 자격 증명 문제이므로 401. */
    @ExceptionHandler({UnverifiedSocialEmailException.class, OAuth2AuthenticationException.class})
    public ResponseEntity<ApiErrorResponse> handleSocialLoginRejected(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.of(HttpStatus.UNAUTHORIZED.value(), "Unauthorized", ex.getMessage()));
    }

    /** 지원하지 않거나 설정되지 않은 공급자, 교환할 수 없는 인가 코드 등 요청 자체가 잘못된 경우. */
    @ExceptionHandler(SocialLoginException.class)
    public ResponseEntity<ApiErrorResponse> handleSocialLogin(SocialLoginException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Bad Request", ex.getMessage()));
    }

    @ExceptionHandler(InvalidCardStateException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCardState(InvalidCardStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(HttpStatus.CONFLICT.value(), "Conflict", ex.getMessage()));
    }

    @ExceptionHandler(TeamOperationException.class)
    public ResponseEntity<ApiErrorResponse> handleTeamOperation(TeamOperationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(HttpStatus.CONFLICT.value(), "Conflict", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Bad Request", "Validation failed", details));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Bad Request", ex.getMessage()));
    }

    /**
     * 저장소 오류는 원인을 함께 남긴다. 응답에 담기는 건 "Failed to store audio file" 같은 문구뿐이라,
     * 여기서 로깅하지 않으면 그 아래에 있던 실제 이유(자격증명 없음, 권한 거부, 리전 불일치)가
     * 통째로 사라져 서버에서 볼 방법이 없어진다.
     */
    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ApiErrorResponse> handleStorage(StorageException ex) {
        log.error("Audio storage failed", ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Bad Request", ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiErrorResponse.of(HttpStatus.PAYLOAD_TOO_LARGE.value(), "Payload Too Large", "Uploaded file exceeds the maximum allowed size"));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiErrorResponse> handleMultipart(MultipartException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Bad Request", "Malformed multipart request"));
    }

    /**
     * 여기까지 온 예외는 우리가 예상하지 못한 것들이다. 응답은 "Unexpected error occurred" 한 줄로
     * 덮어 클라이언트에 내부 사정을 흘리지 않되, 서버에는 반드시 남긴다. 로깅하지 않으면 500이
     * 났다는 사실 말고는 아무것도 알 수 없어 원인을 재현으로만 찾아야 한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error", "Unexpected error occurred"));
    }
}
