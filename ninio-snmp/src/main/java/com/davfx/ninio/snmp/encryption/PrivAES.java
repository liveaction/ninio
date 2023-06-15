package com.davfx.ninio.snmp.encryption;

public final class PrivAES implements PrivacyProtocol {
    @Override
    public String name() {
        return "AES";
    }

    @Override
    public String protocolId() {
        return "AES/CFB/NoPadding";
    }

    @Override
    public String protocolClass() {
        return "AES";
    }

    @Override
    public int keyLength() {
        return 16;
    }

    @Override
    public int minKeyLength() {
        return keyLength();
    }

    @Override
    public int maxKeyLength() {
        return keyLength();
    }
}
