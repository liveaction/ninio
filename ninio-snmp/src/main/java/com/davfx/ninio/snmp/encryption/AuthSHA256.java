package com.davfx.ninio.snmp.encryption;

public final class AuthSHA256 implements AuthProtocol {
    @Override
    public String protocolName() {
        return "SHA-256";
    }

    @Override
    public int authCodeLength() {
        return 24;
    }

    @Override
    public int digestLength() {
        return 32;
    }

}
