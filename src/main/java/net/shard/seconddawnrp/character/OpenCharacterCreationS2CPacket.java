package net.shard.seconddawnrp.character;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;

import java.util.ArrayList;
import java.util.List;

/**
 * Sent server → client when a player right-clicks the Character Creation Terminal.
 *
 * <p>Carries:
 * <ul>
 *   <li>The full species list (id + displayName + description) for the dropdown.</li>
 *   <li>The player's current character data (pre-populated if editing).</li>
 *   <li>{@code locked} — true if species is already set (species field becomes read-only).</li>
 * </ul>
 */
public record OpenCharacterCreationS2CPacket(
        List<SpeciesSnapshot> species,
        String currentCharacterName,
        String currentSpeciesId,
        String currentBio,
        boolean speciesLocked   // true once species has been set — cannot change without GM override
) implements CustomPayload {

    public static final Id<OpenCharacterCreationS2CPacket> ID =
            new Id<>(Identifier.of(SecondDawnRP.MOD_ID, "open_character_creation"));

    /** Lightweight species entry for the dropdown. */
    public record SpeciesSnapshot(String id, String displayName, String description) {}

    // ── Codec ─────────────────────────────────────────────────────────────────

    private static final PacketCodec<RegistryByteBuf, SpeciesSnapshot> SPECIES_CODEC =
            PacketCodec.of(
                    (v, buf) -> {
                        buf.writeString(v.id());
                        buf.writeString(v.displayName());
                        buf.writeString(v.description());
                    },
                    buf -> new SpeciesSnapshot(buf.readString(), buf.readString(), buf.readString())
            );

    public static final PacketCodec<RegistryByteBuf, OpenCharacterCreationS2CPacket> CODEC =
            PacketCodec.of(
                    (value, buf) -> {
                        buf.writeInt(value.species().size());
                        for (SpeciesSnapshot s : value.species()) SPECIES_CODEC.encode(buf, s);
                        buf.writeString(value.currentCharacterName());
                        buf.writeString(value.currentSpeciesId());
                        buf.writeString(value.currentBio());
                        buf.writeBoolean(value.speciesLocked());
                    },
                    buf -> {
                        int count = buf.readInt();
                        List<SpeciesSnapshot> list = new ArrayList<>(count);
                        for (int i = 0; i < count; i++) list.add(SPECIES_CODEC.decode(buf));
                        return new OpenCharacterCreationS2CPacket(
                                list,
                                buf.readString(),
                                buf.readString(),
                                buf.readString(),
                                buf.readBoolean()
                        );
                    }
            );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }

    // ── Factory ───────────────────────────────────────────────────────────────

    public static OpenCharacterCreationS2CPacket build(
            SpeciesRegistry speciesRegistry, CharacterProfile profile) {

        List<SpeciesSnapshot> snapshots = speciesRegistry.getAll().stream()
                .map(s -> new SpeciesSnapshot(s.getId(), s.getDisplayName(), s.getDescription()))
                .toList();

        return new OpenCharacterCreationS2CPacket(
                snapshots,
                profile.getCharacterName() != null ? profile.getCharacterName() : "",
                profile.getSpecies() != null ? profile.getSpecies() : "",
                profile.getBio() != null ? profile.getBio() : "",
                profile.getSpecies() != null && !profile.getSpecies().isBlank() // species locked if set
        );
    }
}