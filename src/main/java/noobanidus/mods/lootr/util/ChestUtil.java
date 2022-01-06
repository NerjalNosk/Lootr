package noobanidus.mods.lootr.util;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraftforge.network.PacketDistributor;
import noobanidus.mods.lootr.api.blockentity.ILootTile;
import noobanidus.mods.lootr.block.LootrShulkerBlock;
import noobanidus.mods.lootr.block.entities.LootrInventoryBlockEntity;
import noobanidus.mods.lootr.config.ConfigManager;
import noobanidus.mods.lootr.data.DataStorage;
import noobanidus.mods.lootr.entity.LootrChestMinecartEntity;
import noobanidus.mods.lootr.init.ModAdvancements;
import noobanidus.mods.lootr.init.ModStats;
import noobanidus.mods.lootr.networking.CloseCart;
import noobanidus.mods.lootr.networking.PacketHandler;
import noobanidus.mods.lootr.networking.UpdateModelData;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("unused")
public class ChestUtil {
  public static Random random = new Random();
  public static Set<Class<?>> tileClasses = new HashSet<>();

  public static boolean handleLootSneak(Block block, Level world, BlockPos pos, Player player) {
    if (world.isClientSide()) {
      return false;
    }
    if (player.isSpectator()) {
      return false;
    }

    BlockEntity te = world.getBlockEntity(pos);
    if (te instanceof ILootTile tile) {
      if (tile.getOpeners().remove(player.getUUID())) {
        tile.updatePacketViaState();
        UpdateModelData message = new UpdateModelData(te.getBlockPos());
        PacketHandler.sendToInternal(message, (ServerPlayer) player);
      }
      return true;
    }

    return false;
  }

  public static void handleLootCartSneak(Level world, LootrChestMinecartEntity cart, Player player) {
    if (world.isClientSide()) {
      return;
    }

    if (player.isSpectator()) {
      return;
    }

    cart.getOpeners().remove(player.getUUID());
    CloseCart open = new CloseCart(cart.getId());
    PacketHandler.sendInternal(PacketDistributor.TRACKING_ENTITY.with(() -> cart), open);
  }

  public static boolean handleLootChest(Block block, Level world, BlockPos pos, Player player) {
    if (world.isClientSide()) {
      return false;
    }
    if (player.isSpectator()) {
      player.openMenu(null);
      return false;
    }
    BlockEntity te = world.getBlockEntity(pos);
    if (te instanceof ILootTile tile) {
      UUID tileId = tile.getTileId();
      if (DataStorage.isDecayed(tileId)) {
        world.destroyBlock(pos, true);
        player.sendMessage(new TranslatableComponent("lootr.message.decayed").setStyle(Style.EMPTY.withColor(TextColor.fromLegacyFormat(ChatFormatting.RED)).withBold(true)), Util.NIL_UUID);
        return false;
      } else {
        int decayValue = DataStorage.getDecayValue(tileId);
        if (decayValue > 0) {
          player.sendMessage(new TranslatableComponent("lootr.message.decay_in", decayValue / 20).setStyle(Style.EMPTY.withColor(TextColor.fromLegacyFormat(ChatFormatting.RED)).withBold(true)), Util.NIL_UUID);
        } else if (decayValue == -1) {
          if (ConfigManager.isDecaying(world, (ILootTile)te)) {
            DataStorage.setDecaying(tileId, ConfigManager.DECAY_VALUE.get());
            player.sendMessage(new TranslatableComponent("lootr.message.decay_start", ConfigManager.DECAY_VALUE.get() / 20).setStyle(Style.EMPTY.withColor(TextColor.fromLegacyFormat(ChatFormatting.RED)).withBold(true)), Util.NIL_UUID);
          }
        }
      }
      if (block instanceof BarrelBlock) {
        ModAdvancements.BARREL_PREDICATE.trigger((ServerPlayer) player, ((ILootTile) te).getTileId());
      } else if (block instanceof ChestBlock) {
        ModAdvancements.CHEST_PREDICATE.trigger((ServerPlayer) player, ((ILootTile) te).getTileId());
      } else if (block instanceof LootrShulkerBlock) {
        ModAdvancements.SHULKER_PREDICATE.trigger((ServerPlayer) player, ((ILootTile) te).getTileId());
      }
      MenuProvider provider = DataStorage.getInventory(world, ((ILootTile) te).getTileId(), pos, (ServerPlayer) player, (RandomizableContainerBlockEntity) te, ((ILootTile) te)::unpackLootTable);
      if (!DataStorage.isScored(player.getUUID(), ((ILootTile)te).getTileId())) {
        player.awardStat(ModStats.LOOTED_STAT);
        ModAdvancements.SCORE_PREDICATE.trigger((ServerPlayer) player, null);
        DataStorage.score(player.getUUID(), ((ILootTile) te).getTileId());
      }
      if (tile.getOpeners().add(player.getUUID())) {
        tile.updatePacketViaState();
      }
      player.openMenu(provider);
      PiglinAi.angerNearbyPiglins(player, true);
      return true;
    } else {
      return false;
    }
  }

