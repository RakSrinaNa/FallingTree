package fr.raksrinana.fallingtree.fabric;

import fr.raksrinana.fallingtree.fabric.config.Configuration;
import fr.raksrinana.fallingtree.fabric.config.validator.Validators;
import fr.raksrinana.fallingtree.fabric.leaves.LeafBreakingHandler;
import me.shedaniel.autoconfig.AutoConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import static java.util.stream.Collectors.toList;

public class FallingTree implements ModInitializer{
	public static final String MOD_ID = "fallingtree";
	public static final Logger logger = LogManager.getLogger(MOD_ID);
	public static Configuration config;
	
	@Override
	public void onInitialize(){
		config = Configuration.register();
		if(FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT){
			registerGui();
		}
		
		ServerTickEvents.END_SERVER_TICK.register(new LeafBreakingHandler());
		PlayerBlockBreakEvents.BEFORE.register(new BlockBreakHandler());
	}
	
	@Environment(EnvType.CLIENT)
	private static void registerGui(){
		var registry = AutoConfig.getGuiRegistry(Configuration.class);
		
		//noinspection unchecked
		Validators.RUNNERS.forEach(runner -> registry.registerAnnotationTransformer((guis, i13n, field, config, defaults, guiProvider) -> guis.stream()
				.peek(gui -> gui.setErrorSupplier(() -> runner.apply(gui.getValue(), field)))
				.collect(toList()), runner.getAnnotationClass()));
	}
}
