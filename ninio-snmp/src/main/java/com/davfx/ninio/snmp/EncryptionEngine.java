package com.davfx.ninio.snmp;

import com.davfx.ninio.snmp.encryption.AuthProtocol;
import com.davfx.ninio.snmp.encryption.PrivacyProtocol;
import com.davfx.ninio.util.MemoryCache;
import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * A lot is copied from snmp4j
 */
final class EncryptionEngine {
	private static final Logger LOGGER = LoggerFactory.getLogger(EncryptionEngine.class);

	private static final int ENCRYPTION_MARGIN = 64;
	private final SecureRandom random = new SecureRandom();
	private final MessageDigest messageDigest;
	private final Cipher cipher;
	private final int privKeyLength;
	private final MemoryCache<String, byte[]> cache;
	private final String privEncryptionAlgorithm;
	private final AuthProtocol authDigestAlgorithm;
	private final PrivacyProtocol privacyProtocol;

	public EncryptionEngine(@Nullable AuthProtocol authProtocol, @Nullable PrivacyProtocol privacyProtocol, double cacheDuration) {
		this.authDigestAlgorithm = authProtocol;
		this.privacyProtocol = privacyProtocol;
		if (authProtocol == null) {
			messageDigest = null;
		} else {
			try {
				messageDigest = MessageDigest.getInstance(authProtocol.protocolName());
			} catch (NoSuchAlgorithmException e) {
				throw new RuntimeException(e);
			}
		}

		if (privacyProtocol == null) {
			this.privEncryptionAlgorithm = null;
			cipher = null;
			privKeyLength = 0;
		} else {
			this.privEncryptionAlgorithm = privacyProtocol.protocolClass();
			LOGGER.trace("Creating encryption engine");
			try {
				cipher = Cipher.getInstance(privacyProtocol.protocolId());
			} catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
				throw new RuntimeException(e);
			}
			LOGGER.trace("Encryption engine created");
			privKeyLength = privacyProtocol.keyLength();
		}

