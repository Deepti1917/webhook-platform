package com.webhookplatform.security;

import com.webhookplatform.common.BadRequestException;

public class UnsafeUrlException extends BadRequestException {
    public UnsafeUrlException(String message) {
        super(message);
    }
}
