package com.davfx.ninio.snmp;

import java.util.Objects;

public final class EncryptionEngineKey {

    public final String authDigestAlgorithm;

    public final String privEncryptionAlgorithm;

    public EncryptionEngineKey(String authDigestAlgorithm, String privEncryptionAlgorithm) {
        this.authDigestAlgorithm = authDigestAlgorithm;
        this.privEncryptionAlgorithm = privEncryptionAlgorithm;
    }

    @Override
    public int hashCode() {
        return Objects.hash(authDigestAlgorithm, privEncryptionAlgorithm);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof EncryptionEngineKey)) {
            return false;
        }
        EncryptionEngineKey other = (EncryptionEngineKey) obj;
        return Objects.equals(authDigestAlgorithm, other.authDigestAlgorithm)
                && Objects.equals(privEncryptionAlgorithm, other.privEncryptionAlgorithm);
    }
}
