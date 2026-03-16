package com.nageoffer.ai.ragent.framework.convention;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResultTest {

    @Test
    void testSuccessResult() {
        Result<String> result = new Result<String>()
                .setCode(Result.SUCCESS_CODE)
                .setMessage("success")
                .setData("hello")
                .setRequestId("req-123");

        assertTrue(result.isSuccess());
        assertEquals("hello", result.getData());
        assertEquals("success", result.getMessage());
        assertEquals("req-123", result.getRequestId());
    }

    @Test
    void testFailResult() {
        Result<String> result = new Result<String>()
                .setCode("500")
                .setMessage("error");

        assertFalse(result.isSuccess());
        assertEquals("500", result.getCode());
        assertEquals("error", result.getMessage());
    }

    @Test
    void testGenericType() {
        Result<Integer> result = new Result<Integer>()
                .setCode(Result.SUCCESS_CODE)
                .setData(100);

        assertTrue(result.isSuccess());
        assertEquals(100, result.getData());
    }
}
