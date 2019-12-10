package com.davfx.ninio.snmp;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Connecter;
import com.davfx.ninio.core.SendCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

public final class AuthRemoteEnginePendingRequestManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthRemoteEnginePendingRequestManager.class);

    public final AuthRemoteEngine engine;

    public final List<PendingRequest> pendingRequests = new LinkedList<>();

    public AuthRemoteEnginePendingRequestManager(Auth auth, EncryptionEngine encryptionEngine) {
        engine = new AuthRemoteEngine(auth, encryptionEngine);
    }

    public boolean isReady() {
        return engine.isValid();
    }

    public void reset() {
        engine.reset();
    }

    public void discoverIfNecessary(Address address, Connecter connector) {
        if (!engine.isValid()) {
            Version3PacketBuilder builder = Version3PacketBuilder.get(engine, null, RequestIdProvider.IGNORE_ID, null);
            ByteBuffer b = builder.getBuffer();
            LOGGER.trace("Writing discover GET v3: #{}, packet size = {}", RequestIdProvider.IGNORE_ID, b.remaining());
            connector.send(address, b, new SendCallback() {
                @Override
                public void sent() {
                }

                @Override
                public void failed(IOException ioe) {
                    IOException e = new IOException("Failed to send discover packet", ioe);
                    for (AuthRemoteEnginePendingRequestManager.PendingRequest r : pendingRequests) {
                        r.sendCallback.failed(e);
                    }
                    pendingRequests.clear();
                }
            });
        }
    }

    public void registerPendingRequest(AuthRemoteEnginePendingRequestManager.PendingRequest r) {
        pendingRequests.add(r);
    }

    public void clearPendingRequests() {
        pendingRequests.clear();
    }

    public void sendPendingRequestsIfReady(Address address, Connecter connector) {
        if (!engine.isValid()) {
            return;
        }

        for (AuthRemoteEnginePendingRequestManager.PendingRequest r : pendingRequests) {
            switch (r.request) {
                case GET: {
                    Version3PacketBuilder builder = Version3PacketBuilder.get(engine, r.contextName, r.instanceId, r.oid);
                    ByteBuffer b = builder.getBuffer();
                    LOGGER.trace("Writing GET v3: {} #{}, packet size = {}", r.oid, r.instanceId, b.remaining());
                    connector.send(address, b, r.sendCallback);
                    break;
                }
                case GETNEXT: {
                    Version3PacketBuilder builder = Version3PacketBuilder.getNext(engine, r.contextName, r.instanceId, r.oid);
                    ByteBuffer b = builder.getBuffer();
                    LOGGER.trace("Writing GETNEXT v3: {} #{}, packet size = {}", r.oid, r.instanceId, b.remaining());
                    connector.send(address, b, r.sendCallback);
                    break;
                }
                case GETBULK: {
                    Version3PacketBuilder builder = Version3PacketBuilder.getBulk(engine, r.contextName, r.instanceId, r.oid, SnmpClient.BULK_SIZE);
                    ByteBuffer b = builder.getBuffer();
                    LOGGER.trace("Writing GETBULK v3: {} #{}, packet size = {}", r.oid, r.instanceId, b.remaining());
                    connector.send(address, b, r.sendCallback);
                    break;
                }
                case TRAP: {
                    LOGGER.error("No TRAP possible in v3: {} #{}", r.oid, r.instanceId);
                    break;
                }
                default:
                    break;
            }
        }
        pendingRequests.clear();
    }

    public static final class PendingRequest {
        public final SnmpCallType request;
        public final int instanceId;
        public final Oid oid;
        public final String contextName;
        public final SendCallback sendCallback;

        public PendingRequest(SnmpCallType request, int instanceId, Oid oid, String contextName, /*Iterable<SnmpResult> trap, */SendCallback sendCallback) {
            this.request = request;
            this.instanceId = instanceId;
            this.oid = oid;
            this.contextName = contextName;
            this.sendCallback = sendCallback;
        }
    }
}
