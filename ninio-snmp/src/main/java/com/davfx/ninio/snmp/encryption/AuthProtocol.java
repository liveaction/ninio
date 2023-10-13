package com.davfx.ninio.snmp.encryption;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

public enum AuthProtocol {

    MD5("MD5", 12, 16),
    SHA1("SHA1",12, 20, ImmutableSet.of("SHA", "SHA-1")),
    SHA256("SHA-256", 24,32);

    private final String algorithm;
    private final int authCodeLength;
    private final int digestLength;
    private final Set<String> alternativeNames;

    AuthProtocol(String algorithm, int authCodeLength, int digestLength, Set<String> alternativeNames) {
        this.algorithm = algorithm;
        this.authCodeLength = authCodeLength;
        this.digestLength = digestLength;
        this.alternativeNames = alternativeNames;
    }

    AuthProtocol(String algorithm, int authCodeLength, int digestLength) {
        this(algorithm, authCodeLength, digestLength, ImmutableSet.of());
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
            .flatMap(authProtocol -> {
                return Stream.concat(Stream.of(authProtocol.algorithm), authProtocol.alternativeNames.stream())
                        .map(algorithmName -> Maps.immutableEntry(algorithmName, authProtocol));
            })
            .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

    public static AuthProtocol fromAlgorithm(String algorithm) {
        return Optional.ofNullable(LOOKUP.get(algorithm))
                .orElseThrow(() -> new IllegalArgumentException("auth algorithm " + algorithm + " not found"));
    }
}
