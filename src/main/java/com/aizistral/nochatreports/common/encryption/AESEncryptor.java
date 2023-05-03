package com.aizistral.nochatreports.common.encryption;

import static javax.crypto.Cipher.DECRYPT_MODE;
import static javax.crypto.Cipher.ENCRYPT_MODE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.AEADBadTagException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.aizistral.nochatreports.common.compression.Compression;
import com.aizistral.nochatreports.common.config.NCRConfigEncryption;
import net.minecraft.util.Tuple;

public abstract class AESEncryptor<T extends AESEncryption> extends Encryptor<T> {
	private final T encryption;
	private final SecretKey key;
	private final Cipher encryptor, decryptor;
	private final boolean useIV;
	private String decryptLastUsedEncapsulation = null;
	private Compression decryptLastUsedCompression = null;
	private Float decryptLastUsedCompressionRatio = null;


	protected AESEncryptor(String key, T encryption) throws InvalidKeyException {
		this(new SecretKeySpec(decodeBinaryKey(key), "AES"), encryption);
	}

	protected AESEncryptor(SecretKey key, T encryption) throws InvalidKeyException {
		this.encryption = encryption;
		this.useIV = encryption.requiresIV();
		String mode = encryption.getMode();
		String padding = encryption.getPadding();

		try {
			this.key = key;

			Cipher encryptor = Cipher.getInstance(this.key.getAlgorithm() + "/" + mode + "/" + padding);
			if (this.useIV) {
				encryptor.init(ENCRYPT_MODE, this.key, this.generateIV().getA());
			} else {
				encryptor.init(ENCRYPT_MODE, this.key);

			}
			this.encryptor = encryptor;

			Cipher decryptor = Cipher.getInstance(this.key.getAlgorithm() + "/" + mode + "/" + padding);
			if (this.useIV) {
				decryptor.init(DECRYPT_MODE, this.key, this.generateIV().getA());
			} else {
				decryptor.init(DECRYPT_MODE, this.key);
			}
			this.decryptor = decryptor;
		} catch (NoSuchAlgorithmException | NoSuchPaddingException ex) {
			throw new RuntimeException(ex);
		} catch (InvalidAlgorithmParameterException ex) {
			throw new InvalidKeyException(ex);
		}
	}

