package com.ruoyi.wimo.exception;

/** 业务异常：Controller 统一捕获后转 R.fail(msg) */
public class WimoException extends RuntimeException
{
    public WimoException(String message)
    {
        super(message);
    }
}
