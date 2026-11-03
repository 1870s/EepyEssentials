package at.eepy.essentials.modules.deaths;

import at.eepy.essentials.config.Settings;
import at.eepy.essentials.core.AbstractModule;
import at.eepy.essentials.text.Formatter;
import at.eepy.essentials.text.SoundSpec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * EepyDeaths: adds a symbol and colours to the vanilla death message and tells
 * the player privately where they died.
 * <p>
 * The vanilla message is kept as a translatable component, so the cause,
 * killer and weapon stay exact and every player reads it in their own
 * language. Inventory, XP, drops and respawn are not touched. If vanilla
 * would not show a message (game rule showDeathMessages=false or another
 * plugin removed it), none is shown here either.
 */
public final class DeathsModule extends AbstractModule {

    enum Category { PVP, MOB, ENVIRONMENT }

    private boolean publicMessages;
    private final Map<Category, String> formats = new EnumMap<>(Category.class);
    private TextColor messageColor;
    private TextColor nameColor;
    private final Set<String> hiddenTypes = new HashSet<>();
    private boolean coordinates;
    private List<String> coordinateLines;
    private boolean clickable;
    private String coordinateActionbar;
    private @Nullable SoundSpec sound;
    private String header;
    private String headerOther;
    private String none;
    private final Map<World.Environment, String> worldNames = new EnumMap<>(World.Environment.class);

    @Override
    public String id() {
        return "deaths";
    }

    @Override
    public String title() {
        return "EepyDeaths";
    }

    @Override
    public String description() {
        return "Styled death messages and private death coordinates";
    }

    @Override
    protected void start() {
        Settings settings = context.settings();
        publicMessages = settings.bool("public", true);
        Settings formatSection = settings.section("formats");
        formats.clear();
        for (Category category : Category.values()) {
            formats.put(category, formatSection.string(category.name().toLowerCase(Locale.ROOT), "<message>"));
        }
        messageColor = Objects.requireNonNull(settings.color("message-color", TextColor.color(0xDCD3F5)));
        nameColor = Objects.requireNonNull(settings.color("name-color", TextColor.color(0xF3EEFF)));
        hiddenTypes.clear();
        settings.stringList("hidden-damage-types").forEach(type -> hiddenTypes.add(type.toLowerCase(Locale.ROOT)));
        Settings coords = settings.section("coordinates");
        coordinates = coords.bool("enabled", true);
        coordinateLines = coords.stringList("chat");
        clickable = coords.bool("clickable", true);
        coordinateActionbar = coords.string("actionbar", "");
        sound = coords.sound("sound");
        Settings lastDeath = settings.section("last-death");
        header = lastDeath.string("header", "");
        headerOther = lastDeath.string("header-other", "");
        none = lastDeath.string("none", "");
        Settings names = settings.section("world-names");
        worldNames.clear();
        for (World.Environment environment : World.Environment.values()) {
            worldNames.put(environment, names.string(environment.name(), environment.name()));
        }
        listen();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        Component original = event.deathMessage();
        DamageSource source = event.getDamageSource();
        if (original != null) {
            String type = source.getDamageType().getKey().asString();
            if (!publicMessages || hiddenTypes.contains(type)) {
                event.deathMessage(null);
            } else {
                event.deathMessage(context.formatter().parse(formats.get(category(player, source)),
                        Placeholder.component("message", restyle(original)),
                        Placeholder.unparsed("player", player.getName())));
            }
        }
        if (coordinates) {
            Location location = player.getLocation();
            sendLocation(player, location, coordinateLines);
            context.formatter().actionBar(player, coordinateActionbar, locationTags(location));
            Formatter.play(player, sound);
        }
    }

    static Category category(Player victim, DamageSource source) {
        Entity causing = source.getCausingEntity();
        if (causing instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            causing = shooter;
        }
        if (causing instanceof Player killer && !killer.equals(victim)) {
            return Category.PVP;
        }
        return causing != null && !causing.equals(victim) ? Category.MOB : Category.ENVIRONMENT;
    }

    /** Colours the text and names of the vanilla message, keeping its translation and arguments. */
    Component restyle(Component message) {
        if (message instanceof TranslatableComponent translatable) {
            List<TranslationArgument> arguments = new ArrayList<>();
            for (TranslationArgument argument : translatable.arguments()) {
                arguments.add(argument.value() instanceof Component component
                        ? TranslationArgument.component(component.colorIfAbsent(nameColor))
                        : argument);
            }
            message = translatable.arguments(arguments);
        }
        return message.colorIfAbsent(messageColor);
    }

    // ── coordinates ───────────────────────────────────────────────────────

    /** /lastdeath: uses the position Minecraft stores itself (also used by the recovery compass). */
    public void showLastDeath(CommandSender viewer, Player target) {
        Location location = target.getLastDeathLocation();
        if (location == null || location.getWorld() == null) {
            context.formatter().send(viewer, none);
            return;
        }
        String head = viewer.equals(target) ? header : headerOther;
        context.formatter().send(viewer, head, Placeholder.unparsed("player", target.getName()));
        sendLocation(viewer, location, coordinateLines);
    }

    private void sendLocation(CommandSender receiver, Location location, List<String> lines) {
        TagResolver tags = locationTags(location);
        for (String line : lines) {
            Component component = context.formatter().parse(line, tags);
            if (clickable) {
                String coords = location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
                component = component.clickEvent(ClickEvent.copyToClipboard(coords))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to copy " + coords, context.formatter().color("muted"))));
            }
            receiver.sendMessage(component);
        }
    }

    private TagResolver locationTags(Location location) {
        World world = location.getWorld();
        return TagResolver.resolver(
                Placeholder.unparsed("x", Integer.toString(location.getBlockX())),
                Placeholder.unparsed("y", Integer.toString(location.getBlockY())),
                Placeholder.unparsed("z", Integer.toString(location.getBlockZ())),
                Placeholder.unparsed("world", world == null ? "?" : worldNames.get(world.getEnvironment())),
                Placeholder.unparsed("world_name", world == null ? "?" : world.getName()));
    }

    @Override
    public String status() {
        return "public " + (publicMessages ? "on" : "off") + " · " + hiddenTypes.size() + " hidden types · coordinates "
                + (coordinates ? "on" : "off");
    }
}
