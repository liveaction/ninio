package com.davfx.ninio.proxy;

import java.io.IOException;
import java.net.ServerSocket;

public final class TestUtil {
    public static final String DEFAULT_RECIPIENT_ID = "1";

    public static int findAvailablePort() {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Couldn't find an available port!!");
        }
    }
}
