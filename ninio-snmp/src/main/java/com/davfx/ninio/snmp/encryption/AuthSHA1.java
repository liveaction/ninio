package com.davfx.ninio.snmp.encryption;

public final class AuthSHA1 implements AuthProtocol {
    @Override
    public String protocolName() {
        return "SHA1";
    }

    @Override
    public int authCodeLength() {
        return 12;
    }

    @Override
    public int digestLength() {
        return 20;
    }

}
