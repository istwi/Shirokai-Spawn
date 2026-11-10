package dev.shirokai.spawn;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.text.Text;
import me.lucko.fabric.api.permissions.v0.Permissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import static net.minecraft.server.command.CommandManager.*;

public final class ShirokaiSpawn implements ModInitializer {
 public static ShirokaiSpawn INSTANCE;
 private static final Logger LOG=LoggerFactory.getLogger("shirokaispawn");
 private Storage.Config config;
 private Storage.Players data;
 private Path configFile,playersFile,playerDir;
 private boolean healthy;
 private ScheduledExecutorService timer;
 private final Map<UUID, ScheduledFuture<?>> tasks=new HashMap<>();
 private final Map<UUID, UUID> sessions=new HashMap<>();
 @Override public void onInitialize() {
  INSTANCE=this;
  configFile=FabricLoader.getInstance().getConfigDir().resolve("shirokaispawn.json");
  ServerLifecycleEvents.SERVER_STARTED.register(this::start);
  ServerLifecycleEvents.SERVER_STOPPING.register(s->{ if(timer!=null) timer.shutdownNow(); tasks.clear(); sessions.clear(); healthy=false; });
  ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->schedule(handler.player,false));
  ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->cancel(handler.player.getUuid()));
  ServerPlayerEvents.AFTER_RESPAWN.register((old,p,alive)->{
   if(healthy && config.teleportOnRespawn && !alive && (config.overridePersonalRespawn || p.getSpawnPointPosition()==null)) schedule(p,true);
  });
  CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->{
   dispatcher.register(literal("explorar").executes(c->explore(c.getSource())));
   var root=literal("shirokaispawn").requires(s->Permissions.check(s,"shirokaispawn.admin",2));
   root.then(literal("set").executes(c->command(c.getSource(),"set")));
   root.then(literal("setexplorar").executes(c->command(c.getSource(),"setexplorar")));
   root.then(literal("info").executes(c->command(c.getSource(),"info")));
   root.then(literal("reload").executes(c->command(c.getSource(),"reload")));
   root.then(literal("test").executes(c->command(c.getSource(),"test")));
   for(String action:List.of("reset","setplayer")) root.then(literal(action).then(argument("jogador",GameProfileArgumentType.gameProfile()).executes(c->{
    if(!healthy) { c.getSource().sendError(Text.literal("Registro indisponível. Consulte o log e reinicie após corrigir.")); return 0; }
    var oldPlayers=new HashSet<>(data.players); var oldPending=new HashSet<>(data.pending); var oldReset=new HashSet<>(data.reset);
    var profiles=GameProfileArgumentType.getProfileArgument(c,"jogador");
    try {
     for(var profile:profiles) {
      UUID id=profile.getId(); cancel(id); data.pending.remove(id);
      if(action.equals("reset")) {data.players.remove(id);data.reset.add(id);} else {data.players.add(id);data.reset.remove(id);}
     }
     save(); c.getSource().sendFeedback(()->Text.literal("Registro atualizado: "+action),true); return profiles.size();
    } catch(IOException e) {data.players=oldPlayers;data.pending=oldPending;data.reset=oldReset; fail(e);c.getSource().sendError(Text.literal("Falha ao salvar registro; teleporte automático desativado."));return 0;}
   })));
   dispatcher.register(root);
  });
 }
 private void start(MinecraftServer server) {
  healthy=false; timer=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"shirokaispawn-delay");t.setDaemon(true);return t;});
  playerDir=server.getSavePath(WorldSavePath.PLAYERDATA);
  playersFile=server.getSavePath(WorldSavePath.ROOT).resolve("shirokaispawn/players.json");
  try {
   if(!Files.exists(configFile)) Storage.save(configFile,new Storage.Config());
   reload();
   data=Files.exists(playersFile)?Storage.read(playersFile,Storage.Players.class):new Storage.Players(); data.validate();
   if(Files.exists(playerDir)) try(var files=Files.list(playerDir)) {
    for(Path path:files.toList()) {
     String n=path.getFileName().toString();
     if(n.endsWith(".dat")||n.endsWith(".dat_old")) {
      String raw=n.substring(0,n.indexOf('.'));
      try { UUID id=UUID.fromString(raw); if(!data.pending.contains(id)&&!data.reset.contains(id)) data.players.add(id); }
      catch(IllegalArgumentException e) {throw new IOException("Arquivo de jogador não reconhecido: "+n,e);}
     }
    }
   }
   save(); healthy=true;
   LOG.info("Shirokai Spawn iniciado; {} UUIDs protegidos/processados",data.players.size());
  } catch(Exception e) {fail(e);}
 }
 private void reload() throws IOException {Storage.Config next=Storage.read(configFile,Storage.Config.class);next.validate();config=next;}
 private void save() throws IOException {Storage.save(playersFile,data);}
 private void fail(Exception e) {healthy=false;LOG.error("Teleporte automático desativado para proteger jogadores; corrija os arquivos e reinicie",e);}
 // Executado ANTES de o fluxo de conexão poder salvar os dados de um jogador novo.
 public void classify(ServerPlayerEntity p) {
  if(!healthy) return;
  UUID id=p.getUuid(); if(data.players.contains(id)||data.pending.contains(id)||data.reset.contains(id)) return;
  try {
   boolean existing=false;
   for(String suffix:List.of(".dat",".dat_old")) {
    Path file=playerDir.resolve(id+suffix);
    if(Files.exists(file)) existing=true;
    else if(!Files.notExists(file)) throw new IOException("Não foi possível determinar se jogador é novo: "+id);
   }
   if(existing) data.players.add(id); else data.pending.add(id);
   save();
  } catch(Exception e) {fail(e);}
 }
 private void cancel(UUID id) {sessions.remove(id);var f=tasks.remove(id);if(f!=null) f.cancel(false);}
 private void schedule(ServerPlayerEntity p,boolean respawn) {
  if(!healthy || !config.enabled) return;
  UUID id=p.getUuid();
  if(!respawn && (data.players.contains(id)||(!data.pending.contains(id)&&!data.reset.contains(id)))) return;
  cancel(id); UUID token=UUID.randomUUID();sessions.put(id,token);
  MinecraftServer server=p.getServer();
  tasks.put(id,timer.schedule(()->server.execute(()->{
   if(!token.equals(sessions.get(id))) return;
   tasks.remove(id);sessions.remove(id);
   if(!healthy||!config.enabled||server.getPlayerManager().getPlayer(id)!=p||p.isDisconnected()) return;
   if(!respawn&&data.players.contains(id)) return;
   if(teleport(p)) {
    if(!respawn) {data.players.add(id);data.pending.remove(id);data.reset.remove(id);try {save();}catch(IOException e){fail(e);}}
   }
  }), Math.max(1,config.teleportDelayTicks)*50L,TimeUnit.MILLISECONDS));
 }
 private int explore(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
  var player=source.getPlayerOrThrow();
  if(config==null||config.explorar==null) {
   source.sendError(Text.literal("O destino de exploração ainda não foi configurado pelo administrador."));return 0;
  }
  if(!teleport(player,config.explorar)) {
   source.sendError(Text.literal("Não foi possível explorar: o destino está indisponível ou inseguro. Avise um administrador."));return 0;
  }
  source.sendFeedback(()->Text.literal("Você chegou ao destino de exploração!"),false);return 1;
 }
 private boolean teleport(ServerPlayerEntity p) {
  return teleport(p,new Storage.Destination(config.dimension,config.x,config.y,config.z,config.yaw,config.pitch));
 }
 private boolean teleport(ServerPlayerEntity p,Storage.Destination destination) {
  ServerWorld world=p.getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD,Identifier.of(destination.dimension)));
  if(world==null) {LOG.warn("Destino indisponível: {}; jogador {} não foi teleportado",destination.dimension,p.getUuid());return false;}
  try {
   double x=destination.x,y=destination.y,z=destination.z;
   BlockPos feet=BlockPos.ofFloored(x,y,z);
   world.getChunk(feet);
   Box body=new Box(x-0.3,y,z-0.3,x+0.3,y+1.8,z+0.3);
   if(y<world.getBottomY()+1||y+1.8>=world.getTopY()||!world.getWorldBorder().contains(body)||!world.isSpaceEmpty(p,body)||!world.getBlockState(feet.down()).isSideSolidFullSquare(world,feet.down(),net.minecraft.util.math.Direction.UP)) return unsafe(p);
   for(BlockPos pos:BlockPos.iterate(BlockPos.ofFloored(x-0.3,y-0.01,z-0.3),BlockPos.ofFloored(x+0.3,y+1.8,z+0.3))) {
    var state=world.getBlockState(pos);
    if(!state.getFluidState().isEmpty()||state.isOf(net.minecraft.block.Blocks.FIRE)||state.isOf(net.minecraft.block.Blocks.SOUL_FIRE)||state.isOf(net.minecraft.block.Blocks.MAGMA_BLOCK)||state.isOf(net.minecraft.block.Blocks.CACTUS)||state.isOf(net.minecraft.block.Blocks.CAMPFIRE)||state.isOf(net.minecraft.block.Blocks.SOUL_CAMPFIRE)||state.isOf(net.minecraft.block.Blocks.SWEET_BERRY_BUSH)||state.isOf(net.minecraft.block.Blocks.POWDER_SNOW)||state.isOf(net.minecraft.block.Blocks.WITHER_ROSE)||state.isOf(net.minecraft.block.Blocks.END_PORTAL)||state.isOf(net.minecraft.block.Blocks.NETHER_PORTAL)) return unsafe(p);
   }
   p.teleport(world,x,y,z,destination.yaw,destination.pitch);p.fallDistance=0;
   boolean ok=p.getServerWorld()==world && p.squaredDistanceTo(x,y,z)<0.01;
   if(ok) LOG.info("Teleporte concluído para {} em {}",p.getUuid(),destination.dimension);
   return ok;
  } catch(Exception e) {LOG.error("Falha no teleporte de {}",p.getUuid(),e);return false;}
 }
 private boolean unsafe(ServerPlayerEntity p) {LOG.warn("Destino inseguro; teleporte cancelado para {}",p.getUuid());return false;}
 private int command(ServerCommandSource s,String action) {
  try {
   if(action.equals("reload")) {reload();s.sendFeedback(()->Text.literal("Configuração recarregada."),true);return 1;}
   if(config==null) throw new IOException("Configuração indisponível");
   switch(action) {
    case "info" -> s.sendFeedback(()->Text.literal(config.dimension+" XYZ: "+config.x+", "+config.y+", "+config.z+" yaw="+config.yaw+" pitch="+config.pitch),false);
    case "setexplorar" -> {
     var p=s.getPlayerOrThrow();
     Storage.Config next=Storage.JSON.fromJson(Storage.JSON.toJson(config),Storage.Config.class);
     next.explorar=new Storage.Destination(p.getServerWorld().getRegistryKey().getValue().toString(),p.getX(),p.getY(),p.getZ(),p.getYaw(),p.getPitch());
     next.validate();Storage.save(configFile,next);config=next;
     s.sendFeedback(()->Text.literal("Destino de /explorar salvo em "+config.explorar.dimension+" XYZ: "+config.explorar.x+", "+config.explorar.y+", "+config.explorar.z),true);
    }
    case "set" -> {var p=s.getPlayerOrThrow();Storage.Config next=Storage.JSON.fromJson(Storage.JSON.toJson(config),Storage.Config.class);next.dimension=p.getServerWorld().getRegistryKey().getValue().toString();next.x=p.getX();next.y=p.getY();next.z=p.getZ();next.yaw=p.getYaw();next.pitch=p.getPitch();next.validate();Storage.save(configFile,next);config=next;s.sendFeedback(()->Text.literal("Spawn inicial salvo em "+config.dimension),true);}
    case "test" -> {if(!teleport(s.getPlayerOrThrow())) {s.sendError(Text.literal("Teleporte cancelado: dimensão indisponível ou destino inseguro. Consulte o log."));return 0;}}
   }
   return 1;
  } catch(Exception e) {s.sendError(Text.literal("Erro: "+e.getMessage()));LOG.error("Falha no comando {}",action,e);return 0;}
 }
}
