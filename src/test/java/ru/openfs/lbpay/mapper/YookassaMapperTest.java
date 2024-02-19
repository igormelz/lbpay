package ru.openfs.lbpay.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

class YookassaMapperTest {
    @Test
    void testCreateRequest() throws JsonProcessingException {
        var req = YookassaMapper.createRequest(1000L, "101010", 2.0, "http://return");
        assertEquals("2.0", req.amount().value());
        assertEquals("RUB", req.amount().currency());
        assertEquals("redirect", req.confirmation().type());
        assertEquals("http://return", req.confirmation().returnUrl());
        assertEquals("Оплата заказа №1000 по договору 101010", req.description());
        assertEquals("1000", req.metadata().paymentId());
    }
}
