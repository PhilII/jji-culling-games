package net.jji.cullinggames.client;

import net.jji.cullinggames.CullingGames;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * The mod's sounds (assets/cullinggames/sounds.json). Played on the client only, so they are not registered:
 * the server stays free of extra registry entries and players without the mod can still join.
 */
final class CgSounds {
    private CgSounds() {}

    /** Plays a sound by name, e.g. "start". Empty does nothing. */
    static void play(String name) {
        if (name.isEmpty()) {
            return;
        }
        SoundEvent sound = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(CullingGames.MOD_ID, name));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0f, 0.9f));
    }
}
