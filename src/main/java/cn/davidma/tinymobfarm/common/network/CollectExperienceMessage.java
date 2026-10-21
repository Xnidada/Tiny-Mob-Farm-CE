package cn.davidma.tinymobfarm.common.network;

import cn.davidma.tinymobfarm.common.tileentity.TileEntityMobFarm;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class CollectExperienceMessage implements IMessage {

	private BlockPos pos;

	public CollectExperienceMessage() {
	}

	public CollectExperienceMessage(BlockPos pos) {
		this.pos = pos;
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		this.pos = BlockPos.fromLong(buf.readLong());
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeLong(this.pos.toLong());
	}

	public static class Handler implements IMessageHandler<CollectExperienceMessage, IMessage> {
		@Override
		public IMessage onMessage(final CollectExperienceMessage message, final MessageContext context) {
			final EntityPlayerMP player = context.getServerHandler().player;
			player.getServerWorld().addScheduledTask(new Runnable() {
				@Override
				public void run() {
					if (message.pos == null || player.getDistanceSq(message.pos) > 64.0D) return;
					TileEntity tileEntity = player.world.getTileEntity(message.pos);
					if (tileEntity instanceof TileEntityMobFarm) {
						((TileEntityMobFarm) tileEntity).collectExperience(player);
					}
				}
			});
			return null;
		}
	}
}
