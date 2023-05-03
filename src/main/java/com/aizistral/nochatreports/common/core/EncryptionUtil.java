package com.aizistral.nochatreports.common.core;

import java.util.Optional;

import com.aizistral.nochatreports.common.compression.Compression;
import com.aizistral.nochatreports.common.config.NCRConfig;
import com.aizistral.nochatreports.common.encryption.AESEncryptor;
import com.aizistral.nochatreports.common.encryption.Encryptor;

import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;

public class EncryptionUtil {

	public record DetailedDecryptionInfo(Component decrypted, int keyIndex, @Nullable String encapsulation, @Nullable Compression compression, @Nullable Float compressionRatio) {
		public String getDecryptedText() {
			return decrypted.getString();
		}

		public @Nullable String getCompressionName() {
			if(compression == null) return null;
			return compression.getCompressionName();
		}
	}



	public static Optional<Component> tryDecrypt(Component component) {
		// Try out all encryptors
		int index = 0;
		for(Encryptor<?> encryption : NCRConfig.getEncryption().getAllEncryptors()) {
			Component copy = recreate(component);

			if(tryDecrypt(copy, encryption)) {
				return Optional.of(copy);
			}
			index++;
		}
		return Optional.empty();
	}

	public static Optional<DetailedDecryptionInfo> tryDecryptDetailed(String message) {
		return tryDecryptDetailed(Component.literal(message));
	}

	public static Optional<DetailedDecryptionInfo> tryDecryptDetailed(Component component) {
		// Try out all encryptors
		int index = 0;
		for(Encryptor<?> encryption : NCRConfig.getEncryption().getAllEncryptors()) {
			Component copy = recreate(component);

			if(tryDecrypt(copy, encryption)) {
				String encapsulation = null;
				Compression compression = null;
				Float compressionRatio = null;
				if(encryption instanceof AESEncryptor<?> aesEncryption) {
					encapsulation = aesEncryption.getDecryptLastUsedEncapsulation();
					compression = aesEncryption.getDecryptLastUsedCompression();
					compressionRatio = aesEncryption.getDecryptLastUsedCompressionRatio();
				}
				return Optional.of(new DetailedDecryptionInfo(copy, index, encapsulation, compression, compressionRatio));
			}
			index++;
		}
		return Optional.empty();
	}



	public static boolean tryDecrypt(Component component, Encryptor<?> encryptor) {
		boolean decryptedSiblings = false;
		for (Component sibling : component.getSiblings()) {
			if (tryDecrypt(sibling, encryptor)) {
				decryptedSiblings = true;
			}
		}

		if (component.getContents() instanceof LiteralContents literal) {
			var decrypted = tryDecrypt(literal.text(), encryptor);

			if (decrypted.isPresent()) {
				((MutableComponent)component).contents = new LiteralContents(decrypted.get());
				return true;
			}
		} else if (component.getContents() instanceof TranslatableContents translatable) {
			for (Object arg : translatable.args) {
				if (arg instanceof MutableComponent mutable) {
					if (tryDecrypt(mutable, encryptor)) {
						decryptedSiblings = true;
					}
				}
			}
		}

		return decryptedSiblings;
	}

	public static Optional<String> tryDecrypt(String message, Encryptor<?> encryptor) {
		try {
			// Invis2 uses space. Don't split on spaces if other char (\u200c) for invisi2 found.
			String[] splat = message.contains(" ") && !message.contains("\u200c") ? message.split(" ") : new String[] { message };
			String decryptable = splat[splat.length-1];

			String decrypted = encryptor.decrypt(decryptable);

			if (decrypted.startsWith("#%") || decrypted.startsWith("#?"))
				return Optional.of(message.substring(0, message.length() - decryptable.length()) + decrypted.substring(2, decrypted.length()));
			else
				return Optional.empty();
		} catch (Exception ex) {
			return Optional.empty();
		}
	}

	public static Component recreate(Component component) {
		return Component.Serializer.fromJson(Component.Serializer.toStableJson(component));
	}

}
