package com.logai.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result implements Serializable {
    private static final long serialVersionUID = 1L;

    private int code;
    private String msg;
    private Object data;
    private LocalDateTime timestamp;

    public Result(int code, String msg, Object data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.timestamp = LocalDateTime.now();
    }

    public static Result success() {
        return new Result(200, "success", null);
    }

    public static Result success(Object data) {
        return new Result(200, "success", data);
    }

    public static Result success(String msg, Object data) {
        return new Result(200, msg, data);
    }

    public static Result failure(String msg) {
        return new Result(500, msg, null);
    }

    public static Result failure(String msg, Object data) {
        return new Result(500, msg, data);
    }

    public static Result failure(int code, String msg) {
        return new Result(code, msg, null);
    }

    public static Result failure(int code, String msg, Object data) {
        return new Result(code, msg, data);
    }

    public boolean isSuccess() {
        return this.code == 200;
    }
}