	public String encryptAndCompress(String plaintextPrefix, String secretMessage, NCRConfigEncryption.CompressionPolicy policy, Compression specificCompression) {
		if(!secretMessage.startsWith("#%")) secretMessage = "#%" + secretMessage;
		try {
			if(policy == NCRConfigEncryption.CompressionPolicy.Never) {
				return plaintextPrefix + encrypt(secretMessage);
			} else if (policy == NCRConfigEncryption.CompressionPolicy.Always) {
				if (secretMessage.startsWith("#%")) secretMessage = secretMessage.substring(2);
				ByteArrayOutputStream compressed = new ByteArrayOutputStream();
				compressed.write("#?".getBytes());
				if (specificCompression == null)
					compressed.write(Compression.compressWithBest(toBytes(secretMessage)));
				else
					compressed.write(specificCompression.compress(toBytes(secretMessage)));
				return plaintextPrefix + encapsulate(internalRawEncrypt(compressed.toByteArray()));
			}else if(policy == NCRConfigEncryption.CompressionPolicy.Preferred) {
				String compSecretMessage = secretMessage;
				if (compSecretMessage.startsWith("#%")) compSecretMessage = compSecretMessage.substring(2);
				ByteArrayOutputStream compressed = new ByteArrayOutputStream();
				compressed.write("#?".getBytes());
				if (specificCompression == null)
					compressed.write(Compression.compressWithBest(toBytes(compSecretMessage)));
				else
					compressed.write(specificCompression.compress(toBytes(compSecretMessage)));
				String finalCompressedText = plaintextPrefix + encapsulate(internalRawEncrypt(compressed.toByteArray()));
				String finalNormalText = plaintextPrefix + encapsulate(internalRawEncrypt(toBytes(secretMessage)));
				if(finalNormalText.length() <= finalCompressedText.length())
					return finalNormalText;
				else
					return finalCompressedText;
			} else if(policy == NCRConfigEncryption.CompressionPolicy.WhenNecessary) {
				String finalNonCompressedText = plaintextPrefix + encrypt(secretMessage);
				// Switch to "Preferred" if message wouldn't fit in single packet. Otherwise do not compress.
				if (finalNonCompressedText.length() >= 256) {
					return encryptAndCompress(plaintextPrefix, secretMessage, NCRConfigEncryption.CompressionPolicy.Preferred, specificCompression);
				}else {
					return finalNonCompressedText;
				}
			}else {
				throw new RuntimeException("Unsupported Compression Policy: " + policy);
			}
		}catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	private byte[] internalRawEncrypt(byte[] message) {
		try {
			if (this.useIV) {
				var tuple = this.generateIV();

				this.encryptor.init(ENCRYPT_MODE, this.key, tuple.getA());
				byte[] encrypted = this.encryptor.doFinal(message);
				return ByteBuffer.allocate(encrypted.length + tuple.getB().length).put(tuple.getB())
						.put(encrypted).array();
			} else {
				return this.encryptor.doFinal(message);
			}
		} catch (IllegalBlockSizeException | BadPaddingException | InvalidKeyException | InvalidAlgorithmParameterException ex) {
			throw new RuntimeException(ex);
		}
	}

	public String encapsulate(byte[] data) {
		if (this.encryption.getEncapsulation().equalsIgnoreCase("Base64")) {
			return encodeBase64(data);
		} else if (this.encryption.getEncapsulation().equalsIgnoreCase("Base64R")) {
			return encodeBase64R(data);
		} else if (this.encryption.getEncapsulation().equalsIgnoreCase("Sus16")) {
			return encodeSus16(data);
		} else if (this.encryption.getEncapsulation().equalsIgnoreCase("MC256")) {
			return encodeMC256(data);
		} else if (this.encryption.getEncapsulation().equalsIgnoreCase("Invis2")) {
			return encodeInvis2(data);
		} else {
			throw new RuntimeException("Unknown Encapsulation: " + this.encryption.getEncapsulation());
		}
	}
	@Override
	public String encrypt(String message) {
		return encapsulate(internalRawEncrypt(toBytes(message)));
	}

	private boolean isPlaintextOrCompressed(byte[] message) {
		return message != null && message.length >= 2 &&  message[0] == (byte) '#' &&
				(message[1] == (byte) '%' || message[1] == (byte) '?');
	}

	private boolean isCompressed(byte[] message) {
		return message != null && message.length >= 2 && message[0] == (byte) '#' && message[1] == (byte) '?';
	}

	@Override
	public String decrypt(String message) {
		decryptLastUsedEncapsulation = null;
		decryptLastUsedCompression = null;
		decryptLastUsedCompressionRatio = null;
		byte[] candidate = null;
		RuntimeException firstEx = null;
		// Attempt Base64R first
		try {
			candidate = internalRawDecrypt(decodeBase64RBytes(message));
			decryptLastUsedEncapsulation = "Base64R";
		} catch (RuntimeException ex) {
			if(firstEx == null) firstEx = ex;
		}
		// If failed, attempt Base64 (old version)
		if (!isPlaintextOrCompressed(candidate)) {
			try {
				candidate = internalRawDecrypt(decodeBase64NonRBytes(message));
				decryptLastUsedEncapsulation = "Base64";
			} catch (RuntimeException ex) {
				if(firstEx == null) firstEx = ex;
			}
		}
		// If also failed, attempt Sus16
		if (!isPlaintextOrCompressed(candidate)) {
			try {
				candidate = internalRawDecrypt(decodeSus16Bytes(message));
				decryptLastUsedEncapsulation = "Sus16";
			} catch (RuntimeException ex) {
				if(firstEx == null) firstEx = ex;
			}
		}
		// next MC256
		if (!isPlaintextOrCompressed(candidate)) {
			try {
				candidate = internalRawDecrypt(decodeMC256(message));
				decryptLastUsedEncapsulation = "MC256";
			} catch (RuntimeException ex) {
				if(firstEx == null) firstEx = ex;
			}
		}
		// next Invis2
		if (!isPlaintextOrCompressed(candidate)) {
			try {
				candidate = internalRawDecrypt(decodeInvis2(message));
				decryptLastUsedEncapsulation = "Invis2";
			} catch (RuntimeException ex) {
				if(firstEx == null) firstEx = ex;
			}
		}
		if(candidate == null && firstEx != null) {
			throw firstEx;
		}

		// Decompress
		if(isCompressed(candidate)) {
			byte[] compressedData = new byte[candidate.length - 2];
			for(int i = 2; i < candidate.length; i++)
				compressedData[i-2] = candidate[i];

			try {
				Compression compression = Compression.findCompression(compressedData);
				if (compression == null) {
					return "#%<UNKNOWN COMPRESSION>";
				}
				byte[] decompressed = compression.decompress(compressedData);
				String decompressedString = fromBytes(decompressed);
				decryptLastUsedCompressionRatio = ((float) decompressed.length) / ((float) compressedData.length);
				decryptLastUsedCompression = compression;
				return "#%" + decompressedString;
			}catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		}else {
			return fromBytes(candidate);
		}
	}

	public byte[] internalRawDecrypt(byte[] message) {
		try {
			if (this.useIV) {
				var tuple = this.splitIV(message);

				this.decryptor.init(DECRYPT_MODE, this.key, tuple.getA());
				return this.decryptor.doFinal(tuple.getB());
			} else
				return this.decryptor.doFinal(message);
		} catch (AEADBadTagException ex) {
			return "???".getBytes();
		} catch (IllegalBlockSizeException | BadPaddingException | InvalidKeyException | InvalidAlgorithmParameterException ex) {
			throw new RuntimeException(ex);
		}
	}

	@Override
	public String getKey() {
		return encodeBinaryKey(this.key.getEncoded());
	}

	@Override
	public T getAlgorithm() {
		return this.encryption;
	}

	protected abstract Tuple<AlgorithmParameterSpec, byte[]> generateIV() throws UnsupportedOperationException;

	protected abstract Tuple<AlgorithmParameterSpec, byte[]> splitIV(byte[] message) throws UnsupportedOperationException;

	public String getDecryptLastUsedEncapsulation() {
		return decryptLastUsedEncapsulation;
	}

	public Compression getDecryptLastUsedCompression() {
		return decryptLastUsedCompression;
	}

	public Float getDecryptLastUsedCompressionRatio() {
		return decryptLastUsedCompressionRatio;
	}

}