		cache = MemoryCache.<String, byte[]> builder().expireAfterAccess(cacheDuration).build();
	}

	public AuthProtocol authDigestAlgorithm() {
		return authDigestAlgorithm;
	}

	public byte[] regenerateKey(byte[] id, String password, boolean privateKey) {
		if (messageDigest == null) {
			return null;
		}

		if (password == null) {
			return null;
		}

		if (id == null) {
			return null; // id = new byte[] {};
		}

		String k = BaseEncoding.base64().encode(id) + " " + password; // Space is a safe separator (not in the regular Base64 characters)
		byte[] key = cache.get(k);
		if (key == null) {
			LOGGER.trace("Regenerating key");
			byte[] passwordBytes = password.getBytes(Charsets.UTF_8);

			int count = 0;
			int s = 1024 * 1024; // 1 MiB to be done
			while (count < s) {
				int l = passwordBytes.length;
				if ((count + l) > s) {
					l = s - count;
				}
				messageDigest.update(passwordBytes, 0, l);
				count += l;
			}

			byte[] digest = messageDigest.digest();

			messageDigest.reset();
			messageDigest.update(digest);
			messageDigest.update(id);
			messageDigest.update(digest);
			key = messageDigest.digest();

			if (privateKey && privacyProtocol != null) {
				if (key.length >= privacyProtocol.minKeyLength()) {
					if (key.length > privacyProtocol.maxKeyLength()) {
						// truncate key
						byte[] truncatedKey = new byte[privacyProtocol.maxKeyLength()];
						System.arraycopy(key, 0, truncatedKey, 0, privacyProtocol.maxKeyLength());
						key = truncatedKey;
					}
				} else {
					// extend key if necessary
					key = extendShortKey(key);
				}
			}

			cache.put(k, key);
			LOGGER.trace("Key regenerated");
		}
		return key;
	}

	public byte[] extendShortKey(byte[] shortKey) {
		// we have to extend the key, currently only the AES draft
		// defines this algorithm, so this may have to be changed for other
		// privacy protocols
		byte[] extKey = new byte[privacyProtocol.minKeyLength()];
		int length = shortKey.length;
		System.arraycopy(shortKey, 0, extKey, 0, length);

		while (length < extKey.length) {
			byte[] hash = hash(extKey, 0, length);

			if (hash == null) {
				return null;
			}
			int bytesToCopy = extKey.length - length;
			if (bytesToCopy > authDigestAlgorithm.digestLength()) {
				bytesToCopy = authDigestAlgorithm.digestLength();
			}
			System.arraycopy(hash, 0, extKey, length, bytesToCopy);
			length += bytesToCopy;
		}
		return extKey;
	}

	public byte[] hash(byte[] data, int offset, int length) {
		messageDigest.reset();
		messageDigest.update(data, offset, length);
		return messageDigest.digest();
	}

	public byte[] hash(byte[] authKey, ByteBuffer message) {
		if (messageDigest == null) {
			return null;
		}

		ByteBuffer messageDup = message.duplicate();

		byte[] newDigest;
		byte[] k_ipad = new byte[64]; /* inner padding - key XORd with ipad */
		byte[] k_opad = new byte[64]; /* outer padding - key XORd with opad */

		/*
		 * the HMAC_MD transform looks like:
		 *
		 * MD(K XOR opad, MD(K XOR ipad, msg))
		 *
		 * where K is an n byte key ipad is the byte 0x36 repeated 64 times opad
		 * is the byte 0x5c repeated 64 times and text is the data being
		 * protected
		 */
		/* start out by storing key, ipad and opad in pads */
		for (int i = 0; i < authKey.length; ++i) {
			k_ipad[i] = (byte) (authKey[i] ^ 0x36);
			k_opad[i] = (byte) (authKey[i] ^ 0x5c);
		}
		for (int i = authKey.length; i < 64; ++i) {
			k_ipad[i] = 0x36;
			k_opad[i] = 0x5c;
		}

		/* perform inner MD */
		messageDigest.reset();
		messageDigest.update(k_ipad); /* start with inner pad */
		messageDigest.update(messageDup); /* then text of msg */
		newDigest = messageDigest.digest(); /* finish up 1st pass */
		/* perform outer MD */
		messageDigest.reset(); /* init md5 for 2nd pass */
		messageDigest.update(k_opad); /* start with outer pad */
		messageDigest.update(newDigest); /* then results of 1st hash */
		newDigest = messageDigest.digest(); /* finish up 2nd pass */

		// copy the digest into the message (12 bytes only!)
		byte[] k = new byte[authDigestAlgorithm.authCodeLength()];
		System.arraycopy(newDigest, 0, k, 0, k.length);
		return k;
	}

	public ByteBuffer encrypt(int bootCount, int time, byte[] encryptionParameters, byte[] privKey, ByteBuffer decryptedBuffer) {
		if (cipher == null) {
			return null;
		}

		int salt = random.nextInt();
		byte[] iv;

		if (privEncryptionAlgorithm.equals("AES")) {
			iv = new byte[16];
			ByteBuffer ivb = ByteBuffer.wrap(iv);
			ivb.putInt(bootCount);
			ivb.putInt(time);
			ivb.putInt(0);
			ivb.putInt(salt);

			ByteBuffer bb = ByteBuffer.wrap(encryptionParameters);
			bb.putInt(0);
			bb.putInt(salt);
		} else {
			ByteBuffer bb = ByteBuffer.wrap(encryptionParameters);
			bb.putInt(bootCount);
			bb.putInt(salt);

			iv = new byte[8];
			for (int i = 0; i < iv.length; i++) {
				iv[i] = (byte) (privKey[iv.length + i] ^ encryptionParameters[i]);
			}
		}
		try {
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(privKey, 0, privKeyLength, privEncryptionAlgorithm), new IvParameterSpec(iv));
			ByteBuffer b = ByteBuffer.allocate(decryptedBuffer.remaining() + ENCRYPTION_MARGIN);
			cipher.doFinal(decryptedBuffer, b);
			b.flip();
			return b;
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	public ByteBuffer decrypt(int bootCount, int time, byte[] encryptionParameters, byte[] privKey, ByteBuffer encryptedBuffer) {
		if (cipher == null) {
			return null;
		}

		byte[] iv;

		if (privEncryptionAlgorithm.equals("AES")) {
			iv = new byte[16];
			ByteBuffer ivb = ByteBuffer.wrap(iv);
			ivb.putInt(bootCount);
			ivb.putInt(time);

			ByteBuffer bb = ByteBuffer.wrap(encryptionParameters);
			ivb.putInt(bb.getInt());
			ivb.putInt(bb.getInt());
		} else {
			iv = new byte[8];
			for (int i = 0; i < 8; ++i) {
				iv[i] = (byte) (privKey[8 + i] ^ encryptionParameters[i]);
			}
		}

		try {
			SecretKeySpec key = new SecretKeySpec(privKey, 0, privKeyLength, privEncryptionAlgorithm);
			IvParameterSpec ivSpec = new IvParameterSpec(iv);
			cipher.init(Cipher.DECRYPT_MODE, key, ivSpec);
			ByteBuffer b = ByteBuffer.allocate(encryptedBuffer.remaining() + ENCRYPTION_MARGIN);
			cipher.doFinal(encryptedBuffer, b);
			b.flip();
			return b;
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

}
