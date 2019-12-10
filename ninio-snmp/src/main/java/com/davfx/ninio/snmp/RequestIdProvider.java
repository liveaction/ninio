package com.davfx.ninio.snmp;

import java.security.SecureRandom;
import java.util.Random;

public final class RequestIdProvider {

    private static final Random RANDOM = new SecureRandom();

    private static final int MIN_ID = 1_000;
    private static final int MAX_ID = 2_043_088_696; // Let's do as snmpwalk is doing
    public static final int IGNORE_ID = MAX_ID;

    private static int NEXT = MAX_ID;

    private static final Object LOCK = new Object();

    public RequestIdProvider() {
    }

    public int get() {
        synchronized (LOCK) {
            if (NEXT == MAX_ID) {
                NEXT = MIN_ID + RANDOM.nextInt(MAX_ID - MIN_ID);
            }
            int k = NEXT;
            NEXT++;
            return k;
        }
    }
}
