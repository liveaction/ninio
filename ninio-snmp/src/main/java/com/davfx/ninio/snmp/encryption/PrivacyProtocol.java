package com.davfx.ninio.snmp.encryption;

public interface PrivacyProtocol {

    String name();

    String protocolId();

    String protocolClass();

    int keyLength();

    int minKeyLength();
    int maxKeyLength();
}
