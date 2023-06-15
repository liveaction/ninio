package com.davfx.ninio.snmp.encryption;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SecurityProtocols {

    private static final List<AuthProtocol> AUTH_GENERIC_LIST = ImmutableList.of(new AuthMD5(), new AuthSHA1(), new AuthSHA256());
    private static final List<PrivacyProtocol> PRIVACY_GENERIC_LIST = ImmutableList.of(new PrivDES(), new PrivAES(), new PrivAES256());

    private static final Map<String, AuthProtocol> AUTH_GENERIC_MAP;
    private static final Map<String, PrivacyProtocol> PRIVACY_GENERIC_MAP;

    static {
        AUTH_GENERIC_MAP = AUTH_GENERIC_LIST.stream()
                .map(authGeneric -> Maps.immutableEntry(authGeneric.protocolName(), authGeneric))
                .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

        PRIVACY_GENERIC_MAP = PRIVACY_GENERIC_LIST.stream()
                .map(privacyGeneric -> Maps.immutableEntry(privacyGeneric.name(), privacyGeneric))
                .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
    }


    public static AuthProtocol getAuthenticationProtocol(String authAlgorithm) {
        return Optional.ofNullable(AUTH_GENERIC_MAP.get(authAlgorithm))
                .orElseThrow(() -> new IllegalArgumentException("auth algorithm " + authAlgorithm + " not found"));
    }

    public static PrivacyProtocol getPrivacyProtocol(String privicyAlgorithm) {
        return Optional.ofNullable(PRIVACY_GENERIC_MAP.get(privicyAlgorithm))
                .orElseThrow(() -> new IllegalArgumentException("privacy algorithm " + privicyAlgorithm + " not found"));
    }
}
