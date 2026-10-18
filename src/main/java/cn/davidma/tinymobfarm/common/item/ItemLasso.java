package cn.davidma.tinymobfarm.common.item;

import java.util.List;

import cn.davidma.tinymobfarm.common.TinyMobFarm;
import cn.davidma.tinymobfarm.core.ConfigTinyMobFarm;
import cn.davidma.tinymobfarm.core.Reference;
import cn.davidma.tinymobfarm.core.util.EntityHelper;
import cn.davidma.tinymobfarm.core.util.Msg;
import cn.davidma.tinymobfarm.core.util.NBTHelper;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

public class ItemLasso extends Item {

	public ItemLasso() {
		super();
		this.setRegistryName(Reference.MOD_ID + ":lasso");
		this.setTranslationKey(this.getRegistryName().toString());
		this.setMaxStackSize(1);
		this.setMaxDamage(ConfigTinyMobFarm.LASSO_DURABILITY);
		this.setCreativeTab(TinyMobFarm.creativeTabTinyMobFarm);
	}
	
	@Override
	public int getItemStackLimit(ItemStack stack) {
		return 1;
	}
	
	@Override
	public boolean itemInteractionForEntity(ItemStack stack, EntityPlayer player, EntityLivingBase target, EnumHand hand) {
		if (NBTHelper.hasMob(stack) || target.isDead || !(target instanceof EntityLiving)) return false;
		
		NBTTagCompound nbt = NBTHelper.getBaseTag(stack);
		
		// Cannot capture boss.
		if (!target.isNonBoss()) {
			if (!player.world.isRemote) {
				Msg.tellPlayer(player, "tinymobfarm.error.cannot_capture_boss");
			}
			return true;
		}
		
		// Blacklist.
		if (EntityHelper.isMobBlacklisted((EntityLiving) target)) {
			if (!player.world.isRemote) {
				Msg.tellPlayer(player, "tinymobfarm.error.blacklisted_mob");
			}
			return true;
		}

		// Health limits.
		if (target.getMaxHealth() >= ConfigTinyMobFarm.LASSO_MAX_MOB_HEALTH) {
			if (!player.world.isRemote) {
				Msg.tellPlayer(player, "tinymobfarm.error.max_health_too_high", ConfigTinyMobFarm.LASSO_MAX_MOB_HEALTH);
			}
			return true;
		}
		if (target.getHealth() > ConfigTinyMobFarm.LASSO_CAPTURE_HEALTH_LIMIT) {
			if (!player.world.isRemote) {
				Msg.tellPlayer(player, "tinymobfarm.error.health_too_high", ConfigTinyMobFarm.LASSO_CAPTURE_HEALTH_LIMIT);
			}
			return true;
		}
		
		if (!player.world.isRemote) {
			NBTTagCompound mobData = target.serializeNBT();
			mobData.removeTag("Rotation");
			nbt.setTag(NBTHelper.MOB_DATA, mobData);
			nbt.setString(NBTHelper.MOB_NAME, target.getName());
			nbt.setString(NBTHelper.MOB_LOOTTABLE_LOCATION, EntityHelper.getLootTableLocation((EntityLiving) target));
			nbt.setDouble(NBTHelper.MOB_HEALTH, Math.round(target.getHealth() * 10) / 10.0);
			nbt.setDouble(NBTHelper.MOB_MAX_HEALTH, target.getMaxHealth());
			nbt.setBoolean(NBTHelper.MOB_HOSTILE, target.isCreatureType(EnumCreatureType.MONSTER, false));
			
			NBTHelper.setBaseTag(stack, nbt);
			
			if (player.isCreative()) {
				ItemStack newLasso = new ItemStack(this);
				NBTHelper.setBaseTag(newLasso, nbt);
				player.addItemStackToInventory(newLasso);
			}
			
			target.setDead();
			player.inventory.markDirty();
		}
		
		return true;
	}
		
	@Override
	public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
		if (NBTHelper.hasMob(stack)) {
			NBTTagCompound nbt = NBTHelper.getBaseTag(stack);
			String name = nbt.getString(NBTHelper.MOB_NAME);
			double health = nbt.getDouble(NBTHelper.MOB_HEALTH);
			double maxHealth = nbt.getDouble(NBTHelper.MOB_MAX_HEALTH);
			
			tooltip.add(I18n.format("tinymobfarm.tooltip.mob_name", name));
			tooltip.add(I18n.format("tinymobfarm.tooltip.health", health, maxHealth));
			if (nbt.getBoolean(NBTHelper.MOB_HOSTILE)) tooltip.add(I18n.format("tinymobfarm.tooltip.hostile"));
		} else {
			tooltip.add(I18n.format("tinymobfarm.tooltip.capture"));
		}
	}
	
	@Override
	public boolean hasEffect(ItemStack stack) {
		return NBTHelper.hasMob(stack);
	}
}
