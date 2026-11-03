package at.eepy.essentials.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class ModuleManagerTest {

    /** Records what happens to it; can be told to fail on enable. */
    static final class FakeModule implements Module {
        final String id;
        final boolean failOnEnable;
        final List<String> calls = new ArrayList<>();

        FakeModule(String id, boolean failOnEnable) {
            this.id = id;
            this.failOnEnable = failOnEnable;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String title() {
            return "Fake " + id;
        }

        @Override
        public String description() {
            return "test";
        }

        @Override
        public void enable(ModuleContext context) {
            calls.add("enable");
            if (failOnEnable) {
                throw new IllegalStateException("broken on purpose");
            }
        }

        @Override
        public void disable() {
            calls.add("disable");
        }
    }

    private static final Logger SILENT = Logger.getLogger("test");

    static {
        SILENT.setUseParentHandlers(false);
    }

    @Test
    void aFailingModuleDoesNotAffectTheOthers() {
        FakeModule ok = new FakeModule("ok", false);
        FakeModule broken = new FakeModule("broken", true);
        FakeModule off = new FakeModule("off", false);
        ModuleManager manager = new ModuleManager(SILENT, List.of(ok, broken, off));

        manager.apply(Map.of("ok", true, "broken", true, "off", false), module -> null);

        assertEquals(ModuleManager.State.ENABLED, manager.status("ok").state());
        assertEquals(ModuleManager.State.FAILED, manager.status("broken").state());
        assertEquals("broken on purpose", manager.status("broken").error());
        assertEquals(ModuleManager.State.DISABLED, manager.status("off").state());
        assertEquals(List.of("enable", "disable"), broken.calls, "a failed module is cleaned up right away");
        assertEquals(List.of(), off.calls, "a disabled module is never started");
        assertEquals(1, manager.enabledCount());
    }

    @Test
    void reloadRestartsAndStopsModules() {
        FakeModule module = new FakeModule("chat", false);
        ModuleManager manager = new ModuleManager(SILENT, List.of(module));

        manager.apply(Map.of("chat", true), m -> null);
        manager.apply(Map.of("chat", true), m -> null);
        manager.apply(Map.of("chat", false), m -> null);
        manager.apply(Map.of("chat", false), m -> null);

        assertEquals(List.of("enable", "disable", "enable", "disable"), module.calls);
        assertFalse(manager.isEnabled("chat"));
    }

    @Test
    void stopAllStopsOnlyRunningModules() {
        FakeModule running = new FakeModule("a", false);
        FakeModule idle = new FakeModule("b", false);
        ModuleManager manager = new ModuleManager(SILENT, List.of(running, idle));
        manager.apply(Map.of("a", true), m -> null);

        manager.stopAll();

        assertEquals(List.of("enable", "disable"), running.calls);
        assertEquals(List.of(), idle.calls);
        assertEquals(0, manager.enabledCount());
    }
}
