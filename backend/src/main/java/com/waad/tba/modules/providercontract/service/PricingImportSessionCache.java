package com.waad.tba.modules.providercontract.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PricingImportSessionCache {

    private final Map<String, Object> cache = new ConcurrentHashMap<>();

    public String put(Object data) {
        String sessionId = UUID.randomUUID().toString();
        cache.put(sessionId, data);
        return sessionId;
    }

    public Object get(String sessionId) {
        return cache.get(sessionId);
    }

    public void remove(String sessionId) {
        cache.remove(sessionId);
    }
}
