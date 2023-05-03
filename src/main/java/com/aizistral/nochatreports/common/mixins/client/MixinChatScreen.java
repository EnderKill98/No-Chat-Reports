package com.aizistral.nochatreports.common.mixins.client;

import com.aizistral.nochatreports.common.encryption.AESEncryptor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.aizistral.nochatreports.common.NCRClient;
import com.aizistral.nochatreports.common.config.NCRConfig;
import com.aizistral.nochatreports.common.core.ServerSafetyLevel;
import com.aizistral.nochatreports.common.core.ServerSafetyState;
import com.aizistral.nochatreports.common.core.SigningMode;
import com.aizistral.nochatreports.common.gui.AdvancedImageButton;
import com.aizistral.nochatreports.common.gui.AdvancedTooltip;
import com.aizistral.nochatreports.common.gui.EncryptionButton;
import com.aizistral.nochatreports.common.gui.EncryptionWarningScreen;
import com.aizistral.nochatreports.common.gui.GUIShenanigans;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * This is responsible for adding safety status indicator to the bottom-right corner of chat screen.
 * @author Aizistral
 */

@Mixin(ChatScreen.class)
public abstract class MixinChatScreen extends Screen {
	private static final ResourceLocation CHAT_STATUS_ICONS = new ResourceLocation("nochatreports", "textures/gui/chat_status_icons_extended.png");
	private static final ResourceLocation ENCRYPTION_BUTTON = new ResourceLocation("nochatreports", "textures/gui/encryption_toggle_button.png");
	private ImageButton safetyStatusButton;
	@Shadow
	protected EditBox input;

	protected MixinChatScreen() {
		super(null);
		throw new IllegalStateException("Can't touch this");
	}

	@Override
	protected void changeFocus(ComponentPath path) {
		if (GUIShenanigans.getLeaf(path).component() instanceof EditBox) {
			super.changeFocus(path);
		}
	}

	@Override
	public void setFocused(GuiEventListener listener) {
		if (listener instanceof EditBox) {
			super.setFocused(listener);
		}
	}

	@Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
	private void onHandleChatInput(String string, boolean bl, CallbackInfoReturnable<Boolean> info) {
		if (NCRConfig.getServerPreferences().hasModeCurrent(SigningMode.ALWAYS) && !ServerSafetyState.allowChatSigning()) {
			if (this.minecraft.getConnection().getConnection().isEncrypted()) {
				if (!this.normalizeChatMessage(string).isEmpty()) {
					ServerSafetyState.updateCurrent(ServerSafetyLevel.INSECURE);
					ServerSafetyState.scheduleSigningAction(NCRClient::resendLastChatMessage);
					ServerSafetyState.setAllowChatSigning(true);
					info.setReturnValue(true);
				}
			}
		}
	}

	@Inject(method = "normalizeChatMessage", at = @At("RETURN"), cancellable = true)
	public void onBeforeMessage(String original, CallbackInfoReturnable <String> info) {
		final String[] message = {info.getReturnValue() };
		NCRConfig.getEncryption().setLastMessage(message[0]);

		if (!message[0].isEmpty() && !Screen.hasControlDown() && NCRConfig.getEncryption().shouldEncrypt(message[0])) {			NCRConfig.getEncryption().getEncryptor().ifPresent(e -> {
			//replace & color codes with § when it has letter or number after it
			message[0] = colorCodes(message[0]);
			int index = NCRConfig.getEncryption().getEncryptionStartIndex(message[0]);
			String noencrypt = message[0].substring(0, index);
			String encrypt = message[0].substring(index);

				if (encrypt.length() > 0) {
					if(e instanceof AESEncryptor<?>) {
						info.setReturnValue(((AESEncryptor<?>) e).encryptAndCompress(noencrypt, encrypt, NCRConfig.getEncryption().getCompressionPolicy(), NCRConfig.getEncryption().getSpecificCompression()));
					}else {
						info.setReturnValue(noencrypt + e.encrypt("#%" + encrypt));
					}
				}
			});
		}
	}

