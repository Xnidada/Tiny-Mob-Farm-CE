package cn.davidma.tinymobfarm.core.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentTranslation;

public class Msg {

	public static void tellPlayer(EntityPlayer player, String text, Object... args) {
		player.sendMessage(new TextComponentTranslation(text, args));
	}
}
