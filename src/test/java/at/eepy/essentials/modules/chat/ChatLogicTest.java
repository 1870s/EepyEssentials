package at.eepy.essentials.modules.chat;

import at.eepy.essentials.TestFiles;
import at.eepy.essentials.config.Settings;
import at.eepy.essentials.integrations.LuckPermsHook.GroupInfo;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChatLogicTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static ChatRanks ranks(String prefixSource) {
        YamlConfiguration yaml = TestFiles.yaml("modules/chat.yml");
        yaml.set("ranks.prefix-source", prefixSource);
        return new ChatRanks(new Settings(yaml, "modules/chat.yml", new ArrayList<>()).section("ranks"), TestFiles.formatter());
    }

    @Test
    void antiSpamBlocksBurstsAndRepeatsOnly() {
        AntiSpam spam = new AntiSpam(1000, 15_000);
        UUID player = UUID.randomUUID();
        assertEquals(AntiSpam.Result.OK, spam.check(player, "hello", 0));
        assertEquals(AntiSpam.Result.TOO_FAST, spam.check(player, "again", 500));
        assertEquals(AntiSpam.Result.REPEATED, spam.check(player, "  HELLO ", 2000), "case and spaces ignored");
        assertEquals(AntiSpam.Result.OK, spam.check(player, "something new", 2500));
        assertEquals(AntiSpam.Result.OK, spam.check(player, "hello", 4000), "not the last message anymore");
        assertEquals(AntiSpam.Result.OK, spam.check(UUID.randomUUID(), "hello", 4000), "per player");
        assertEquals(AntiSpam.Result.OK, spam.check(player, "hello", 19_001), "allowed after the repeat window");
    }

    @Test
    void luckPermsGroupsMapToConfiguredRanks() {
        ChatRanks ranks = ranks("AUTO");
        ChatRanks.Rank owner = ranks.fromLuckPerms(new GroupInfo("owner", List.of("owner", "default"), "&c[LP Owner] ", null, 100, null));
        assertEquals("owner", owner.id());
        assertEquals("OWNER │ ", PLAIN.serialize(owner.prefix()), "config prefix, the LuckPerms prefix is not added too");

        ChatRanks.Rank inherited = ranks.fromLuckPerms(new GroupInfo("builder", List.of("builder", "vip", "default"), null, null, 30, null));
        assertEquals("vip", inherited.id(), "heaviest inherited group with a rank");
    }

    @Test
    void unknownGroupsUseTheLuckPermsPrefixInAutoMode() {
        ChatRanks.Rank auto = ranks("AUTO").fromLuckPerms(new GroupInfo("builder", List.of("builder"), "&6[Builder] ", null, 30, "Builder"));
        assertEquals("[Builder] ", PLAIN.serialize(auto.prefix()));
        assertEquals("Builder", auto.displayName());
        assertEquals(30, auto.priority());

        ChatRanks.Rank configOnly = ranks("CONFIG").fromLuckPerms(new GroupInfo("builder", List.of("builder"), "&6[Builder] ", null, 30, null));
        assertEquals("member", configOnly.id(), "CONFIG never shows LuckPerms prefixes");

        ChatRanks.Rank luckPerms = ranks("LUCKPERMS").fromLuckPerms(new GroupInfo("owner", List.of("owner"), "&c[LP Owner] ", "&7 ✦", 100, null));
        assertEquals("[LP Owner] ", PLAIN.serialize(luckPerms.prefix()), "LUCKPERMS uses only the LuckPerms prefix");
        assertEquals(" ✦", PLAIN.serialize(luckPerms.suffix()));
        assertEquals("owner", luckPerms.id(), "but keeps the rank's colours and priority");
    }
}
