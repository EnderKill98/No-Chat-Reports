package com.aizistral.nochatreports.common.encryption;

import java.security.InvalidKeyException;

public class AESCFB8Encryption extends AESEncryption {

	protected AESCFB8Encryption(String encapsulation) {
		super("CFB8", "NoPadding", true, encapsulation);
	}

	@Override
	public AESCFB8Encryptor getProcessor(String key) throws InvalidKeyException {
		if (this.getEncapsulation().equalsIgnoreCase("Base64")) {
			return new AESCFB8Encryptor(key, Encryption.AES_CFB8_BASE64);
		} else if (this.getEncapsulation().equalsIgnoreCase("Base64R")) {
			return new AESCFB8Encryptor(key, Encryption.AES_CFB8_BASE64R);
		} else if (this.getEncapsulation().equalsIgnoreCase("Sus16")) {
			return new AESCFB8Encryptor(key, Encryption.AES_CFB8_SUS16);
		} else if (this.getEncapsulation().equalsIgnoreCase("MC256")) {
			return new AESCFB8Encryptor(key, Encryption.AES_CFB8_MC256);
		} else {
			throw new RuntimeException("Unknown Encapsulation: " + this.getEncapsulation());
		}
	}

	@Override
	public AESCFB8Encryptor getRandomProcessor() {
		try {
			return this.getProcessor(this.getRandomKey());
		} catch (InvalidKeyException ex) {
			throw new RuntimeException(ex);
		}
	}

}
