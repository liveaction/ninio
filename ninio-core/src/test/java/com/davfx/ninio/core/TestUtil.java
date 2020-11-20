package com.davfx.ninio.core;

import java.io.IOException;
import java.net.ServerSocket;

public final class TestUtil {

    public static int findAvailablePort() {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Couldn't find an available port!!");
        }
    }
}
