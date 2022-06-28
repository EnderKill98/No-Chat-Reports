package com.aizistral.nochatreports.mixins;

import java.time.Instant;
import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.aizistral.nochatreports.handlers.NoReportsConfig;
import com.mojang.authlib.minecraft.BanDetails;
import com.mojang.authlib.minecraft.UserApiService;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Minecraft.ChatStatus;
import net.minecraft.world.entity.player.ChatVisiblity;

@Mixin(Minecraft.class)
public class MixinMinecraft {

	@Inject(method = "createTitle", at = @At("RETURN"), cancellable = true, require = 0)
	private void onCreateTitle(CallbackInfoReturnable<String> info) {
		if (info.getReturnValue().endsWith("1.19.1")) {
			info.setReturnValue(info.getReturnValue().replace("1.19.1", "1.19.84"));
		}
	}

	@Inject(method = "multiplayerBan", at = @At("HEAD"), cancellable = true)
	private void onFetchBanDetails(CallbackInfoReturnable<BanDetails> info) {
		if (NoReportsConfig.suppressBanNotices()) {
			info.setReturnValue(null);
		}
	}

}