package com.aizistral.nochatreports.common.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

import com.aizistral.nochatreports.common.compression.Compression;
import com.aizistral.nochatreports.common.config.NCRConfig;
import com.aizistral.nochatreports.common.config.NCRConfigEncryption;
import com.aizistral.nochatreports.common.encryption.Encryption;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringUtil;

@Environment(EnvType.CLIENT)
public class EncryptionConfigScreen extends Screen {
	private static final Component HEADER = Component.translatable("gui.nochatreports.encryption_config.header");
	private static final Component KEY_DESC = Component.translatable("gui.nochatreports.encryption_config.key_desc");
	private static final Component PASS_DESC = Component
			.translatable("gui.nochatreports.encryption_config.passphrase_desc");
	private static final Component VALIDATION_OK = Component
			.translatable("gui.nochatreports.encryption_config.validation_ok");
	private static final Component VALIDATION_FAILED = Component
			.translatable("gui.nochatreports.encryption_config.validation_failed");
	private static final Component DICE_TOOLTIP = Component
			.translatable("gui.nochatreports.encryption_config.dice_tooltip");
	private static final Component PASS_NOT_ALLOWED = Component
			.translatable("gui.nochatreports.encryption_config.pass_not_allowed");
	private static final Component ENCRYPT_PUBLIC = Component
			.translatable("gui.nochatreports.encryption_config.encrypt_public");
	private static final ResourceLocation CROSSMARK = new ResourceLocation("nochatreports", "encryption/crossmark_big");

	private static final int FIELDS_Y_START = 45;
	private final Screen previous;
	private CustomEditBox keyField, passField;
	private AdvancedImageButton validationIcon;
	private CycleButton<Encryption> algorithmButton;
	private CycleButton<String> specificCompressionButton;
	private CycleButton<NCRConfigEncryption.CompressionPolicy> compressionPolicyButton;
	private MultiLineLabel keyDesc = MultiLineLabel.EMPTY, passDesc = MultiLineLabel.EMPTY;
	protected Checkbox encryptPublicCheck;
	private boolean settingPassKey = false;
	private CycleButton<Integer> usedEncryptionKeyIndexButton;

	public EncryptionConfigScreen(Screen previous) {
		super(CommonComponents.EMPTY);
		this.previous = previous;
	}

	private NCRConfigEncryption getConfig() {
		return NCRConfig.getEncryption();
	}

