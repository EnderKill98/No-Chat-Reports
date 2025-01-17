package com.aizistral.nochatreports.common.config;

import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.aizistral.nochatreports.common.compression.Compression;
import com.aizistral.nochatreports.common.encryption.Encryption;
import com.aizistral.nochatreports.common.encryption.Encryptor;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.annotation.Nullable;

public class NCRConfigEncryption extends JSONConfig {
	protected static final String FILE_NAME = "NoChatReports/NCR-Encryption.json";
	protected boolean skipWarning = false, enableEncryption = false, encryptPublic = true,
			showEncryptionButton = true, showEncryptionIndicators = true;
	protected String encryptionKey = Encryption.AES_CFB8_BASE64R.getDefaultKey(), encryptionPassphrase = "",
			algorithmName = Encryption.AES_CFB8_BASE64R.getName();
	protected List<String> encryptableCommands = List.of("msg:1", "w:1", "whisper:1", "tell:1", "r:0", "dm:1",
			"me:0", "m:1", "t:1", "pm:1", "emsg:1", "epm:1", "etell:1", "ewhisper:1", "message:1", "reply:0");
	private transient Encryption algorithm;
	private transient boolean isValid = false;
	private transient String lastMessage = "???";
	protected List<String> commandPrefixes = List.of("/", ".");
	private int usedEncryptionKeyIndex = 0;
	private CompressionPolicy compressionPolicy = CompressionPolicy.WhenNecessary;
	private @Nullable String specificCompressionName = null; // Null = Find best
	private transient @Nullable Compression specificCompression;

	protected NCRConfigEncryption() {
		super(FILE_NAME);
	}

	@Override
	public NCRConfigEncryption getDefault() {
		return new NCRConfigEncryption();
	}

	@Override
	protected void uponLoad() {
		this.algorithm = Encryption.getRegistered().stream().filter(e -> e.getName().equals(this.algorithmName))
				.findFirst().orElse(Encryption.AES_CFB8_BASE64R);
		if(this.specificCompressionName == null)
			this.specificCompression = null;
		else
			this.specificCompression = Arrays.stream(Compression.getRegistered()).filter(c -> c.getCompressionName().equals(this.specificCompressionName)).findFirst().orElse(null);
		this.validate();
	}

	private void validate() {
		this.isValid = true;
		for(String key : this.encryptionKey.split(",")) {
			if(!this.algorithm.validateKey(key)) {
				this.isValid = false;
				return;
			}
		}
	}

	public void toggleEncryption() {
		this.enableEncryption = !this.enableEncryption;
		this.saveFile();
	}

	public void setAlgorithm(Encryption encryption) {
		this.algorithm = encryption;
		this.algorithmName = encryption.getName();
		this.validate();
		this.saveFile();
	}

	public void setEncryptionKey(String key) {
		this.encryptionKey = key;
		this.validate();
		this.saveFile();
	}

	public void setEncryptionPassphrase(String pass) {
		this.encryptionPassphrase = pass;
		this.saveFile();
	}

	public void setEncryptPublic(boolean encryptPublic) {
		this.encryptPublic = encryptPublic;
		this.saveFile();
	}

	public void disableWarning() {
		this.skipWarning = true;
	}

	public boolean isWarningDisabled() {
		return this.skipWarning;
	}

	public boolean isEnabled() {
		return this.enableEncryption;
	}

	public String getEncryptionKey() {
		return this.encryptionKey;
	}

	public String getEncryptionPassphrase() {
		return this.encryptionPassphrase;
	}

	public boolean isValid() {
		return this.isValid;
	}

	public boolean shouldEncryptPublic() {
		return this.encryptPublic;
	}

	public boolean showEncryptionIndicators() {
		return this.showEncryptionIndicators;
	}

	public boolean isEnabledAndValid() {
		return this.isEnabled() && this.isValid();
	}

	public Encryption getAlgorithm() {
		return this.algorithm;
	}

