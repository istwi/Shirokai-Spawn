package dev.shirokai.spawn.mixin;
import dev.shirokai.spawn.ShirokaiSpawn;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.network.ConnectedClientData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PlayerManager.class)
public class JoinMixin {
 @Inject(method="onPlayerConnect", at=@At("HEAD"))
 private void beforeJoin(ClientConnection connection, ServerPlayerEntity player, ConnectedClientData data, CallbackInfo ci) {
  ShirokaiSpawn.INSTANCE.classify(player);
 }
}
