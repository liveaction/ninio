package com.davfx.ninio.snmp.encryption;

public final class PrivAES256 implements PrivacyProtocol {

    @Override
    public String name() {
        return "AES-256";
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
        return 32;
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