	public boolean shouldEncrypt(String message) {
		return this.isEnabledAndValid() && this.getEncryptionStartIndex(message) != -1;
	}

	public boolean showEncryptionButton() {
		return this.showEncryptionButton;
	}

	public int getEncryptionStartIndex(String message) {
		if (commandPrefixes.stream().noneMatch(message::startsWith))
			return this.encryptPublic ? 0 : -1;
		else {
			for (String rule : this.encryptableCommands) {
				String[] splat = rule.split(":");

				if (splat.length != 2)
					throw new IllegalArgumentException("Invalid encryptable command definition: " + rule
							+ ", in file: " + FILE_NAME);

				String cmd = splat[0];
				String args = splat[1];
				int argnum = 0;

				try {
					argnum = Integer.valueOf(args);
				} catch (NumberFormatException ex) {
					throw new IllegalArgumentException("Invalid encryptable command definition: " + rule
							+ ", in file: " + FILE_NAME);
				}

				String prefix = "/(" + cmd + "|.*:" + cmd + ") .*";
				if (message.matches(prefix)) {
					splat = message.split(" ", 2);
					char[] array = splat[1].toCharArray();

					for (int i = 0; i < array.length; i++) {
						char ch = array[i];
						if (argnum > 0) {
							if (ch == ' ') {
								argnum--;
							}
							continue;
						} else {
							int index = i + splat[0].length() + 1;
							return index < message.length() ? index : -1;
						}
					}
				} else {
					continue;
				}
			}

			return -1;
		}
	}

	public void setLastMessage(String lastMessage) {
		this.lastMessage = lastMessage;
	}

	public String getLastMessage() {
		return this.lastMessage;
	}

	/**
	 * @return Chat encryptor, if encryption is enabled and user specified valid key in config.
	 */

	public Optional<Encryptor<?>> getEncryptor() {
		if (!this.isValid())
			return Optional.empty();

		try {
			String[] keys = this.encryptionKey.split(",");
			int validKeyIndex = getUsedEncryptionKeyIndex() >= 0 && getUsedEncryptionKeyIndex() < keys.length ? getUsedEncryptionKeyIndex() : 0;
			return Optional.of(this.algorithm.getProcessor(keys[validKeyIndex]));
		} catch (InvalidKeyException ex) {
			throw new RuntimeException(ex); // shouldn't happen due to prior validation
		}
	}

	public Encryptor<?>[] getAllEncryptors() {
		if (!this.isValid())
			return new Encryptor[0];

		try {
			ArrayList<Encryptor<?>> encryptors = new ArrayList<>();
			for(String key : this.encryptionKey.split(",")) {
				encryptors.add(this.algorithm.getProcessor(key));
			}
			return encryptors.toArray(new Encryptor[0]);
		} catch (InvalidKeyException ex) {
			throw new RuntimeException(ex); // shouldn't happen due to prior validation
		}
	}

	public int getUsedEncryptionKeyIndex() {
		return usedEncryptionKeyIndex;
	}

	public void setUsedEncryptionKeyIndex(int usedEncryptionKeyIndex) {
		this.usedEncryptionKeyIndex = usedEncryptionKeyIndex;
		saveFile();
	}

	public CompressionPolicy getCompressionPolicy() {
		return compressionPolicy;
	}

	public void setCompressionPolicy(CompressionPolicy compressionPolicy) {
		this.compressionPolicy = compressionPolicy;
		saveFile();
	}

	public @Nullable Compression getSpecificCompression() {
		return specificCompression;
	}

	public void setSpecificCompression(@Nullable Compression specificCompression) {
		this.specificCompression = specificCompression;
		this.specificCompressionName = specificCompression != null ? specificCompression.getCompressionName() : null;
		saveFile();
	}

	public enum CompressionPolicy {
		WhenNecessary,
		Preferred,
		Always,
		Never,
	}

}
