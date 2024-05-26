package com.aizistral.nochatreports.common.mixins.client;

import com.aizistral.nochatreports.common.compression.Compression;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.annotation.Nullable;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;

import com.aizistral.nochatreports.common.NCRCore;
import com.aizistral.nochatreports.common.config.NCRConfig;
import com.aizistral.nochatreports.common.core.EncryptionUtil;

import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Mixin(ChatComponent.class)
public class MixinChatComponent {
	private static final GuiMessageTag.Icon ENCRYPTED_ICON = GuiMessageTag.Icon.valueOf("CHAT_NCR_ENCRYPTED");
	private boolean lastMessageEncrypted;
	private Component lastMessageOriginal;
	private int lastMessageKeyIndex;
	private @Nullable String lastMessageEncapsulation;
	private @Nullable Compression lastMessageCompression;
	private @Nullable Float lastMessageCompressionRatio;

	@ModifyVariable(method = "addRecentChat", at = @At("HEAD"), argsOnly = true)
	private String onAddRecentChat(String message) {
		if (NCRConfig.getEncryption().isEnabledAndValid())
			return NCRConfig.getEncryption().getLastMessage();
		else
			return message;
	}

	@ModifyVariable(index = -1, method = "addMessage(Lnet/minecraft/network/chat/Component;"
			+ "Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;"
					+ "logChatMessage(Lnet/minecraft/client/GuiMessage;)V", ordinal = 0, shift = Shift.BEFORE))
	private GuiMessage modifyGUIMessage(GuiMessage msg) {
		if (NCRConfig.getCommon().enableDebugLog()) {
			NCRCore.LOGGER.info("Adding chat message, structure: " +
					Component.Serializer.toJson(msg.content(),  RegistryAccess.EMPTY));
		}

		var decrypted = EncryptionUtil.tryDecryptDetailed(msg.content());

		decrypted.ifPresentOrElse(info -> {
			this.lastMessageOriginal = EncryptionUtil.recreate(msg.content());
			this.lastMessageEncrypted = true;
			this.lastMessageKeyIndex = info.keyIndex();
			this.lastMessageEncapsulation = info.encapsulation();
			this.lastMessageCompression = info.compression();
			this.lastMessageCompressionRatio = info.compressionRatio();
		}, () -> this.lastMessageEncrypted = false);

		if (this.lastMessageEncrypted) {
			this.lastMessageEncrypted = false;

			Component decryptedComponent = decrypted.get().decrypted();
			GuiMessageTag newTag = msg.tag();

			if (NCRConfig.getEncryption().showEncryptionIndicators()) {
				MutableComponent tooltip = Component.empty().append(Component.translatable("tag.nochatreports.encrypted",
								Component.literal(NCRConfig.getEncryption().getAlgorithm().getName()).withStyle(ChatFormatting.BOLD)))
						.append(CommonComponents.NEW_LINE)
						.append(Component.translatable("tag.nochatreports.encryption_tooltip_extra_key", this.lastMessageKeyIndex))
						.append(CommonComponents.NEW_LINE)
						.append(Component.translatable("tag.nochatreports.encryption_tooltip_extra_encapsultation", this.lastMessageEncapsulation == null ? "Unknown" : this.lastMessageEncapsulation))
						.append(CommonComponents.NEW_LINE);
				if(lastMessageCompression != null)
					tooltip = tooltip.append(Component.translatable("tag.nochatreports.encryption_tooltip_extra_compression", this.lastMessageCompression.getCompressionName()))
							.append(CommonComponents.NEW_LINE);
				if(lastMessageCompressionRatio != null)
					tooltip = tooltip.append(Component.translatable("tag.nochatreports.encryption_tooltip_extra_compression_ratio", new BigDecimal(lastMessageCompressionRatio).setScale(2, RoundingMode.HALF_UP)))
							.append(CommonComponents.NEW_LINE);
				tooltip = tooltip.append(Component.translatable("tag.nochatreports.encrypted_original", this.lastMessageOriginal));
				newTag = new GuiMessageTag(0x8B3EC7, ENCRYPTED_ICON, tooltip, "Encrypted");
			}

			return new GuiMessage(msg.addedTime(), decryptedComponent, msg.signature(), newTag);
		} else
			return msg;
	}

}
