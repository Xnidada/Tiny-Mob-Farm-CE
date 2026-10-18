package cn.davidma.tinymobfarm.core;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.Config.Comment;
import net.minecraftforge.common.config.Config.Name;
import net.minecraftforge.common.config.Config.RangeInt;
import net.minecraftforge.common.config.Config.Type;
import net.minecraftforge.common.config.ConfigManager;

@Config(modid = Reference.MOD_ID, name = Reference.MOD_ID)
public class ConfigTinyMobFarm {

	public static void syncConfig() {
		ConfigManager.sync(Reference.MOD_ID, Type.INSTANCE);
	}

	@Name("Lasso Durability")
	@Comment({"套索的耐久度。", "Durability of the lasso."})
	public static int LASSO_DURABILITY = 256;

	@Name("Lasso Maximum Mob Health")
	@Comment({"生物最大生命值大于等于此值时无法被套索捕捉。默认 50。", "Mobs with maximum health greater than or equal to this value cannot be captured by a lasso. Default: 50."})
	public static double LASSO_MAX_MOB_HEALTH = 50.0;

	@Name("Lasso Capture Health Limit")
	@Comment({"套索捕捉生物时，生物当前生命值必须小于等于此值。默认 5。", "When capturing a mob, its current health must be less than or equal to this value. Default: 5."})
	public static double LASSO_CAPTURE_HEALTH_LIMIT = 5.0;

	@Name("Lasso Durability Damage")
	@Comment({"各等级农场每轮产出消耗的套索耐久配置，顺序为木、石、铁、金、钻石、绿宝石、炼狱、终极；格式为“耐久点数=概率”，多个结果用逗号分隔。", "Lasso durability damage per production cycle for wood, stone, iron, gold, diamond, emerald, infernal, and ultimate farms; format: damage=chance, separated by commas."})
	public static String[] LASSO_DURABILITY_DAMAGE = {
		"2=33,3=67",
		"1=33,2=34,3=33",
		"1=50,2=50",
		"1=67,2=33",
		"1=100",
		"0=33,1=67",
		"0=67,1=33",
		"0=100"
	};
	
	@Name("Mob Farm Rate")
	@Comment({"每个等级农场生成一次掉落所需的秒数。", "Seconds required for each farm tier to generate loot once."})
	public static double[] MOB_FARM_SPEED = {50.0, 40.0, 30.0, 20.0, 10.0, 5.0, 2.5, 0.5};

	@Comment({"是否在客户端渲染农场内部的生物模型；当视野内有大量农场时可关闭以提升帧率。", "Whether to render captured mob models inside mob farms on the client; disable this to improve FPS when many farms are visible."})
	@Name("Render Farm Mob Model")
	public static boolean RENDER_FARM_MOB_MODEL = true;

	@Comment({"启用后，如果本轮掉落无法完整放入相邻物品容器，农场会暂停在完成前；不会扣除套索耐久，也不会把产物掉到世界中。", "When enabled, farms pause before completing if the generated drops cannot fully fit into adjacent item handlers; no lasso durability is consumed and no items are dropped into the world."})
	@Name("Pause When Output Full")
	public static boolean PAUSE_WHEN_OUTPUT_FULL = true;

	@Name("Output Retry Interval Ticks")
	@Comment({"输出失败后的重试间隔，单位 tick。默认 20。范围 1 到 3600。", "Retry interval after output fails, in ticks. Default 20. Range 1 to 3600."})
	@RangeInt(min = 1, max = 3600)
	public static int OUTPUT_RETRY_INTERVAL_TICKS = 20;
	
	@Comment({"禁止被套索捕捉的生物注册名列表，例如 minecraft:cow。", "Blacklist of mobs that cannot be captured, e.g. minecraft:cow."})
	@Name("Mob Blacklist")
	public static String[] MOB_BLACKLIST = {};
}
