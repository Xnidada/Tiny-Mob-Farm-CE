package cn.davidma.tinymobfarm.common.tileentity;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import cn.davidma.tinymobfarm.common.block.BlockMobFarm;
import cn.davidma.tinymobfarm.core.ConfigTinyMobFarm;
import cn.davidma.tinymobfarm.core.EnumMobFarm;
import cn.davidma.tinymobfarm.core.Reference;
import cn.davidma.tinymobfarm.core.drop.MobFarmDropManager;
import cn.davidma.tinymobfarm.core.drop.MobFarmDropManager.DropSource;
import cn.davidma.tinymobfarm.core.util.EntityHelper;
import cn.davidma.tinymobfarm.core.util.FakePlayerHelper;
import cn.davidma.tinymobfarm.core.util.NBTHelper;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

public class TileEntityMobFarm extends TileEntity implements ITickable {
	
	private static final String PENDING_DROPS = "PendingDrops";
	private List<ItemStack> pendingDrops = new ArrayList<ItemStack>();
	private int outputRetryCooldown;
	private int storedExperience;
	private DropSource cachedDropSource;
	private int cachedDropSourceVersion = -1;
	private final ItemStackHandler inventory = new ItemStackHandler(1) {
		@Override
		protected void onContentsChanged(int slot) {
			TileEntityMobFarm.this.pendingDrops.clear();
			TileEntityMobFarm.this.clearDropSourceCache();
			TileEntityMobFarm.this.shouldUpdate = true;
			if (TileEntityMobFarm.this.world != null && !TileEntityMobFarm.this.world.isRemote) {
				TileEntityMobFarm.this.saveAndSync();
			}
		}
	};
	private final IItemHandler automationInventory = new MachineItemHandler();
	private EnumMobFarm mobFarmData;
	private EntityLiving model;
	private EnumFacing modelFacing;
	private int currProgress;
	private boolean mechanical;
	private boolean powered;
	private boolean shouldUpdate;

	@Override
	public void update() {
		if (this.shouldUpdate) {
			this.updateModel();
			this.updateRedstone();
			this.shouldUpdate = false;
		}
		if (this.outputRetryCooldown > 0) {
			this.outputRetryCooldown--;
		}
		if (this.isWorking()) {
			if (!this.world.isRemote && this.mobFarmData != null) {
				if (!this.canAttemptOutput()) {
					return;
				}
				if (this.outputRetryCooldown > 0 && !this.pendingDrops.isEmpty()) {
					return;
				}
				int maxProgress = this.mobFarmData.getMaxProgress();
				if (this.currProgress + 1 >= maxProgress) {
					List<ItemStack> drops = this.compactDrops(this.pendingDrops.isEmpty() ? this.createDrops() : this.copyDrops(this.pendingDrops));
					if (ConfigTinyMobFarm.PAUSE_WHEN_OUTPUT_FULL && !this.canStoreDrops(drops)) {
						int blockedProgress = Math.max(0, maxProgress - 1);
						boolean shouldSync = this.currProgress != blockedProgress || this.pendingDrops.isEmpty();
						this.currProgress = blockedProgress;
						if (this.pendingDrops.isEmpty()) {
							this.pendingDrops = this.copyDrops(drops);
						}
						this.outputRetryCooldown = Math.max(1, ConfigTinyMobFarm.OUTPUT_RETRY_INTERVAL_TICKS);
						if (shouldSync) {
							this.saveAndSync();
						}
						return;
					}

					this.currProgress = 0;
					
					if (!this.outputDrops(drops)) {
						this.pendingDrops = this.copyDrops(drops);
						this.currProgress = Math.max(0, maxProgress - 1);
						this.outputRetryCooldown = Math.max(1, ConfigTinyMobFarm.OUTPUT_RETRY_INTERVAL_TICKS);
						this.saveAndSync();
						return;
					}
					this.pendingDrops.clear();
					this.outputRetryCooldown = 0;
					
					FakePlayer daniel = FakePlayerHelper.getPlayer((WorldServer) world);
					this.addMobExperience(daniel);
					this.getLasso().damageItem(this.mobFarmData.getRandomDamage(this.world.rand), daniel);
					
					this.saveAndSync();
					return;
				}
			}
			this.currProgress++;
		} else {
			this.currProgress = 0;
		}
	}
	
