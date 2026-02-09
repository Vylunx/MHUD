package com.buuz135.mhud;

import com.hypixel.hytale.protocol.packets.interface_.CustomUICommand;
import com.hypixel.hytale.protocol.packets.interface_.CustomUICommandType;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class MultipleCustomUIHud extends CustomUIHud {

    /* ===============================
       CLIENT READY SAFETY (CRITICAL)
       =============================== */

    private static final Set<UUID> CLIENT_READY = ConcurrentHashMap.newKeySet();

    public static void markClientReady(@Nonnull PlayerRef playerRef) {
        CLIENT_READY.add(playerRef.getUuid());
    }

    public static void resetClientReady(@Nonnull PlayerRef playerRef) {
        CLIENT_READY.remove(playerRef.getUuid());
    }

    private boolean canSendUI() {
        return CLIENT_READY.contains(this.getPlayerRef().getUuid());
    }

    /* ===============================
       REFLECTION SETUP
       =============================== */

    private static Method BUILD_METHOD;
    private static Field COMMANDS_FIELD;

    static {
        try {
            BUILD_METHOD = CustomUIHud.class.getDeclaredMethod("build", UICommandBuilder.class);
            BUILD_METHOD.setAccessible(true);
        } catch (NoSuchMethodException e) {
            BUILD_METHOD = null;
            MultipleHUD.getInstance().getLogger().at(Level.SEVERE)
                    .log("Could not find method 'build' in CustomUIHud");
        }

        try {
            COMMANDS_FIELD = UICommandBuilder.class.getDeclaredField("commands");
            COMMANDS_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            COMMANDS_FIELD = null;
            MultipleHUD.getInstance().getLogger().at(Level.SEVERE)
                    .log("Could not find field 'commands' in UICommandBuilder");
        }
    }

    /* ===============================
       INTERNAL BUILDER
       =============================== */

    private static class PrefixedUICommandBuilder extends UICommandBuilder {

        private final List<CustomUICommand> wrappedCommands = new ObjectArrayList<>();
        private final String prefix;

        public PrefixedUICommandBuilder(@NonNullDecl String id) {
            this.prefix = "#MultipleHUD #" + id;
        }

        private void prefixCommands() throws IllegalAccessException {
            final List<CustomUICommand> commands =
                    (List<CustomUICommand>) COMMANDS_FIELD.get(this);

            for (CustomUICommand command : commands) {
                command.selector = command.selector == null
                        ? this.prefix
                        : this.prefix + " " + command.selector;
                wrappedCommands.add(command);
            }
            commands.clear();
        }

        @Override
        @Nonnull
        public CustomUICommand[] getCommands() {
            try {
                this.prefixCommands();
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            CustomUICommand[] result = wrappedCommands.toArray(new CustomUICommand[0]);
            wrappedCommands.clear();
            return result;
        }

        void appendCommandsTo(UICommandBuilder builder) throws IllegalAccessException {
            this.prefixCommands();
            final List<CustomUICommand> commands =
                    (List<CustomUICommand>) COMMANDS_FIELD.get(builder);
            commands.addAll(this.wrappedCommands);
        }

        void addCustomCommand(CustomUICommandType type, String selector, String document) {
            this.wrappedCommands.add(new CustomUICommand(type, selector, null, document));
        }
    }

    /* ===============================
       HUD LOGIC
       =============================== */

    private static void buildHud(
            @Nonnull UICommandBuilder uiCommandBuilder,
            @NonNullDecl String normalizedId,
            @Nonnull CustomUIHud hud,
            boolean hudExists
    ) {
        try {
            if (BUILD_METHOD == null || COMMANDS_FIELD == null) return;

            PrefixedUICommandBuilder builder = new PrefixedUICommandBuilder(normalizedId);

            if (hudExists) {
                builder.addCustomCommand(CustomUICommandType.Clear, builder.prefix, null);
            } else {
                builder.addCustomCommand(
                        CustomUICommandType.AppendInline,
                        "#MultipleHUD",
                        "Group #" + normalizedId + " {}"
                );
            }

            BUILD_METHOD.invoke(hud, builder);
            builder.appendCommandsTo(uiCommandBuilder);

        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private final HashMap<String, String> normalizedIds = new HashMap<>();
    private final HashMap<String, CustomUIHud> customHuds = new HashMap<>();

    public MultipleCustomUIHud(@NonNullDecl PlayerRef playerRef) {
        super(playerRef);
    }

    @Override
    protected void build(@NonNullDecl UICommandBuilder uiCommandBuilder) {
        uiCommandBuilder.append("HUD/MultipleHUD.ui");
    }

    @Override
    public void show() {
        if (!canSendUI()) return;

        UICommandBuilder builder = new UICommandBuilder();
        this.build(builder);

        for (String id : customHuds.keySet()) {
            buildHud(builder, normalizedIds.get(id), customHuds.get(id), false);
        }

        update(true, builder);
    }

    public void add(@NonNullDecl String identifier, @NonNullDecl CustomUIHud hud) {
        if (!canSendUI()) return;

        UICommandBuilder builder = new UICommandBuilder();
        String normalizedId = normalizedIds.computeIfAbsent(
                identifier,
                i -> i.replaceAll("[^a-zA-Z0-9]", "")
        );

        CustomUIHud existing = customHuds.put(identifier, hud);
        buildHud(builder, normalizedId, hud, existing != null);
        update(false, builder);
    }

    public void remove(@NonNullDecl String identifier) {
        if (!canSendUI()) return;

        String normalizedId = normalizedIds.remove(identifier);
        if (normalizedId == null) return;

        customHuds.remove(identifier);
        UICommandBuilder builder = new UICommandBuilder();
        builder.remove("#MultipleHUD #" + normalizedId);
        update(false, builder);
    }
}