	@Inject(method = "init", at = @At("HEAD"))
	private void onInit(CallbackInfo info) {
		int buttonX = this.width - 23;

		if (NCRConfig.getClient().showServerSafety() && NCRConfig.getClient().enableMod()) {
			this.safetyStatusButton = new AdvancedImageButton(buttonX, this.height - 37, 20, 20, this.getXOffset(),
					0, 20, CHAT_STATUS_ICONS, 128, 128, btn -> {
						if (!NCRClient.areSigningKeysPresent())
							return;

						var address = ServerSafetyState.getLastServer();

						if (address != null) {
							var preferences = NCRConfig.getServerPreferences();
							preferences.setMode(address, preferences.getModeUnresolved(address).next());
							preferences.saveFile();
						}
					}, Component.empty(), this);
			this.safetyStatusButton.setTooltip(new AdvancedTooltip(() -> {
				MutableComponent tooltip = this.getSafetyLevel().getTooltip();

				if (ServerSafetyState.allowChatSigning()) {
					tooltip = Component.translatable("gui.nochatreports.safety_status.insecure_signing");
				} else if (ServerSafetyState.isInSingleplayer())
					return tooltip;

				ServerAddress address = ServerSafetyState.getLastServer();
				SigningMode mode = NCRConfig.getServerPreferences().getModeUnresolved(address);
				String signing = "gui.nochatreports.signing_status.";

				if (!this.minecraft.getConnection().getConnection().isEncrypted()) {
					signing += "disabled_offline";
				} else if (ServerSafetyState.getCurrent() == ServerSafetyLevel.REALMS) {
					signing += "allowed_realms";
				} else if (mode.resolve() == SigningMode.ALWAYS) {
					if (ServerSafetyState.allowChatSigning()) {
						signing += "allowed";
					} else {
						signing += "disabled_allowance_pending";
					}
				} else if (ServerSafetyState.allowChatSigning()) {
					signing += "allowed_session";
				} else {
					signing += "disabled";
				}

				tooltip.append("\n\n");
				tooltip.append(Component.translatable(signing));

				if (ServerSafetyState.isOnRealms())
					return tooltip;

				tooltip.append("\n\n");
				tooltip.append(Component.translatable("gui.nochatreports.safety_status_button.controls"));
				tooltip.append("\n\n");
				tooltip.append(Component.translatable("gui.nochatreports.signing_mode",
						mode.getName().withStyle(ChatFormatting.BOLD, ChatFormatting.AQUA)));
				tooltip.append("\n");
				tooltip.append((mode == SigningMode.DEFAULT ? mode.resolve() : mode).getTooltip());

				return tooltip;
			}).setMaxWidth(250).setRenderWithoutGap(true));

			this.addRenderableWidget(this.safetyStatusButton);
			buttonX -= 25;
		}

		if (!NCRConfig.getEncryption().showEncryptionButton())
			return;

		int xStart = !NCRConfig.getEncryption().isValid() ? 40 : (NCRConfig.getEncryption().isEnabled() ? 0 : 20);

		var button = new EncryptionButton(buttonX, this.height - 37, 20, 20, xStart,
				0, 20, ENCRYPTION_BUTTON, 64, 64, btn -> {
					if (!EncryptionWarningScreen.seenOnThisSession() && !NCRConfig.getEncryption().isWarningDisabled()
							&& !NCRConfig.getEncryption().isEnabled()) {
						Minecraft.getInstance().setScreen(new EncryptionWarningScreen(this));
					} else if (NCRConfig.getEncryption().isValid()) {
						NCRConfig.getEncryption().toggleEncryption();
						((EncryptionButton)btn).xTexStart = NCRConfig.getEncryption().isEnabledAndValid() ? 0 : 20;
					} else {
						((EncryptionButton)btn).openEncryptionConfig();
					}
				}, Component.empty(), this);
		button.setTooltip(new AdvancedTooltip(() -> {
			if (NCRConfig.getEncryption().isValid())
				return Component.translatable("gui.nochatreports.encryption_tooltip", Language.getInstance()
						.getOrDefault("gui.nochatreports.encryption_state_" + (NCRConfig.getEncryption()
								.isEnabledAndValid() ? "on" : "off")));
			else
				return Component.translatable("gui.nochatreports.encryption_tooltip_invalid", Language.getInstance()
						.getOrDefault("gui.nochatreports.encryption_state_" + (NCRConfig.getEncryption()
								.isEnabledAndValid() ? "on" : "off")));
		}).setMaxWidth(250));
		button.active = true;
		button.visible = true;

		this.addRenderableWidget(button);
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void onTick(CallbackInfo info) {
		if (this.safetyStatusButton != null) {
			this.safetyStatusButton.xTexStart = this.getXOffset();
		}
	}

	private ServerSafetyLevel getSafetyLevel() {
		return ServerSafetyState.getCurrent();
	}

	private int getXOffset() {
		return this.getXOffset(this.getSafetyLevel());
	}

	private int getXOffset(ServerSafetyLevel level) {
		return switch (level) {
		case SECURE, SINGLEPLAYER -> 21;
		case UNINTRUSIVE -> 42;
		case INSECURE -> 0;
		case REALMS -> 63;
		case UNKNOWN -> 84;
		case UNDEFINED -> 105;
		};
	}

	@Shadow
	public abstract String normalizeChatMessage(String string);
	private String colorCodes(String message) {
		List<String> colors = new ArrayList<>();
		List<String> specialChars = new ArrayList<>();
		colors.add("&0");
		colors.add("&1");
		colors.add("&2");
		colors.add("&3");
		colors.add("&4");
		colors.add("&5");
		colors.add("&6");
		colors.add("&7");
		colors.add("&8");
		colors.add("&9");
		colors.add("&a");
		colors.add("&b");
		colors.add("&c");
		colors.add("&d");
		colors.add("&e");
		colors.add("&f");
		colors.add("&k");
		colors.add("&l");
		colors.add("&m");
		colors.add("&n");
		colors.add("&o");
		colors.add("&r");
		specialChars.add("\\n");
		specialChars.add("\\r");
		for (String color: colors) {
			message = message.replace(color, color.replace("&", "\u00a7"));
		}
		for (String specialChar: specialChars) {
			//replace & with backslash
			message = message.replace(specialChar, specialChars(specialChar));
		}

		return message;
	}

	private String specialChars(String character) {
		return switch (character) {
			case "\\n" -> "\n";
			case "\\r" -> "\r";
			default -> character;
		};
	}

}