	private List<ItemStack> createDrops() {
		return this.copyDrops(this.getDropSource().generateDrops(this.world));
	}

	private DropSource getDropSource() {
		int dropSourceVersion = MobFarmDropManager.getDropSourceVersion();
		if (this.cachedDropSource == null || this.cachedDropSourceVersion != dropSourceVersion) {
			this.cachedDropSource = MobFarmDropManager.getDropSource(this.getLasso());
			this.cachedDropSourceVersion = dropSourceVersion;
		}
		return this.cachedDropSource;
	}

	private void clearDropSourceCache() {
		this.cachedDropSource = null;
		this.cachedDropSourceVersion = -1;
	}

	private boolean canAttemptOutput() {
		return !ConfigTinyMobFarm.PAUSE_WHEN_OUTPUT_FULL || this.hasAdjacentOutputInventory();
	}

	private boolean hasAdjacentOutputInventory() {
		if (this.world == null) return false;

		for (EnumFacing facing: EnumFacing.values()) {
			TileEntity tileEntity = this.world.getTileEntity(this.pos.offset(facing));
			if (tileEntity != null && tileEntity.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, facing.getOpposite())) {
				return true;
			}
		}
		return false;
	}

	private boolean canStoreDrops(List<ItemStack> drops) {
		if (drops.isEmpty()) return true;

		List<ItemStack> remaining = this.copyDrops(drops);
		for (EnumFacing facing: EnumFacing.values()) {
			TileEntity tileEntity = this.world.getTileEntity(this.pos.offset(facing));
			if (tileEntity == null || !tileEntity.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, facing.getOpposite())) {
				continue;
			}

			IItemHandler itemHandler = tileEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, facing.getOpposite());
			SimulatedInventory simulatedInventory = new SimulatedInventory(itemHandler);
			for (int i = 0; i < remaining.size(); i++) {
				ItemStack remain = simulatedInventory.insertItemStacked(remaining.get(i));
				if (remain.isEmpty()) {
					remaining.remove(i);
					i--;
				} else {
					remaining.set(i, remain);
				}
			}

			if (remaining.isEmpty()) return true;
		}

		return false;
	}

	private boolean outputDrops(List<ItemStack> drops) {
		this.insertDropsIntoAdjacentInventories(drops);
		if (!ConfigTinyMobFarm.PAUSE_WHEN_OUTPUT_FULL) {
			this.dropRemainingDrops(drops);
			drops.clear();
		}
		return drops.isEmpty();
	}

	private void insertDropsIntoAdjacentInventories(List<ItemStack> drops) {
		for (EnumFacing facing: EnumFacing.values()) {
			TileEntity tileEntity = this.world.getTileEntity(this.pos.offset(facing));
			if (tileEntity != null) {
				
				if (tileEntity.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, facing.getOpposite())) {
					IItemHandler itemHandler = tileEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, facing.getOpposite());
					for (int i = 0; i < drops.size(); i++) {
						ItemStack remain = ItemHandlerHelper.insertItemStacked(itemHandler, drops.get(i).copy(), false);
						if (remain.isEmpty()) {
							drops.remove(i);
							i--;
						} else {
							drops.set(i, remain);
						}
					}
				}
				
				if (drops.isEmpty()) return;
			}
		}
		
	}

	private void dropRemainingDrops(List<ItemStack> drops) {
		for (ItemStack stack: drops) {
			if (stack.isEmpty()) continue;
			EntityItem entityItem = new EntityItem(this.world, this.pos.getX() + 0.5, this.pos.getY() + 1, this.pos.getZ() + 0.5, stack);
			this.world.spawnEntity(entityItem);
		}
	}

	private List<ItemStack> copyDrops(List<ItemStack> drops) {
		List<ItemStack> copy = new ArrayList<ItemStack>();
		for (ItemStack stack: drops) {
			if (!stack.isEmpty()) {
				copy.add(stack.copy());
			}
		}
		return copy;
	}

	private List<ItemStack> compactDrops(List<ItemStack> drops) {
		List<ItemStack> compacted = new ArrayList<ItemStack>();
		for (ItemStack stack: drops) {
			if (stack.isEmpty()) continue;

			ItemStack remaining = stack.copy();
			for (ItemStack compactedStack: compacted) {
				if (!remaining.isEmpty() && remaining.isStackable() && ItemHandlerHelper.canItemStacksStackRelaxed(compactedStack, remaining)) {
					int transfer = Math.min(remaining.getCount(), compactedStack.getMaxStackSize() - compactedStack.getCount());
					if (transfer > 0) {
						compactedStack.grow(transfer);
						remaining.shrink(transfer);
					}
				}
			}

			while (!remaining.isEmpty()) {
				ItemStack split = remaining.copy();
				if (split.getCount() > split.getMaxStackSize()) {
					split.setCount(split.getMaxStackSize());
				}
				remaining.shrink(split.getCount());
				compacted.add(split);
			}
		}
		return compacted;
	}

	private NBTTagList writeDropsToNBT(List<ItemStack> drops) {
		NBTTagList list = new NBTTagList();
		for (ItemStack stack: drops) {
			if (!stack.isEmpty()) {
				list.appendTag(stack.writeToNBT(new NBTTagCompound()));
			}
		}
		return list;
	}

	private List<ItemStack> readDropsFromNBT(NBTTagList list) {
		List<ItemStack> drops = new ArrayList<ItemStack>();
		for (int i = 0; i < list.tagCount(); i++) {
			ItemStack stack = new ItemStack(list.getCompoundTagAt(i));
			if (!stack.isEmpty()) {
				drops.add(stack);
			}
		}
		return drops;
	}
	
	private void updateModel() {
		if (this.world.isRemote) {
			if (!ConfigTinyMobFarm.RENDER_FARM_MOB_MODEL) {
				this.model = null;
				return;
			}

			if (this.getLasso().isEmpty()) {
				this.model = null;
			} else {
				NBTTagCompound nbt = NBTHelper.getBaseTag(this.getLasso());
				String mobName = nbt.getString(NBTHelper.MOB_NAME);
				if (this.model == null || !this.model.getName().equals(mobName)) {
					NBTTagCompound entityData = nbt.getCompoundTag(NBTHelper.MOB_DATA);
					Entity newModel = EntityList.createEntityFromNBT(entityData, this.world);
					
					if (newModel != null && newModel instanceof EntityLiving) {
						this.model = (EntityLiving) newModel;
						this.modelFacing = this.world.getBlockState(this.pos).getValue(BlockMobFarm.FACING);
					}
				}
			}
		}
	}
	
	public boolean isWorking() {
		if (this.mobFarmData == null || this.getLasso().isEmpty() || this.isPowered()) return false;
		return this.mobFarmData.isLassoValid(this.getLasso());
	}
	
	public void updateRedstone() {
		this.powered = this.world.isBlockPowered(this.pos);
	}

	public void onNeighborOutputChanged() {
		if (!this.pendingDrops.isEmpty()) {
			this.outputRetryCooldown = 0;
		}
	}
	
	public ItemStack getLasso() {
		return this.inventory.getStackInSlot(0);
	}

	private void addMobExperience(FakePlayer player) {
		int capacity = Math.max(0, ConfigTinyMobFarm.MAX_STORED_EXPERIENCE);
		if (capacity <= 0 || this.storedExperience >= capacity || this.getLasso().isEmpty()) {
			this.storedExperience = Math.min(this.storedExperience, capacity);
			return;
		}

		NBTTagCompound mobData = NBTHelper.getBaseTag(this.getLasso()).getCompoundTag(NBTHelper.MOB_DATA);
		Entity entity = EntityList.createEntityFromNBT(mobData, this.world);
		if (!(entity instanceof EntityLiving)) return;

		int experience = EntityHelper.getExperiencePoints((EntityLiving) entity, player);
		this.storedExperience = Math.min(capacity, this.storedExperience + experience);
	}

	public int getStoredExperience() {
		return Math.min(Math.max(0, this.storedExperience), Math.max(0, ConfigTinyMobFarm.MAX_STORED_EXPERIENCE));
	}

	public void collectExperience(net.minecraft.entity.player.EntityPlayer player) {
		if (this.world == null || this.world.isRemote || player == null) return;

		int experience = this.getStoredExperience();
		if (experience <= 0) return;

		this.storedExperience = 0;
		player.addExperience(experience);
		this.saveAndSync();
	}

	public void setLasso(ItemStack lasso) {
		this.inventory.setStackInSlot(0, lasso);
	}
	
	public void setMobFarmData(EnumMobFarm mobFarmData) {
		this.mobFarmData = mobFarmData;
	}

	public void setMechanical(boolean mechanical) {
		this.mechanical = mechanical;
	}

	public boolean isMechanical() {
		return this.mechanical;
	}
	
	public boolean isPowered() {
		return this.powered;
	}
	
	@Deprecated
	public ItemStackHandler getInventory() {
		return this.inventory;
	}
	
	public double getScaledProgress() {
		if (this.mobFarmData == null) return 0;
		return this.currProgress / (double) this.mobFarmData.getMaxProgress();
	}
	
	public EntityLiving getModel() {
		if (this.model == null && this.world != null && this.world.isRemote && ConfigTinyMobFarm.RENDER_FARM_MOB_MODEL && !this.getLasso().isEmpty()) {
			this.updateModel();
		}
		return this.model;
	}
	
	public EnumFacing getModelFacing() {
		return this.modelFacing;
	}
	
	public String getTranslationKey() {
		if (this.mobFarmData == null) return "tile." + Reference.MOD_ID + ":default_mob_farm.name";
		return this.mobFarmData.getTranslationKey(this.mechanical);
	}
	
	public void saveAndSync() {
		if (this.world == null) return;
		IBlockState state = this.world.getBlockState(this.pos);
		this.world.markBlockRangeForRenderUpdate(this.pos, this.pos);
		this.world.notifyBlockUpdate(pos, state, state, 3);
		this.markDirty();
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.mobFarmData = EnumMobFarm.values()[nbt.getInteger(NBTHelper.MOB_FARM_DATA)];
		this.currProgress = nbt.getInteger(NBTHelper.CURR_PROGRESS);
		this.storedExperience = nbt.getInteger(NBTHelper.STORED_EXPERIENCE);
		this.mechanical = nbt.getBoolean("Mechanical");
		this.inventory.deserializeNBT(nbt.getCompoundTag(NBTHelper.INVENTORY));
		this.pendingDrops = this.readDropsFromNBT(nbt.getTagList(PENDING_DROPS, 10));
		this.outputRetryCooldown = nbt.getInteger("OutputRetryCooldown");
		this.shouldUpdate = true;
		
	}
	
	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
		if (this.mobFarmData != null) nbt.setInteger(NBTHelper.MOB_FARM_DATA, this.mobFarmData.ordinal());
		nbt.setInteger(NBTHelper.CURR_PROGRESS, this.currProgress);
		nbt.setInteger(NBTHelper.STORED_EXPERIENCE, this.getStoredExperience());
		nbt.setBoolean("Mechanical", this.mechanical);
		nbt.setTag(NBTHelper.INVENTORY, this.inventory.serializeNBT());
		if (!this.pendingDrops.isEmpty()) {
			nbt.setTag(PENDING_DROPS, this.writeDropsToNBT(this.pendingDrops));
		}
		nbt.setInteger("OutputRetryCooldown", this.outputRetryCooldown);
		return super.writeToNBT(nbt);
	}
	
	@Nullable
	@Override
	public SPacketUpdateTileEntity getUpdatePacket() {
		return new SPacketUpdateTileEntity(this.getPos(), 0, this.getUpdateTag());
	}
	
	@Override
	public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
		this.readFromNBT(packet.getNbtCompound());
	}
	
	@Override
	public NBTTagCompound getUpdateTag() {
		return this.writeToNBT(new NBTTagCompound());
	}
	
	@Override
	public void handleUpdateTag(NBTTagCompound nbt) {
		this.readFromNBT(nbt);
	}

	@Override
	public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
		if (this.mechanical && capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
			return true;
		}
		return super.hasCapability(capability, facing);
	}

	@Override
	@Nullable
	public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
		if (this.mechanical && capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
			return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(this.automationInventory);
		}
		return super.getCapability(capability, facing);
	}

	private boolean isAutomationLassoValid(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() == cn.davidma.tinymobfarm.common.TinyMobFarm.lasso && NBTHelper.hasMob(stack);
	}

	private static class SimulatedInventory {
		private final IItemHandler itemHandler;
		private final List<ItemStack> stacks = new ArrayList<ItemStack>();

		private SimulatedInventory(IItemHandler itemHandler) {
			this.itemHandler = itemHandler;
			for (int i = 0; i < itemHandler.getSlots(); i++) {
				this.stacks.add(itemHandler.getStackInSlot(i).copy());
			}
		}

		private ItemStack insertItemStacked(ItemStack stack) {
			if (stack.isEmpty()) return ItemStack.EMPTY;

			ItemStack remain = stack.copy();
			if (remain.isStackable()) {
				for (int i = 0; i < this.stacks.size(); i++) {
					if (ItemHandlerHelper.canItemStacksStackRelaxed(this.stacks.get(i), remain)) {
						remain = this.insertItem(i, remain);
						if (remain.isEmpty()) return ItemStack.EMPTY;
					}
				}
			}

			for (int i = 0; i < this.stacks.size(); i++) {
				if (this.stacks.get(i).isEmpty()) {
					remain = this.insertItem(i, remain);
					if (remain.isEmpty()) return ItemStack.EMPTY;
				}
			}

			return remain;
		}

		private ItemStack insertItem(int slot, ItemStack stack) {
			if (stack.isEmpty()) return ItemStack.EMPTY;

			ItemStack current = this.stacks.get(slot);
			int slotLimit = Math.min(this.itemHandler.getSlotLimit(slot), stack.getMaxStackSize());
			if (slotLimit <= 0) return stack;

			int insertLimit = slotLimit;
			if (!current.isEmpty()) {
				if (!ItemHandlerHelper.canItemStacksStackRelaxed(current, stack)) {
					return stack;
				}

				insertLimit = slotLimit - current.getCount();
				if (insertLimit <= 0) {
					return stack;
				}
			}

			ItemStack candidate = stack.copy();
			if (candidate.getCount() > insertLimit) {
				candidate.setCount(insertLimit);
			}

			ItemStack rejected = this.itemHandler.insertItem(slot, candidate.copy(), true);
			int accepted = candidate.getCount() - rejected.getCount();
			if (accepted <= 0) {
				return stack;
			}

			if (current.isEmpty()) {
				ItemStack inserted = candidate.copy();
				inserted.setCount(accepted);
				this.stacks.set(slot, inserted);
			} else {
				current.grow(accepted);
			}

			ItemStack remain = stack.copy();
			remain.shrink(accepted);
			return remain.isEmpty() ? ItemStack.EMPTY : remain;
		}
	}

	private class MachineItemHandler implements IItemHandler {
		@Override
		public int getSlots() {
			return 1;
		}

		@Override
		public ItemStack getStackInSlot(int slot) {
			return slot == 0 ? TileEntityMobFarm.this.getLasso() : ItemStack.EMPTY;
		}

		@Override
		public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
			if (slot != 0 || stack.isEmpty() || !TileEntityMobFarm.this.isAutomationLassoValid(stack) || !TileEntityMobFarm.this.getLasso().isEmpty()) {
				return stack;
			}

			ItemStack single = stack.copy();
			single.setCount(1);
			if (!simulate) {
				TileEntityMobFarm.this.setLasso(single);
			}

			ItemStack remain = stack.copy();
			remain.shrink(1);
			return remain;
		}

		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			if (slot != 0 || amount <= 0) {
				return ItemStack.EMPTY;
			}

			ItemStack current = TileEntityMobFarm.this.getLasso();
			if (current.isEmpty()) {
				return ItemStack.EMPTY;
			}

			ItemStack extracted = current.copy();
			extracted.setCount(1);
			if (!simulate) {
				TileEntityMobFarm.this.setLasso(ItemStack.EMPTY);
			}
			return extracted;
		}

		@Override
		public int getSlotLimit(int slot) {
			return 1;
		}
	}

}
