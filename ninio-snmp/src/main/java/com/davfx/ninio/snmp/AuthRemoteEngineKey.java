package com.davfx.ninio.snmp;

import com.davfx.ninio.core.Address;
import com.google.common.base.MoreObjects;

import java.util.Objects;

public final class AuthRemoteEngineKey {

    public final Address address;

    public final Auth auth;

    public AuthRemoteEngineKey(Address address, Auth auth) {
        this.address = address;
        this.auth = auth;
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, auth);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof AuthRemoteEngineKey)) {
            return false;
        }
        AuthRemoteEngineKey other = (AuthRemoteEngineKey) obj;
        return Objects.equals(address, other.address)
                && Objects.equals(auth, other.auth);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("address", address)
                .add("auth", auth.login + "," + auth.authDigestAlgorithm + "," + auth.privEncryptionAlgorithm)
                .toString();
    }
}
