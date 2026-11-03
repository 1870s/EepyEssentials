package at.eepy.essentials.core;

import at.eepy.essentials.config.Settings;
import at.eepy.essentials.integrations.LuckPermsHook;
import at.eepy.essentials.integrations.PermissionChanges;
import at.eepy.essentials.text.Formatter;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * Everything a module needs when it starts.
 *
 * @param settings          the module's own file (modules/&lt;id&gt;.yml)
 * @param luckPerms         LuckPerms hook, {@code null} when LuckPerms is missing or disabled in config.yml
 * @param permissionChanges notifications when a player's groups change
 */
public record ModuleContext(Plugin plugin, Formatter formatter, Settings settings, @Nullable LuckPermsHook luckPerms,
                            PermissionChanges permissionChanges) {
}
