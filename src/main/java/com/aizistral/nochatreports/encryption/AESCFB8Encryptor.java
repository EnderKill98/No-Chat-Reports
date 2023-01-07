package com.aizistral.nochatreports.encryption;

import net.minecraft.util.Tuple;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Random;

public class AESCFB8Encryptor extends AESEncryptor<AESCFB8Encryption> {

	protected AESCFB8Encryptor(String key, AESCFB8Encryption encryption) throws InvalidKeyException {
		super(key, encryption);
	}

	protected AESCFB8Encryptor(SecretKey key, String mode, String padding, boolean iv, AESCFB8Encryption encryption) throws InvalidKeyException {
		super(key, encryption);
	}

	@Override
	protected Tuple<AlgorithmParameterSpec, byte[]> generateIV() throws UnsupportedOperationException {
		long nonce = RANDOM.nextLong();
		byte[] iv = new byte[16];
		new Random(nonce).nextBytes(iv);
		return new Tuple<>(new IvParameterSpec(iv), ByteBuffer.allocate(8).putLong(nonce).array());
	}

	@Override
	protected Tuple<AlgorithmParameterSpec, byte[]> splitIV(byte[] message) throws UnsupportedOperationException {
		ByteBuffer buffer = ByteBuffer.wrap(message);
		int size = buffer.capacity();
		long nonce = buffer.getLong();
		byte[] encrypted = new byte[size - 8];
		buffer.get(encrypted);
		byte[] iv = new byte[16];
		new Random(nonce).nextBytes(iv);
		return new Tuple<>(new IvParameterSpec(iv), encrypted);
	}

}