  public static void handleLootCart(Level world, LootrChestMinecartEntity cart, Player player) {
    if (!world.isClientSide()) {
      if (player.isSpectator()) {
        player.openMenu(null);
      } else {
        ModAdvancements.CART_PREDICATE.trigger((ServerPlayer) player, cart.getUUID());
        UUID tileId = cart.getUUID();
        if (DataStorage.isDecayed(tileId)) {
          cart.destroy(DamageSource.OUT_OF_WORLD);
          player.sendMessage(new TranslatableComponent("lootr.message.decayed").setStyle(Style.EMPTY.withColor(TextColor.fromLegacyFormat(ChatFormatting.RED)).withBold(true)), Util.NIL_UUID);
          return;
        } else {
          int decayValue = DataStorage.getDecayValue(tileId);
          if (decayValue > 0) {
            player.sendMessage(new TranslatableComponent("lootr.message.decay_in", decayValue / 20).setStyle(Style.EMPTY.withColor(TextColor.fromLegacyFormat(ChatFormatting.RED)).withBold(true)), Util.NIL_UUID);
          } else if (decayValue == -1) {
            if (ConfigManager.isDecaying(world, cart)) {
              DataStorage.setDecaying(tileId, ConfigManager.DECAY_VALUE.get());
              player.sendMessage(new TranslatableComponent("lootr.message.decay_start", ConfigManager.DECAY_VALUE.get() / 20).setStyle(Style.EMPTY.withColor(TextColor.fromLegacyFormat(ChatFormatting.RED)).withBold(true)), Util.NIL_UUID);
            }
          }
        }
        if (!cart.getOpeners().contains(player.getUUID())) {
          cart.addOpener(player);
        }
        if (!DataStorage.isScored(player.getUUID(), cart.getUUID())) {
          player.awardStat(ModStats.LOOTED_STAT);
          ModAdvancements.SCORE_PREDICATE.trigger((ServerPlayer) player, null);
          DataStorage.score(player.getUUID(), cart.getUUID());
        }
        MenuProvider provider = DataStorage.getInventory(world, cart, (ServerPlayer) player, cart::addLoot);
        player.openMenu(provider);
      }
    }
  }

  public static boolean handleLootInventory(Block block, Level world, BlockPos pos, Player player) {
    if (world.isClientSide()) {
      return false;
    }
    if (player.isSpectator()) {
      player.openMenu(null);
      return false;
    }
    BlockEntity te = world.getBlockEntity(pos);
    if (te instanceof LootrInventoryBlockEntity tile) {
      ModAdvancements.CHEST_PREDICATE.trigger((ServerPlayer) player, tile.getTileId());
      NonNullList<ItemStack> stacks = null;
      if (tile.getCustomInventory() != null) {
        stacks = copyItemList(tile.getCustomInventory());
      }
      MenuProvider provider = DataStorage.getInventory(world, tile.getTileId(), stacks, (ServerPlayer) player, pos, tile);
      if (!DataStorage.isScored(player.getUUID(), ((ILootTile)te).getTileId())) {
        player.awardStat(ModStats.LOOTED_STAT);
        ModAdvancements.SCORE_PREDICATE.trigger((ServerPlayer) player, null);
        DataStorage.score(player.getUUID(), ((ILootTile) te).getTileId());
      }
      if (tile.getOpeners().add(player.getUUID())) {
        tile.updatePacketViaState();
      }
      player.openMenu(provider);
      PiglinAi.angerNearbyPiglins(player, true);
      return true;
    } else {
      return false;
    }
  }

  public static NonNullList<ItemStack> copyItemList(NonNullList<ItemStack> reference) {
    NonNullList<ItemStack> contents = NonNullList.withSize(reference.size(), ItemStack.EMPTY);
    for (int i = 0; i < reference.size(); i++) {
      contents.set(i, reference.get(i).copy());
    }
    return contents;
  }
}
