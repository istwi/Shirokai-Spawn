package dev.shirokai.spawn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class StorageTest {
 @TempDir Path directory;
 @Test void survivesReplacementAndRestart() throws Exception {
  Path file=directory.resolve("players.json"); var data=new Storage.Players();
  UUID old=UUID.randomUUID(),fresh=UUID.randomUUID(),reset=UUID.randomUUID();
  data.players.add(old);data.pending.add(fresh);data.reset.add(reset);Storage.save(file,data);
  var loaded=Storage.read(file,Storage.Players.class);loaded.validate();
  assertTrue(loaded.players.contains(old));assertTrue(loaded.pending.contains(fresh));assertTrue(loaded.reset.contains(reset));
  loaded.pending.remove(fresh);loaded.players.add(fresh);Storage.save(file,loaded);
  var restarted=Storage.read(file,Storage.Players.class);
  assertTrue(restarted.players.contains(old));assertTrue(restarted.players.contains(fresh));assertTrue(restarted.pending.isEmpty());
  try(var files=Files.list(directory)){assertEquals(1,files.count());}
 }
 @Test void rejectsCorruptedStoreWithoutOverwriting() throws Exception {
  Path file=directory.resolve("players.json"); Files.writeString(file,"{broken");
  assertThrows(java.io.IOException.class,()->Storage.read(file,Storage.Players.class));
  assertEquals("{broken",Files.readString(file));
 }
 @Test void supportsOriginalPlayersOnlySchema() throws Exception {
  Path file=directory.resolve("players.json"); UUID id=UUID.randomUUID();
  Files.writeString(file,"{\"players\":[\""+id+"\"]}");var loaded=Storage.read(file,Storage.Players.class);loaded.validate();
  assertTrue(loaded.players.contains(id));assertTrue(loaded.pending.isEmpty());assertTrue(loaded.reset.isEmpty());
 }
 @Test void rejectsNullSets() {var data=new Storage.Players();data.players=null;assertThrows(java.io.IOException.class,data::validate);}
 @Test void oldConfigurationHasNoExplorationDestination() throws Exception {
  Path file=directory.resolve("config.json");
  Files.writeString(file,"{\"dimension\":\"multiworld:lobby\",\"x\":10.5,\"y\":81,\"z\":30.5}");
  var config=Storage.read(file,Storage.Config.class);config.validate();
  assertNull(config.explorar);assertEquals("multiworld:lobby",config.dimension);assertEquals(10.5,config.x);
 }
 @Test void explorationDestinationPersistsIndependently() throws Exception {
  Path file=directory.resolve("config.json");var config=new Storage.Config();
  config.explorar=new Storage.Destination("multiworld:exploration",200.5,81,200.5,45,10);
  config.validate();Storage.save(file,config);
  var loaded=Storage.read(file,Storage.Config.class);loaded.validate();
  assertEquals("minecraft:overworld",loaded.dimension);assertEquals(0.5,loaded.x);
  assertEquals("multiworld:exploration",loaded.explorar.dimension);assertEquals(200.5,loaded.explorar.x);
  assertEquals(81,loaded.explorar.y);assertEquals(200.5,loaded.explorar.z);
  assertEquals(45,loaded.explorar.yaw);assertEquals(10,loaded.explorar.pitch);
 }
 @Test void invalidExplorationDestinationIsRejected() {
  for(var destination:java.util.List.of(
   new Storage.Destination(null,0,80,0,0,0),
   new Storage.Destination("invalid world",0,80,0,0,0),
   new Storage.Destination("minecraft:overworld",Double.NaN,80,0,0,0),
   new Storage.Destination("minecraft:overworld",0,80,0,0,91))) {
   var config=new Storage.Config();config.explorar=destination;
   assertThrows(java.io.IOException.class,config::validate);
  }
 }
}
