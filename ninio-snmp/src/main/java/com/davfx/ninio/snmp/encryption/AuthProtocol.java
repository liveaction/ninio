package com.davfx.ninio.snmp.encryption;

public interface AuthProtocol {

    String protocolName();

    int authCodeLength();

    int digestLength();

}
