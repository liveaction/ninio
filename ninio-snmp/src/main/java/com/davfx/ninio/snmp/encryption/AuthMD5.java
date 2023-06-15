package com.davfx.ninio.snmp.encryption;

public final class AuthMD5 implements AuthProtocol {
    @Override
    public String protocolName() {
        return "MD5";
    }

    @Override
    public int authCodeLength() {
        return 12;
    }

    @Override
    public int digestLength() {
        return 16;
    }

}
