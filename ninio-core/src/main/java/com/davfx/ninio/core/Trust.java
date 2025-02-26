package com.davfx.ninio.core;

import com.davfx.ninio.core.dependencies.Dependencies;
import com.davfx.ninio.util.ConfigUtils;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

public final class Trust {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(Trust.class);
	
	private static final Config CONFIG = ConfigUtils.load(new Dependencies()).getConfig(Trust.class.getPackage().getName());
	private static final boolean INSECURE = CONFIG.getBoolean("insecure");
	private static final String TLS_VERSION = CONFIG.getString("tls");

	private final KeyStore ksKeys;
	private final SSLContext sslContext;

	public Trust(File keysTrust, String keysTrustPassPhrase) {
		this(keysTrust, keysTrustPassPhrase, keysTrust, keysTrustPassPhrase);
	}
	
	public Trust(File keys, String keysPassPhrase, File trust, String trustPassPhrase) {
		try {
			ksKeys = KeyStore.getInstance("JKS");
			try (InputStream in = new FileInputStream(keys)) {
				ksKeys.load(in, keysPassPhrase.toCharArray());
			}
			Enumeration<String> e = ksKeys.aliases();
			while (e.hasMoreElements()) {
				String alias = e.nextElement();
				LOGGER.trace("Alias in key store: {}", alias);
			}
			KeyStore ksTrust = KeyStore.getInstance("JKS");
			try (InputStream in = new FileInputStream(trust)) {
				ksTrust.load(in, trustPassPhrase.toCharArray());
			}

			KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			kmf.init(ksKeys, keysPassPhrase.toCharArray());

			TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init(ksTrust);

			sslContext = SSLContext.getInstance(TLS_VERSION);
			sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
			SSLEngine sslEngine = sslContext.createSSLEngine();
			sslEngine.setNeedClientAuth(true);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public Trust(String keysResourceName, String keysTrustPassPhrase) {
		this(keysResourceName, keysTrustPassPhrase, keysResourceName, keysTrustPassPhrase);
	}
	
	public Trust(String keysResourceName, String keysPassPhrase, String trustResourceName, String trustPassPhrase) {
		LOGGER.info("Initialize secured trust store");
		try {
			ksKeys = KeyStore.getInstance("JKS");
			try (InputStream in = Files.newInputStream(Paths.get(keysResourceName))) {
				ksKeys.load(in, keysPassPhrase.toCharArray());
			}
			Enumeration<String> e = ksKeys.aliases();
			while (e.hasMoreElements()) {
				String alias = e.nextElement();
				LOGGER.trace("Alias in key store: {}", alias);
			}
			KeyStore ksTrust = KeyStore.getInstance("JKS");
			try (InputStream in = Files.newInputStream(Paths.get(trustResourceName))) {
				ksTrust.load(in, trustPassPhrase.toCharArray());
			}

			KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			kmf.init(ksKeys, keysPassPhrase.toCharArray());

			TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init(ksTrust);

			sslContext = SSLContext.getInstance(TLS_VERSION);
			sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public Trust() {
		ksKeys = null;
		try {
			/*%%
			KeyStore ksTrust = KeyStore.getInstance("JKS");

			TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
			tmf.init(ksTrust);

			TrustManager defaultTrustManager = new SavingTrustManager((X509TrustManager) tmf.getTrustManagers()[0]);
			
			TrustManager[] t = new TrustManager[] { defaultTrustManager }
			*/
			
			// WARNING! Not secure to a man-in-the-middle attack
			TrustManager[] t = INSECURE ? new TrustManager[] {
				new X509TrustManager() {
					@Override
					public X509Certificate[] getAcceptedIssuers() {
						return null;
					}
					@Override
					public void checkClientTrusted(X509Certificate[] certs, String authType) {
					}
					@Override
					public void checkServerTrusted(X509Certificate[] certs, String authType) {
					}
				}
			} : null;
			
			sslContext = SSLContext.getInstance(TLS_VERSION);
			sslContext.init(null, t, null);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	/*%%
	private static class SavingTrustManager implements X509TrustManager {

		private final X509TrustManager tm;
		private X509Certificate[] chain;

		SavingTrustManager(X509TrustManager tm) {
			this.tm = tm;
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			this.chain = chain;
			tm.checkServerTrusted(chain, authType);
		}
	}
	*/
	
	public PrivateKey getPrivateKey(String alias, String password) {
		try {
			return (PrivateKey) ksKeys.getKey(alias, password.toCharArray());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	public PublicKey getPublicKey(String alias) {
		try {
			return ksKeys.getCertificate(alias).getPublicKey();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public SSLEngine createEngine(boolean clientMode) {
		SSLEngine engine = sslContext.createSSLEngine();
		engine.setUseClientMode(clientMode);
		return engine;
	}
}
