package com.lotus.bixi.common.core.servlet;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StreamUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Request包装类：允许 body 重复读取
 *
 * @author 唐磊
 */
@Slf4j
public class RepeatBodyRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] bodyByteArray;

    private final Map<String, String[]> parameterMap;

    public RepeatBodyRequestWrapper(HttpServletRequest request) {
        super(request);
        this.bodyByteArray = getByteBody(request);
        this.parameterMap = new LinkedHashMap<>();
        super.getParameterMap().forEach((key, values) -> this.parameterMap.put(key, values.clone()));
    }

    @Override
    public BufferedReader getReader() {
        return ObjectUtils.isEmpty(this.bodyByteArray) ? null
                : new BufferedReader(new InputStreamReader(getInputStream()));
    }

    @Override
    public ServletInputStream getInputStream() {
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(this.bodyByteArray);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return false;
            }

            @Override
            public boolean isReady() {
                return false;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // doNoting
            }

            @Override
            public int read() {
                return byteArrayInputStream.read();
            }
        };
    }

    private static byte[] getByteBody(HttpServletRequest request) {
        byte[] body = new byte[0];
        try {
            body = StreamUtils.copyToByteArray(request.getInputStream());
        } catch (IOException e) {
            log.error("解析流中数据异常", e);
        }
        return body;
    }

    /**
     * 重写 getParameterMap() 方法 解决 undertow 中流被读取后，会进行标记，从而导致无法正确获取 body 中的表单数据的问题
     *
     * @return Map<String, String [ ]> parameterMap
     */
    @Override
    public Map<String, String[]> getParameterMap() {
        return this.parameterMap;
    }

    @Override
    public String getParameter(String name) {
        String[] values = this.parameterMap.get(name);
        return ObjectUtils.isEmpty(values) ? null : values[0];
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(this.parameterMap.keySet());
    }

    @Override
    public String[] getParameterValues(String name) {
        return this.parameterMap.get(name);
    }

}
