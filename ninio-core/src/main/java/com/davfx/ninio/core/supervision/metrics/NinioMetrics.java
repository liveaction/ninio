/*
 * NinioMetrics.java
 * Created on May 07, 2026, 4:47 PM
 *
 * Copyright 2026 BlueCat Networks (USA) Inc. and its affiliates and licensors. All Rights Reserved.
 *
 * BlueCat Networks and its licensors hereby assert and retain all rights, title, and interest in and to the code,
 * including any and all modifications, enhancements, or derivative works thereof (collectively, the "Code"). This
 * includes, but is not limited to, all intellectual property rights, whether registered or unregistered, associated
 * with the Code. The Code contains trade secrets and proprietary and confidential information of BlueCat Networks
 * and its licensors. It is protected under applicable worldwide copyright and trade secret laws. No rights, title,
 * or interest in the Code are transferred to any third party without the explicit written consent of BlueCat
 * Networks. Any unauthorized use, reproduction, or distribution of the Code is strictly prohibited and may result in
 * legal action.
 */
package com.davfx.ninio.core.supervision.metrics;

import com.davfx.ninio.core.supervision.metrics.pmt.PmtMetrics;
import com.davfx.ninio.core.supervision.metrics.pmt.PmtMetricsImpl;
import io.prometheus.metrics.core.metrics.Counter;

/**
 *
 *
 * @author Baptiste Le Bail
 */
public class NinioMetrics {

    public static final String OUT = "out";
    public static final String IN = "in";
    public static final String UDP = "udp";
    public static final String RAW = "raw";
    public static final String TCP_DUMP = "tcp_dump";
    public static final String PROXY_CLIENT = "proxy_client";
    public static final String PING = "ping";
    public static final String AUTH_V2_OUT = "auth_v2_out";
    public static final String AUTH_V3_OUT = "auth_v3_out";
    public static final String DISCOVER_V3_OUT = "discover_v3_out";

    private static final NinioMetrics INSTANCE = new NinioMetrics();

    private final PmtMetrics metrics;

    private final Counter udpIn;
    private final Counter udpOut;
    private final Counter rawIn;
    private final Counter rawOut;
    private final Counter tcpDumpIn;
    private final Counter tcpDumpOut;
    private final Counter proxyClientIn;
    private final Counter proxyClientOut;
    private final Counter pingIn;
    private final Counter pingOut;
    private final Counter authV2Out;
    private final Counter authV3Out;
    private final Counter discoverV3Out;

    public NinioMetrics() {
        this.metrics = PmtMetricsImpl.get();
        udpIn = metrics.counter(UDP + "_" + IN, "UDP packets in");
        udpOut = metrics.counter(UDP + "_" + OUT, "UDP packets out");
        rawIn = metrics.counter(RAW + "_" + IN, "RAW packets in");
        rawOut = metrics.counter(RAW + "_" + OUT, "RAW packets out");
        tcpDumpIn = metrics.counter(TCP_DUMP + "_" + IN, "TCPDUMP packets in");
        tcpDumpOut = metrics.counter(TCP_DUMP + "_" + OUT, "TCPDUMP packets out");
        proxyClientIn = metrics.counter(PROXY_CLIENT + "_" + IN, "Proxy client packets in");
        proxyClientOut = metrics.counter(PROXY_CLIENT + "_" + OUT, "Proxy client packets out");
        pingIn = metrics.counter(PING + "_" + IN, "Ping requests in");
        pingOut = metrics.counter(PING + "_" + OUT, "Ping requests out");
        authV2Out = metrics.counter(AUTH_V2_OUT, "SNMP auth v2 outgoing requests");
        authV3Out = metrics.counter(AUTH_V3_OUT, "SNMP auth v3 outgoing requests");
        discoverV3Out = metrics.counter(DISCOVER_V3_OUT, "SNMP auth v3 discover outgoing requests");
    }

    public static NinioMetrics get() {
        return INSTANCE;
    }

    public Counter udpIn() {
        return udpIn;
    }

    public Counter udpOut() {
        return udpOut;
    }

    public Counter rawIn() {
        return rawIn;
    }

    public Counter rawOut() {
        return rawOut;
    }

    public Counter tcpDumpIn() {
        return tcpDumpIn;
    }

    public Counter tcpDumpOut() {
        return tcpDumpOut;
    }

    public Counter proxyClientIn() {
        return proxyClientIn;
    }

    public Counter proxyClientOut() {
        return proxyClientOut;
    }

    public Counter pingIn() {
        return pingIn;
    }

    public Counter pingOut() {
        return pingOut;
    }

    public Counter authV2Out() {
        return authV2Out;
    }

    public Counter authV3Out() {
        return authV3Out;
    }

    public Counter discoverV3Out() {
        return discoverV3Out;
    }

    public Counter inMemoryCache(String name, String io) {
        return metrics.counter(name + "_cache_" + io, "In memory cache");
    }
}