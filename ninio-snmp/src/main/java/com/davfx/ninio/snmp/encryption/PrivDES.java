package com.davfx.ninio.snmp.encryption;

public final class PrivDES implements PrivacyProtocol {
    @Override
    public String name() {
        return "DES";
    }

    @Override
    public String protocolId() {
        return "DES/CBC/NoPadding";
    }

    @Override
    public String protocolClass() {
        return "DES";
    }

    @Override
    public int keyLength() {
        return 8;
    }

    @Override
    public int minKeyLength() {
        return 16;
    }

    @Override
    public int maxKeyLength() {
        return 16;
    }
}
