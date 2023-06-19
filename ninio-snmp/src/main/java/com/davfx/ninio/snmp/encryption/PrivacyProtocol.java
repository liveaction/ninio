package com.davfx.ninio.snmp.encryption;

import com.google.common.collect.ImmutableMap;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public enum PrivacyProtocol {

    DES("DES", "DES/CBC/NoPadding", "DES", 8,16,16),
    AES("AES", "AES/CFB/NoPadding", "AES", 16,16,16),
    AES256("AES-256", "AES/CFB/NoPadding", "AES", 32, 32, 32);

    private final String algorithm;
    private final String id;
    private final String category;
    private final int keyLength;
    private final int minKeyLength;
    private final int maxKeyLength;

    PrivacyProtocol(String algorithm, String id, String category, int keyLength, int minKeyLength, int maxKeyLength) {
        this.algorithm = algorithm;
        this.id = id;
        this.category = category;
        this.keyLength = keyLength;
        this.minKeyLength = minKeyLength;
        this.maxKeyLength = maxKeyLength;
    }

    public String id() {
        return id;
    }

    public String category() {
        return category;
    }

    public int keyLength() {
        return keyLength;
    }

    public int minKeyLength() {
        return minKeyLength;
    }

    public int maxKeyLength() {
        return maxKeyLength;
    }

    static final Map<String, PrivacyProtocol> LOOKUP = Arrays.stream(PrivacyProtocol.values())
            .collect(ImmutableMap.toImmutableMap(authProtocol -> authProtocol.algorithm, Function.identity()));

    public static PrivacyProtocol fromAlgorithm(String algorithm) {
        return Optional.ofNullable(LOOKUP.get(algorithm))
                .orElseThrow(() -> new IllegalArgumentException("privacy algorithm " + algorithm + " not found"));
    }

}
