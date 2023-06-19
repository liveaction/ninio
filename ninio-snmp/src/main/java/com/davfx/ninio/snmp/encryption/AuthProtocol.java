package com.davfx.ninio.snmp.encryption;

import com.google.common.collect.ImmutableMap;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public enum AuthProtocol {

    MD5("MD5", 12, 16),
    SHA1("SHA1",12, 20),
    SHA256("SHA-256", 24,32);

    private final String algorithm;
    private final int authCodeLength;
    private final int digestLength;

    AuthProtocol(String algorithm, int authCodeLength, int digestLength) {
        this.algorithm = algorithm;
        this.authCodeLength = authCodeLength;
        this.digestLength = digestLength;
    }

    public String algorithm() {
        return algorithm;
    }

    public int authCodeLength() {
        return authCodeLength;
    }

    public int digestLength() {
        return digestLength;
    }

    static final Map<String, AuthProtocol> LOOKUP = Arrays.stream(AuthProtocol.values())
            .collect(ImmutableMap.toImmutableMap(authProtocol -> authProtocol.algorithm, Function.identity()));

    public static AuthProtocol fromAlgorithm(String algorithm) {
        return Optional.ofNullable(LOOKUP.get(algorithm))
                .orElseThrow(() -> new IllegalArgumentException("auth algorithm " + algorithm + " not found"));
    }
}
