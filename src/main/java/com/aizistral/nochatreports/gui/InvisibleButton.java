package com.aizistral.nochatreports.gui;

import java.util.function.Supplier;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

@Environment(EnvType.CLIENT)
public class InvisibleButton extends Button {

	public InvisibleButton() {
		super(0, 0, 20, 20, Component.empty(), btn -> {}, (btn, stack, x, y) -> {}, Supplier::get);
	}

	@Override
	public void render(PoseStack poseStack, int i, int j, float f) {
		// NO-OP
	}

}