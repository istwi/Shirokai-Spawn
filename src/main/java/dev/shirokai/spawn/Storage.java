package dev.shirokai.spawn;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.*;
final class Storage {
 static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
 static void save(Path path, Object value) throws IOException {
  Files.createDirectories(path.getParent());
  Path temp=Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
  try {
   try(var ch=FileChannel.open(temp, StandardOpenOption.WRITE)) {
    ByteBuffer b=StandardCharsets.UTF_8.encode(JSON.toJson(value)); while(b.hasRemaining()) ch.write(b); ch.force(true);
   }
   try { Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
   catch(AtomicMoveNotSupportedException e) { Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING); }
  } finally { Files.deleteIfExists(temp); }
 }
 static <T> T read(Path path, Class<T> type) throws IOException {
  try(var reader=Files.newBufferedReader(path)) { T v=JSON.fromJson(reader,type); if(v==null) throw new IOException("JSON vazio"); return v; }
  catch(JsonParseException e) { throw new IOException("JSON inválido: "+path,e); }
 }
 static class Config {
  boolean enabled=true;
  String dimension="minecraft:overworld";
  double x=0.5,y=80,z=0.5;
  float yaw=0,pitch=0;
  boolean firstJoinOnly=true;
  int teleportDelayTicks=1;
  boolean protectExistingPlayers=true,teleportOnRespawn=false,overridePersonalRespawn=false;
  void validate() throws IOException {
   if(net.minecraft.util.Identifier.tryParse(dimension)==null || !Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Float.isFinite(yaw)||!Float.isFinite(pitch)||Math.abs(x)>29999984||Math.abs(z)>29999984||pitch < -90||pitch>90||teleportDelayTicks<0||teleportDelayTicks>1200)
    throw new IOException("Configuração inválida; delay permitido: 0..1200");
   if(!firstJoinOnly || !protectExistingPlayers) throw new IOException("firstJoinOnly e protectExistingPlayers devem ser true para proteger jogadores antigos");
  }
 }
 static class Players {
  Set<UUID> players=new HashSet<>();
  Set<UUID> pending=new HashSet<>();
  Set<UUID> reset=new HashSet<>();
  void validate() throws IOException { if(players==null||pending==null||reset==null||players.contains(null)||pending.contains(null)||reset.contains(null)) throw new IOException("Registro inválido"); }
 }
}
