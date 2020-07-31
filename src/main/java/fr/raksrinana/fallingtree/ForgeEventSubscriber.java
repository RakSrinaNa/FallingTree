package fr.raksrinana.fallingtree;

import fr.raksrinana.fallingtree.config.BreakMode;
import fr.raksrinana.fallingtree.config.Config;
import fr.raksrinana.fallingtree.tree.Tree;
import fr.raksrinana.fallingtree.tree.TreeHandler;
import fr.raksrinana.fallingtree.utils.CachedSpeed;
import fr.raksrinana.fallingtree.utils.LeafBreakingSchedule;
import io.netty.util.internal.ConcurrentSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import javax.annotation.Nonnull;
import java.util.*;
import static fr.raksrinana.fallingtree.utils.FallingTreeUtils.canPlayerBreakTree;
import static fr.raksrinana.fallingtree.utils.FallingTreeUtils.isLeafBlock;

@Mod.EventBusSubscriber(modid = FallingTree.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeEventSubscriber{
	private static final Set<LeafBreakingSchedule> scheduledLeavesBreaking = new ConcurrentSet<>();
	private static final Map<UUID, CachedSpeed> speedCache = new HashMap<>();
	
	@SubscribeEvent
	public static void onBreakSpeed(@Nonnull PlayerEvent.BreakSpeed event){
		if(!event.isCanceled()){
			if(Config.COMMON.getTreesConfiguration().getBreakMode() == BreakMode.INSTANTANEOUS){
				if(isPlayerInRightState(event.getPlayer())){
					CachedSpeed cachedSpeed = speedCache.compute(event.getPlayer().getUniqueID(), (pos, speed) -> {
						if(Objects.isNull(speed) || !speed.isValid(event.getPos())){
							speed = getSpeed(event);
						}
						return speed;
					});
					if(Objects.nonNull(cachedSpeed)){
						event.setNewSpeed(cachedSpeed.getSpeed());
					}
				}
			}
		}
	}
	
	private static CachedSpeed getSpeed(PlayerEvent.BreakSpeed event){
		double speedMultiplicand = Config.COMMON.getToolsConfiguration().getSpeedMultiplicand();
		return speedMultiplicand <= 0 ? null :
				TreeHandler.getTree(event.getEntity().getEntityWorld(), event.getPos())
						.map(tree -> new CachedSpeed(event.getPos(), event.getOriginalSpeed() / ((float) speedMultiplicand * tree.getLogCount())))
						.orElse(null);
	}
	
	@SubscribeEvent
	public static void onBlockBreakEvent(@Nonnull BlockEvent.BreakEvent event){
		if(!event.isCanceled() && !event.getWorld().isRemote()){
			if(isPlayerInRightState(event.getPlayer()) && event.getWorld() instanceof World){
				TreeHandler.getTree((World) event.getWorld(), event.getPos()).ifPresent(tree -> {
					BreakMode breakMode = Config.COMMON.getTreesConfiguration().getBreakMode();
					if(breakMode == BreakMode.INSTANTANEOUS){
						breakInstant(event, tree);
					}
					else if(breakMode == BreakMode.SHIFT_DOWN){
						breakShiftDown(event, tree);
					}
				});
			}
		}
	}
	
	private static void breakInstant(BlockEvent.BreakEvent event, Tree tree){
		if(Config.COMMON.getTreesConfiguration().getMaxSize() >= tree.getLogCount()){
			if(!TreeHandler.destroyInstant(tree, event.getPlayer(), event.getPlayer().getHeldItem(Hand.MAIN_HAND))){
				event.setCanceled(true);
			}
		}
		else{
			event.getPlayer().sendMessage(new TranslationTextComponent("chat.falling_tree.tree_too_big", tree.getLogCount(), Config.COMMON.getTreesConfiguration().getMaxSize()));
		}
	}
	
	private static void breakShiftDown(BlockEvent.BreakEvent event, Tree tree){
		TreeHandler.destroyShift(tree, event.getPlayer(), event.getPlayer().getHeldItem(Hand.MAIN_HAND));
		event.setCanceled(true);
	}
	
	private static boolean isPlayerInRightState(PlayerEntity player){
		if(player.abilities.isCreativeMode && !Config.COMMON.isBreakInCreative()){
			return false;
		}
		if(Config.COMMON.isReverseSneaking() != player.isSneaking()){
			return false;
		}
		return canPlayerBreakTree(player);
	}
	
	@SubscribeEvent
	public static void onNeighborNotifyEvent(BlockEvent.NeighborNotifyEvent event){
		if(Config.COMMON.getTreesConfiguration().isLavesBreaking() && !event.getWorld().isRemote()){
			ServerWorld world = (ServerWorld) event.getWorld();
			BlockState eventState = event.getState();
			Block eventBlock = eventState.getBlock();
			BlockPos eventPos = event.getPos();
			if(eventBlock.isAir(eventState, world, eventPos)){
				for(Direction facing : event.getNotifiedSides()){
					BlockPos neighborPos = eventPos.offset(facing);
					if(world.isBlockLoaded(neighborPos)){
						BlockState neighborState = event.getWorld().getBlockState(neighborPos);
						if(isLeafBlock(neighborState.getBlock())){
							scheduledLeavesBreaking.add(new LeafBreakingSchedule(world, neighborPos, 4));
						}
					}
				}
			}
		}
	}
	
	@SubscribeEvent
	public static void onServerTick(TickEvent.ServerTickEvent event){
		if(event.side == LogicalSide.SERVER && event.phase == TickEvent.Phase.END){
			Iterator<LeafBreakingSchedule> leavesBreak = scheduledLeavesBreaking.iterator();
			while(leavesBreak.hasNext()){
				LeafBreakingSchedule leafBreakingSchedule = leavesBreak.next();
				ServerWorld world = leafBreakingSchedule.getWorld();
				if(leafBreakingSchedule.getRemainingTicks() <= 0){
					if(world.isBlockLoaded(leafBreakingSchedule.getBlockPos())){
						BlockState state = world.getBlockState(leafBreakingSchedule.getBlockPos());
						Block block = state.getBlock();
						if(isLeafBlock(block)){
							block.randomTick(state, world, leafBreakingSchedule.getBlockPos(), world.getRandom());
						}
						else{
							leavesBreak.remove();
						}
					}
					else{
						leavesBreak.remove();
					}
				}
				else{
					leafBreakingSchedule.tick();
				}
			}
		}
	}
}
