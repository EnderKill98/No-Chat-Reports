package com.aizistral.nochatreports.mixins.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.aizistral.nochatreports.config.NCRConfig;
import com.aizistral.nochatreports.core.ServerSafetyState;
import com.mojang.authlib.minecraft.TelemetrySession;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;

@Mixin(Minecraft.class)
public class MixinMinecraft {

	@Inject(method = "getProfileKeyPairManager", at = @At("HEAD"), cancellable = true)
	private void onGetProfileKeyPairManager(CallbackInfoReturnable<ProfileKeyPairManager> info) {
		if (!NCRConfig.getClient().enableMod())
			return;

		if (!ServerSafetyState.allowChatSigning()) {
			info.setReturnValue(ProfileKeyPairManager.EMPTY_KEY_MANAGER);
		}
	}

	@Inject(method = "allowsTelemetry", at = @At("HEAD"), cancellable = true)
	private void onAllowsTelemetry(CallbackInfoReturnable<Boolean> info) {
		if (NCRConfig.getClient().disableTelemetry()) {
			info.setReturnValue(false);
		}
	}

}
