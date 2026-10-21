package cn.davidma.tinymobfarm.common;

import java.util.Collection;
import java.util.EnumMap;

import cn.davidma.tinymobfarm.client.gui.HandlerGui;
import cn.davidma.tinymobfarm.common.block.BlockMobFarm;
import cn.davidma.tinymobfarm.common.tileentity.TileEntityMobFarm;
import cn.davidma.tinymobfarm.common.network.CollectExperienceMessage;
import cn.davidma.tinymobfarm.core.ConfigTinyMobFarm;
import cn.davidma.tinymobfarm.core.EnumMobFarm;
import cn.davidma.tinymobfarm.core.IProxy;
import cn.davidma.tinymobfarm.core.Reference;
import cn.davidma.tinymobfarm.core.util.ConfigReloadHandler;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.common.registry.GameRegistry;

@Mod(modid=Reference.MOD_ID, name=Reference.MOD_NAME, version=Reference.VERSION)
public class TinyMobFarm {
	
	@Instance
	public static TinyMobFarm instance;
	
	@SidedProxy(clientSide=Reference.CLIENT_PROXY, serverSide=Reference.SERVER_PROXY)
	public static IProxy proxy;
	
	public static CreativeTabs creativeTabTinyMobFarm = new CreativeTabTinyMobFarm();
	
	public static Item lasso;
	public static EnumMap<EnumMobFarm, BlockMobFarm> blockMobFarms;
	public static EnumMap<EnumMobFarm, BlockMobFarm> mechanicalBlockMobFarms;
	public static Collection<ItemBlock> itemBlockMobFarms;
	public static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(Reference.MOD_ID);

	public static BlockMobFarm getBlockMobFarm(EnumMobFarm mobFarmData, boolean mechanical) {
		return mechanical ? TinyMobFarm.mechanicalBlockMobFarms.get(mobFarmData) : TinyMobFarm.blockMobFarms.get(mobFarmData);
	}
	
	@EventHandler
	public static void preInit(FMLPreInitializationEvent event) {
		ConfigTinyMobFarm.syncConfig();
		MinecraftForge.EVENT_BUS.register(new ConfigReloadHandler());
		proxy.preInit();
	}
	
	@EventHandler
	public static void init(FMLInitializationEvent event) {
		GameRegistry.registerTileEntity(TileEntityMobFarm.class, new ResourceLocation(Reference.MOD_ID + ":mob_farm_tile_entity"));
		NETWORK.registerMessage(CollectExperienceMessage.Handler.class, CollectExperienceMessage.class, 0, Side.SERVER);
		NetworkRegistry.INSTANCE.registerGuiHandler(TinyMobFarm.instance, new HandlerGui());
		proxy.init();
	}
	
	@EventHandler
	public static void postInit(FMLPostInitializationEvent event) {
		proxy.postInit();
	}
}