	@Override
	protected void init() {
		this.clearWidgets();
		super.init();

		int w = (int) (this.width * (this.hugeGUI() ? 0.9 : 0.7));

		this.keyDesc = MultiLineLabel.create(this.font, KEY_DESC, w - 5);
		int keyDescSpace = (this.keyDesc.getLineCount() + 1) * this.getLineHeight();

		this.passDesc = MultiLineLabel.create(this.font, PASS_DESC, w - 5);
		int passDescSpace = (this.passDesc.getLineCount() + 1) * this.getLineHeight();

		w -= 52;

		this.keyField = new CustomEditBox(this.font, (this.width - w) / 2 - 2,
				(this.hugeGUI() ? 25 : FIELDS_Y_START) + keyDescSpace - 15, w, 18, CommonComponents.EMPTY);
		this.keyField.setMaxLength(512);
		this.keyField.setResponder(this::onKeyUpdate);
		this.addWidget(this.keyField);

		var button = new AdvancedImageButton(this.keyField.getX() + this.keyField.getWidth() - 15,
				this.keyField.getY() + 3, 12, 12, SwitchableSprites.of(
						GUIShenanigans.getSprites("encryption/checkmark", false, false),
						GUIShenanigans.getSprites("encryption/crossmark", false, false)),
				btn -> {
				}, Component.empty(), this);

		button.setTooltip(new AdvancedTooltip(
				() -> this.validationIcon != null && this.validationIcon.getSpritesIndex() == 0 ? VALIDATION_OK
						: VALIDATION_FAILED)
				.setMaxWidth(250));
		button.active = false;
		button.visible = true;

		this.addRenderableOnly(this.validationIcon = button);

		button = new AdvancedImageButton(this.keyField.getX() - 22, this.keyField.getY() - 0, 18, 18,
				SwitchableSprites.of(GUIShenanigans.getSprites("encryption/key_button", false, false)), btn -> {
				},
				Component.empty(), this);
		button.active = false;
		button.visible = true;

		this.addRenderableOnly(button);

		button = new AdvancedImageButton(this.keyField.getX() + this.keyField.getWidth() + 4, this.keyField.getY() - 1,
				23, 20, SwitchableSprites.of(GUIShenanigans.getSprites("encryption/random_button")), btn -> {
					this.unfocusFields();
					this.keyField.setValue(this.algorithmButton.getValue().getRandomKey());
				}, Component.empty(), this);
		button.setTooltip(new AdvancedTooltip(DICE_TOOLTIP).setMaxWidth(250));
		button.active = true;
		button.visible = true;

		this.addRenderableWidget(button);

		w += 25;

		this.passField = new CustomEditBox(this.font, (this.width - w) / 2 + 11, this.keyField.getY() +
				this.keyField.getHeight() + passDescSpace + (this.hugeGUI() ? -3 : 13), w, 18, CommonComponents.EMPTY);
		this.passField.setMaxLength(512);
		this.passField.setResponder(this::onPassphraseUpdate);
		this.addWidget(this.passField);

		button = new AdvancedImageButton(this.passField.getX() - 22, this.passField.getY() - 0, 18, 18,
				SwitchableSprites.of(GUIShenanigans.getSprites("encryption/lock_button", false, false)),
				btn -> {
				}, Component.empty(), this);
		button.active = false;
		button.visible = true;

		this.addRenderableOnly(button);

		int checkWidth = this.font.width(ENCRYPT_PUBLIC);
		this.encryptPublicCheck = Checkbox.builder(ENCRYPT_PUBLIC, this.font).pos(this.width / 2 - checkWidth / 2 - 8,
				this.passField.getY() + 24).selected(NCRConfig.getEncryption().shouldEncryptPublic()).build();
		this.addRenderableWidget(this.encryptPublicCheck);

		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, btn -> {
			this.onDone();
			this.onClose();
		}).pos(this.width / 2 + 4, this.passField.getY() + 48).size(219, 20).build());

		CycleButton<Encryption> cycle = CycleButton.<Encryption>builder(value -> {
			return Component.translatable("gui.nochatreports.encryption_config.algorithm",
					Component.translatable("algorithm.nochatreports." + value.getID() + ".name"));
		}).withValues(Encryption.getRegistered()).displayOnlyValue().withInitialValue(this.getConfig()
				.getAlgorithm()).withTooltip(
						value -> new AdvancedTooltip(Component.translatable(
								"algorithm.nochatreports." + value.getID())).setMaxWidth(250))
				.create(this.width / 2 - 4 - 218, this.passField.getY() + 48, 218, 20, CommonComponents.EMPTY,
						(cycleButton, value) -> {
							this.unfocusFields();
							this.onAlgorithmUpdate(value);
						});

		this.addRenderableWidget(this.algorithmButton = cycle);

		CycleButton<NCRConfigEncryption.CompressionPolicy> compressionPolicyCycle = CycleButton.<NCRConfigEncryption.CompressionPolicy>builder(
				value -> {
					return Component.translatable("gui.nochatreports.encryption_config.compression_policy",
							Component.translatable(
									"compression_policy.nochatreports." + value.toString().toLowerCase() + ".name"));
				}).withValues(NCRConfigEncryption.CompressionPolicy.values()).displayOnlyValue()
				.withInitialValue(this.getConfig().getCompressionPolicy())
				.withTooltip(value -> new AdvancedTooltip(Component.translatable(
						"compression_policy.nochatreports." + value.toString().toLowerCase())).setMaxWidth(250))
				.create(this.width / 2 - 4 - 218, this.passField.getY() + 48 + 24, 218, 20, CommonComponents.EMPTY,
						(cycleButton, value) -> {
							this.unfocusFields();
						});

		this.addRenderableWidget(this.compressionPolicyButton = compressionPolicyCycle);

		CycleButton<String> specificCompressionCycle = CycleButton.<String>builder(value -> {
			return Component.translatable("gui.nochatreports.encryption_config.specific_compression",
					Component.translatable("specific_compression.nochatreports." + value + ".name"));
		}).withValues(getRegisteredCompressionIdsAndAuto()).displayOnlyValue().withInitialValue(this.getConfig()
				.getSpecificCompression() == null ? "auto"
						: this.getConfig().getSpecificCompression().getCompressionName().toLowerCase())
				.withTooltip(value -> new AdvancedTooltip(Component.translatable(
						"specific_compression.nochatreports." + value)).setMaxWidth(250))
				.create(this.width / 2 + 4, this.passField.getY() + 48 + 24, 218, 20, CommonComponents.EMPTY,
						(cycleButton, value) -> {
							this.unfocusFields();
						});

		this.addRenderableWidget(this.specificCompressionButton = specificCompressionCycle);

		this.onAlgorithmUpdate(this.algorithmButton.getValue());

		if (!StringUtil.isNullOrEmpty(this.getConfig().getEncryptionPassphrase())) {
			this.passField.setValue(this.getConfig().getEncryptionPassphrase());
		} else if (!StringUtil.isNullOrEmpty(this.getConfig().getEncryptionKey())) {
			if (!Objects.equals(this.getConfig().getEncryptionKey(), this.algorithmButton.getValue()
					.getDefaultKey())) {
				this.keyField.setValue(this.getConfig().getEncryptionKey());
			} else {
				this.keyField.setValue("");
			}
		}

		updateUsedKeyIndexButton();
	}

	private String[] getRegisteredCompressionIdsAndAuto() {
		ArrayList<String> compressions = new ArrayList<>();
		for (Compression compression : Compression.getRegistered())
			compressions.add(compression.getCompressionName().toLowerCase());
		compressions.add("auto");
		return compressions.toArray(new String[0]);
	}

	private ArrayList<Integer> indicesForLength(int length) {
		ArrayList<Integer> indices = new ArrayList<>();
		for (int i = 0; i < length; i++)
			indices.add(i);
		return indices;
	}

	public void onUpdateUsedKeyIndex(int newKeyIndex) {
		NCRConfig.getEncryption().setUsedEncryptionKeyIndex(newKeyIndex);
	}

	public void updateUsedKeyIndexButton() {
		if (this.usedEncryptionKeyIndexButton != null) {
			this.removeWidget(this.usedEncryptionKeyIndexButton);
		}
		int initialValue = NCRConfig.getEncryption().getUsedEncryptionKeyIndex();
		int keyCount = keyField.getValue().split(",").length;
		if (this.usedEncryptionKeyIndexButton != null) {
			if (this.usedEncryptionKeyIndexButton.getValue() < keyCount) {
				initialValue = this.usedEncryptionKeyIndexButton.getValue();
			} else {
				initialValue = Math.max(0, keyCount - 1);
			}
		}

		int buttonWidth = 128;
		CycleButton<Integer> cycle = CycleButton.<Integer>builder(value -> {
			return Component.translatable("gui.nochatreports.encryption_config.encryption_key_index", value);
		}).withValues(indicesForLength(keyCount))
				.displayOnlyValue()
				.withInitialValue(initialValue)
				.withTooltip(value -> new AdvancedTooltip(
						Component.literal(
								"You can have multiple keys separated by commas. This index specifies, which key is use for encrypting messages."))
						.setMaxWidth(250))
				.create(this.keyField.getX() + this.keyField.getWidth() - buttonWidth, this.keyField.getY() + 24,
						buttonWidth, 20, CommonComponents.EMPTY,
						(cycleButton, value) -> {
							this.unfocusFields();
						});

		this.addRenderableWidget(this.usedEncryptionKeyIndexButton = cycle);
	}

	@Override
	public void render(GuiGraphics graphics, int i, int j, float f) {
		if (!this.passField.isActive()) {
			if (this.passField.isFocused()) {
				this.passField.setFocused(false);
			}
			this.passField.setEditable(false);
		}

		this.renderBackground(graphics, j, j, f);
		graphics.drawCenteredString(this.font, HEADER, this.width / 2, this.hugeGUI() ? 8 : 16, 0xFFFFFF);

		this.keyDesc.renderLeftAligned(graphics, this.keyField.getX() - 20, (this.hugeGUI() ? 25 : FIELDS_Y_START),
				this.getLineHeight(), 0xFFFFFF);

		this.keyField.render(graphics, i, j, f);

		this.passDesc.renderLeftAligned(graphics, this.passField.getX() - 20,
				this.keyField.getY() + this.keyField.getHeight() + (this.hugeGUI() ? 12 : 28),
				this.getLineHeight(), 0xFFFFFF);

		this.passField.render(graphics, i, j, f);

		// if (this.algorithmButton != null && this.algorithmButton.isMouseOver(i, j)) {
		// this.renderTooltip(poseStack, this.algorithmButton.getTooltip(), i, j);
		// }

		super.render(graphics, i, j, f);

		if (StringUtil.isNullOrEmpty(this.keyField.getValue()) && !this.keyField.isFocused()) {
			graphics.drawString(this.font,
					Component.translatable("gui.nochatreports.encryption_config.default_key",
							this.algorithmButton.getValue().getDefaultKey()),
					this.keyField.getX() + 4,
					this.keyField.getY() + 5, 0x999999);
		}

		if (!this.passField.active) {
			graphics.drawString(this.font, PASS_NOT_ALLOWED, this.passField.getX() + 4,
					this.passField.getY() + 5, 0x999999);
			RenderSystem.enableDepthTest();
			graphics.blitSprite(CROSSMARK, this.passField.getX() - 20, this.passField.getY() + 3, 14, 13);
		}
	}

	private int getLineHeight() {
		if (this.hugeGUI())
			return (int) (this.minecraft.font.lineHeight * 1.5) + 1;
		else
			return this.minecraft.font.lineHeight * 2;
	}

	private void onKeyUpdate(String key) {
		if (!this.settingPassKey) {
			this.passField.setValue("");
		}

		if (!StringUtil.isNullOrEmpty(key)) {
			boolean isValid = false;
			for (String subKey : key.split(",")) {
				if (this.algorithmButton.getValue().validateKey(subKey)) {
					isValid = true;
					break;
				}
			}
			this.validationIcon.useSprites(isValid ? 0 : 1);
		} else {
			this.validationIcon.useSprites(0);
		}
	}

	private void onPassphraseUpdate(String pass) {
		Encryption encryption = this.algorithmButton.getValue();

		this.settingPassKey = true;
		if (!StringUtil.isNullOrEmpty(pass)) {
			StringBuilder keyList = new StringBuilder();
			for (String subPass : pass.split(",")) {
				if (encryption.supportsPassphrases()) {
					if (keyList.length() > 0)
						keyList.append(',');
					keyList.append(encryption.getPassphraseKey(subPass));
				}
			}
			if (keyList.length() > 0)
				this.keyField.setValue(keyList.toString());
		} else {
			this.onKeyUpdate(this.keyField.getValue());
		}
		this.settingPassKey = false;
	}

	private void onAlgorithmUpdate(Encryption encryption) {
		if (!encryption.supportsPassphrases()) {
			this.passField.setFocused(false);
			this.passField.setEditable(this.passField.active = false);
			this.onKeyUpdate(this.keyField.getValue());
		} else {
			this.passField.setEditable(this.passField.active = true);
			this.onPassphraseUpdate(this.passField.getValue());
		}
	}

	private void unfocusFields() {
		this.keyField.setFocused(false);
		this.passField.setFocused(false);
	}

	private void onDone() {
		var config = NCRConfig.getEncryption();
		var encryption = this.algorithmButton.getValue();
		var usedEncryptionKeyIndex = this.usedEncryptionKeyIndexButton.getValue();
		var compressionPolicy = this.compressionPolicyButton.getValue();
		var specificCompression = Arrays.stream(Compression.getRegistered())
				.filter(c -> c.getCompressionName().equalsIgnoreCase(this.specificCompressionButton.getValue()))
				.findFirst().orElse(null);

		config.setAlgorithm(encryption);
		config.setEncryptionKey(!StringUtil.isNullOrEmpty(this.keyField.getValue()) ? this.keyField.getValue()
				: encryption.getDefaultKey());
		config.setEncryptPublic(this.encryptPublicCheck.selected());
		config.setUsedEncryptionKeyIndex(usedEncryptionKeyIndex);
		config.setCompressionPolicy(compressionPolicy);
		config.setSpecificCompression(specificCompression);
	}

	private boolean hugeGUI() {
		return this.height <= 1080 / 4;
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.previous);
	}

	private static class CustomEditBox extends EditBox {
		public CustomEditBox(Font font, int i, int j, int k, int l, Component component) {
			super(font, i, j, k, l, component);
		}

		@Override
		public void setFocused(boolean bl) {
			super.setFocused(bl);
		}
	}

}
