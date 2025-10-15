package com.lv.xhsmcp.xhs;

import java.io.Serializable;

public final class Result<T> implements Serializable {
        private final boolean success;
        private final BizErrorCode code;
        private final String message;
        private final T data;

        private Result(boolean success, BizErrorCode code, String message, T data) {
            this.success = success;
            this.code = code;
            this.message = message;
            this.data = data;
        }

        public static <T> Result<T> ok() {
            return new Result<>(true, BizErrorCode.OK, "OK", null);
        }

        public static <T> Result<T> ok(T data) {
            return new Result<>(true, BizErrorCode.OK, "OK", data);
        }
    public static <T> Result<T> ok(String message) {
        return new Result<>(true, BizErrorCode.OK, message, null);
    }

        public static <T> Result<T> ok(T data,String message) {
            return new Result<>(true, BizErrorCode.OK, message, data);
        }

        public static <T> Result<T> image(T data,String message) {
            return new Result<>(true, BizErrorCode.OK, message, data);
        }

        public static <T> Result<T> fail(BizErrorCode code, String message) {
            return new Result<>(false, code, message, null);
        }

        public boolean isSuccess() { return success; }
        public BizErrorCode getCode() { return code; }
        public String getMessage() { return message; }
        public T getData() { return data; }

        @Override
        public String toString() {
            return "Result{" +
                    "success=" + success +
                    ", code=" + code +
                    ", message='" + message + '\'' +
                    ", data=" + data +
                    '}';
        }
    }