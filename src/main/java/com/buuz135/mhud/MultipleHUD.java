package com.buuz135.mhud;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

public class MultipleHUD extends JavaPlugin {

    private static MultipleHUD instance;

    public static MultipleHUD getInstance() {
        return instance;
    }

    public MultipleHUD(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        super.setup();
        // Nothing to initialize here.
        // UI safety is handled inside MultipleCustomUIHud.
    }

    public void setCustomHud(
            Player player,
            PlayerRef playerRef,
            String hudIdentifier,
            CustomUIHud customHud
    ) {
        CustomUIHud currentCustomHud = player.getHudManager().getCustomHud();

        if (currentCustomHud instanceof MultipleCustomUIHud multipleCustomUIHud) {
            multipleCustomUIHud.add(hudIdentifier, customHud);
        } else {
            MultipleCustomUIHud mchud = new MultipleCustomUIHud(playerRef);
            player.getHudManager().setCustomHud(playerRef, mchud);
            mchud.add(hudIdentifier, customHud);

            if (currentCustomHud != null) {
                mchud.add("Unknown", currentCustomHud);
            }
        }
    }

    @Deprecated
    public void hideCustomHud(
            Player player,
            PlayerRef playerRef,
            String hudIdentifier
    ) {
        hideCustomHud(player, hudIdentifier);
    }

    public void hideCustomHud(Player player, String hudIdentifier) {
        CustomUIHud currentCustomHud = player.getHudManager().getCustomHud();

        if (currentCustomHud instanceof MultipleCustomUIHud multipleCustomUIHud) {
            multipleCustomUIHud.remove(hudIdentifier);
        }
    }
}
