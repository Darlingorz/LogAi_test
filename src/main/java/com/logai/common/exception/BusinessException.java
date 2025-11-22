package com.logai.common.exception;

/**
 * 业务异常基类
 * 支持错误码和参数化消息
 */
public class BusinessException extends RuntimeException {

    private static final String DEFAULT_CODE = "BUSINESS_ERROR";

    private final String code;
    private final Object[] args;
    private final Throwable originalCause;

    public BusinessException(String message) {
        super(message);
        this.code = DEFAULT_CODE;
        this.args = new Object[0];
        this.originalCause = null;
    }

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
        this.args = new Object[0];
        this.originalCause = null;
    }

    public BusinessException(String code, String message, Object... args) {
        super(message);
        this.code = code;
        this.args = args;
        this.originalCause = null;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.code = DEFAULT_CODE;
        this.args = new Object[0];
        this.originalCause = cause;
    }

    public BusinessException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.args = new Object[0];
        this.originalCause = cause;
    }

    public String getCode() {
        return code;
    }

    public Object[] getArgs() {
        return args;
    }

    public Throwable getOriginalCause() {
        return originalCause;
    }

    /**
     * 创建带有详细信息的业务异常
     */
    public static BusinessException withDetail(String code, String message, String detail) {
        BusinessException ex = new BusinessException(code, message);
        // 使用异常消息存储详细信息
        ex.addSuppressed(new RuntimeException(detail));
        return ex;
    }

    /**
     * 创建参数验证异常
     */
    public static BusinessException validationError(String field, String message) {
        return new BusinessException("VALIDATION_ERROR", String.format("Field [%s] validation failed: %s", field, message)); // 字段[%s]验证失败: %s
    }

    /**
     * 创建资源不存在异常
     */
    public static BusinessException notFound(String resourceType, Object resourceId) {
        return new BusinessException("RESOURCE_NOT_FOUND",
                String.format("Resource [%s] does not exist, ID: %s", resourceType, resourceId)); // 资源[%s]不存在，ID: %s
    }

    /**
     * 创建未授权异常
     */
    public static BusinessException unauthorized(String operation) {
        return new BusinessException("UNAUTHORIZED",
                String.format("No permission to perform operation: %s", operation)); // 没有权限执行操作: %s
    }

    /**
     * 创建AI服务异常
     */
    public static BusinessException aiServiceError(String service, String message) {
        return new BusinessException("AI_SERVICE_ERROR",
                String.format("AI service [%s] error: %s", service, message)); // AI服务[%s]异常: %s
    }

    /**
     * 创建数据库异常
     */
    public static BusinessException databaseError(String operation, String message) {
        return new BusinessException("DATABASE_ERROR",
                String.format("Database operation [%s] failed: %s", operation, message)); // 数据库操作[%s]失败: %s
    }

    /**
     * 创建超时异常
     */
    public static BusinessException timeout(String operation, long timeoutMs) {
        return new BusinessException("TIMEOUT_ERROR",
                String.format("Operation [%s] timed out, timeout: %d ms", operation, timeoutMs)); // 操作[%s]超时，超时时间: %d毫秒
    }

    public static BusinessException OAuth2Exception(String errorCode, String message) {
        return new BusinessException(errorCode, message);
    }

}