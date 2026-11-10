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
}
