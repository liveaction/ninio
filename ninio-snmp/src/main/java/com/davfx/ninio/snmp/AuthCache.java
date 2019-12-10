package com.davfx.ninio.snmp;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.snmp.dependencies.Dependencies;
import com.davfx.ninio.util.ConfigUtils;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class AuthCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthCache.class);

    private static final Config CONFIG = ConfigUtils.load(new Dependencies()).getConfig(SnmpClient.class.getPackage().getName());

    static final long AUTH_ENGINES_CACHE_DURATION = Double.valueOf(ConfigUtils.getDuration(CONFIG, "auth.cache")).longValue();

    private static AuthCache INSTANCE;

    private final ScheduledExecutorService scheduler;

    final Cache<Address, Auth> auths =
            CacheBuilder.newBuilder()
                    .expireAfterAccess(AUTH_ENGINES_CACHE_DURATION, TimeUnit.SECONDS)
                    .build();

    final Cache<AuthRemoteEngineKey, AuthRemoteEnginePendingRequestManager> authRemoteEngines =
            CacheBuilder.newBuilder()
                    .expireAfterAccess(AUTH_ENGINES_CACHE_DURATION, TimeUnit.SECONDS)
                    .build();

    final Cache<EncryptionEngineKey, EncryptionEngine> encryptionEngines =
            CacheBuilder.newBuilder()
                    .expireAfterAccess(AUTH_ENGINES_CACHE_DURATION, TimeUnit.SECONDS)
                    .build();

    public AuthCache() {
        this.scheduler = Executors.newScheduledThreadPool(1);

        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                int readyCount = 0;
                int totalCount = 0;

                for (AuthRemoteEnginePendingRequestManager authRequestManager : authRemoteEngines.asMap().values()) {
                    if (authRequestManager.isReady()) {
                        readyCount++;
                    }

                    totalCount++;
                }

                LOGGER.info("SNMPv3 Auth Metrics : {}/{} are ready (approximated)", readyCount, totalCount);
            }
        }, 0, 10, TimeUnit.SECONDS);
    }

    static AuthCache get() {
        if (INSTANCE == null) {
            INSTANCE = new AuthCache();
        }

        return INSTANCE;
    }
}
