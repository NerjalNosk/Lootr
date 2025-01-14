package net.zestyblaze.lootr.advancement;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.zestyblaze.lootr.api.advancement.IGenericPredicate;
import net.zestyblaze.lootr.data.DataStorage;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class ContainerPredicate implements IGenericPredicate<UUID> {
    @Override
    public boolean test(ServerPlayer player, UUID condition) {
        if(DataStorage.isAwarded(player.getUUID(), condition)) {
            return false;
        } else {
            DataStorage.award(player.getUUID(), condition);
            return true;
        }
    }

    @Override
    public IGenericPredicate<UUID> deserialize(@Nullable JsonObject element) {
        return new ContainerPredicate();
    }
}
