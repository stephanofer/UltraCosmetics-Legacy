package be.isach.ultracosmetics.cosmetics.killeffects.packetevents;

import be.isach.ultracosmetics.cosmetics.killeffects.VictimSnapshot;
import be.isach.ultracosmetics.cosmetics.killeffects.render.KillEffectRenderer;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.UserDisconnectEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes;
import com.github.retrooper.packetevents.protocol.particle.data.LegacyParticleData;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.protocol.util.LegacyComponent;
import com.github.retrooper.packetevents.protocol.sound.SoundCategory;
import com.github.retrooper.packetevents.protocol.sound.StaticSound;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Kept outside core's Shadow task: PacketEvents signatures must retain their own Adventure types. */
public final class PacketEventsKillEffectRenderer implements KillEffectRenderer {
    private static final StaticSound GLASS_BREAK = new StaticSound(new ResourceLocation("dig.glass"), null);
    private final Map<User, Set<UUID>> knownProfiles = new ConcurrentHashMap<>();
    private final TabNameTags tabNameTags;
    private volatile boolean closed;
    private final PacketListenerAbstract profiles = new PacketListenerAbstract(PacketListenerPriority.MONITOR) {
        @Override
        public void onPacketSend(PacketSendEvent event) {
            if (closed || event.isCancelled() || event.getPacketType() != PacketType.Play.Server.PLAYER_INFO) return;
            WrapperPlayServerPlayerInfo packet = new WrapperPlayServerPlayerInfo(event);
            boolean add = packet.getAction() == WrapperPlayServerPlayerInfo.Action.ADD_PLAYER;
            if (!add && packet.getAction() != WrapperPlayServerPlayerInfo.Action.REMOVE_PLAYER) return;
            List<UUID> identities = new ArrayList<>();
            for (WrapperPlayServerPlayerInfo.PlayerData data : packet.getPlayerDataList()) identities.add(data.getUserProfile().getUUID());
            User user = event.getUser();
            // Only mark profiles after delivery; never access Bukkit from the network callback.
            event.getTasksAfterSend().add(() -> {
                if (closed) return;
                Set<UUID> known = knownProfiles.computeIfAbsent(user, ignored -> ConcurrentHashMap.newKeySet());
                if (add) known.addAll(identities); else known.removeAll(identities);
            });
        }

        @Override
        public void onUserDisconnect(UserDisconnectEvent event) { knownProfiles.remove(event.getUser()); }
    };

    public PacketEventsKillEffectRenderer() {
        if (PacketEvents.getAPI() == null || !PacketEvents.getAPI().isInitialized()
                || PacketEvents.getAPI().isTerminated()
                || !PacketEvents.getAPI().getVersion().toString().equals("2.13.0")) {
            throw new IllegalStateException("An initialized PacketEvents 2.13.0 instance is required");
        }
        // Resolve protocol registries at startup, not in the middle of a death event.
        if (EntityTypes.ARMOR_STAND == null || ParticleTypes.BLOCK == null || ItemTypes.ICE == null) {
            throw new IllegalStateException("Required protocol registries unavailable");
        }
        tabNameTags = TabNameTags.create();
        PacketEvents.getAPI().getEventManager().registerListener(profiles);
    }

    @Override
    public boolean knowsProfile(UUID viewer, UUID profile) {
        Player player = Bukkit.getPlayer(viewer);
        if (player == null) return false;
        User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        Set<UUID> known = user == null ? null : knownProfiles.get(user);
        return known != null && known.contains(profile);
    }

    @Override
    public void close() {
        closed = true;
        PacketEvents.getAPI().getEventManager().unregisterListener(profiles);
        knownProfiles.clear();
    }

    @Override
    public List<VictimSnapshot.SkinProperty> captureSkin(Player player) {
        User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null) throw new IllegalStateException("Player connection is not ready");
        List<VictimSnapshot.SkinProperty> skin = new ArrayList<>();
        for (TextureProperty property : user.getProfile().getTextureProperties()) {
            skin.add(new VictimSnapshot.SkinProperty(property.getName(), property.getValue(), property.getSignature()));
        }
        return skin;
    }

    @Override
    public NameTag captureNameTag(Player player) {
        return tabNameTags == null ? null : tabNameTags.capture(player);
    }

    private void send(UUID viewer, PacketWrapper<?> packet) {
        Player player = Bukkit.getPlayer(viewer);
        if (player == null || !player.isOnline()) throw new IllegalStateException("Viewer disconnected");
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
    }

    @Override
    public void profile(UUID viewer, UUID profile, VictimSnapshot victim, boolean add) {
        if (profile.equals(victim.uuid)) throw new IllegalArgumentException("Never change a real victim's tab entry");
        List<TextureProperty> skin = new ArrayList<>();
        for (VictimSnapshot.SkinProperty property : victim.skin) {
            skin.add(new TextureProperty(property.name, property.value, property.signature));
        }
        WrapperPlayServerPlayerInfo.PlayerData data = new WrapperPlayServerPlayerInfo.PlayerData(
                null, new UserProfile(profile, victim.name, skin), GameMode.SURVIVAL, 0);
        send(viewer, new WrapperPlayServerPlayerInfo(add ? WrapperPlayServerPlayerInfo.Action.ADD_PLAYER
                : WrapperPlayServerPlayerInfo.Action.REMOVE_PLAYER, data));
    }

    @Override
    public void createNameTag(UUID viewer, String teamName, VictimSnapshot victim) {
        WrapperPlayServerTeams.ScoreBoardTeamInfo info = new WrapperPlayServerTeams.ScoreBoardTeamInfo(
                new LegacyComponent(teamName), new LegacyComponent(victim.prefix), new LegacyComponent(victim.suffix),
                WrapperPlayServerTeams.NameTagVisibility.ALWAYS, WrapperPlayServerTeams.CollisionRule.ALWAYS,
                net.kyori.adventure.text.format.NamedTextColor.WHITE, WrapperPlayServerTeams.OptionData.NONE);
        send(viewer, new WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.CREATE, info,
                Collections.singletonList(victim.name)));
    }

    @Override
    public void removeNameTag(UUID viewer, String teamName) {
        send(viewer, new WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.REMOVE,
                (WrapperPlayServerTeams.ScoreBoardTeamInfo) null, Collections.emptyList()));
    }

    @Override
    public void spawnPlayer(UUID viewer, int entity, UUID profile, double x, double y, double z, float yaw, float pitch) {
        send(viewer, new WrapperPlayServerSpawnPlayer(entity, profile, new Vector3d(x, y, z), yaw, pitch,
                Arrays.asList(new EntityData<>(0, EntityDataTypes.BYTE, (byte) 0),
                        new EntityData<>(10, EntityDataTypes.BYTE, (byte) 0x7f))));
    }

    @Override
    public void headRotation(UUID viewer, int entity, float yaw) {
        send(viewer, new WrapperPlayServerEntityHeadLook(entity, yaw));
    }

    @Override
    public void spawnArmorStand(UUID viewer, int entity, double x, double y, double z, float yaw) {
        send(viewer, new WrapperPlayServerSpawnEntity(entity, Optional.empty(), EntityTypes.ARMOR_STAND,
                new Vector3d(x, y, z), 0, yaw, yaw, 0,
                Optional.of(new Vector3d(0, 0, 0))));
    }

    @Override
    public void hideArmorStand(UUID viewer, int entity) {
        send(viewer, new WrapperPlayServerEntityMetadata(entity,
                Arrays.asList(new EntityData<>(0, EntityDataTypes.BYTE, (byte) 0x20),
                        new EntityData<>(10, EntityDataTypes.BYTE, (byte) 0x18))));
    }

    @Override
    public void equipIceHelmet(UUID viewer, int entity) {
        ItemStack ice = ItemStack.builder().type(ItemTypes.ICE).amount(1).build();
        send(viewer, new WrapperPlayServerEntityEquipment(entity,
                Arrays.asList(new Equipment(EquipmentSlot.HELMET, ice))));
    }

    @Override
    public void teleportEntity(UUID viewer, int entity, double x, double y, double z, float yaw, float pitch) {
        send(viewer, new WrapperPlayServerEntityTeleport(entity, new Vector3d(x, y, z), yaw, pitch, false));
    }

    @Override
    public void playEntityStatus(UUID viewer, int entity, byte status) {
        send(viewer, new WrapperPlayServerEntityStatus(entity, status));
    }

    @Override
    public void stopEntityVelocity(UUID viewer, int entity) {
        send(viewer, new WrapperPlayServerEntityVelocity(entity, new Vector3d(0, 0, 0)));
    }

    @Override
    public void animateEntity(UUID viewer, int entity, int animation) {
        send(viewer, new WrapperPlayServerEntityAnimation(entity, WrapperPlayServerEntityAnimation.EntityAnimationType.getById(animation)));
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void spawnParticle(UUID viewer, Particle particle, double x, double y, double z) {
        com.github.retrooper.packetevents.protocol.particle.Particle<?> value;
        Vector3f offset = new Vector3f(0, 0, 0);
        float speed = 0;
        int count = 1;
        switch (particle) {
            case ICE:
                // The registry's generic data type is modern. 1.8 requires id | (metadata << 12),
                // not WrappedBlockState's palette ID (id << 4 | metadata).
                value = new com.github.retrooper.packetevents.protocol.particle.Particle(ParticleTypes.BLOCK,
                        LegacyParticleData.ofBlock(ItemTypes.ICE, (byte) 0));
                break;
            case CYAN:
                value = new com.github.retrooper.packetevents.protocol.particle.Particle<>(ParticleTypes.DUST);
                // Legacy redstone color is carried in offsets with count=0, not modern dust metadata.
                offset = new Vector3f(0.01f, 0.8f, 1f);
                speed = 1;
                count = 0;
                break;
            case CLOUD:
                value = new com.github.retrooper.packetevents.protocol.particle.Particle<>(ParticleTypes.CLOUD);
                break;
            default:
                value = new com.github.retrooper.packetevents.protocol.particle.Particle<>(ParticleTypes.ITEM_SNOWBALL);
        }
        send(viewer, new WrapperPlayServerParticle(value, false, new Vector3d(x, y, z), offset, speed, count));
    }

    @Override
    public void playSound(UUID viewer, double x, double y, double z, float volume, float pitch) {
        send(viewer, new WrapperPlayServerSoundEffect(GLASS_BREAK, SoundCategory.PLAYER,
                new Vector3d(x, y, z), volume, pitch, 0L));
    }

    @Override
    public void destroyEntities(UUID viewer, int[] entities) {
        send(viewer, new WrapperPlayServerDestroyEntities(entities));
    }

    private static final class TabNameTags {
        private final Object api;
        private final Object manager;
        private final Method getPlayer;
        private final Method getPrefix;
        private final Method getSuffix;

        private TabNameTags(Object api, Object manager, Method getPlayer, Method getPrefix, Method getSuffix) {
            this.api = api;
            this.manager = manager;
            this.getPlayer = getPlayer;
            this.getPrefix = getPrefix;
            this.getSuffix = getSuffix;
        }

        static TabNameTags create() {
            Plugin tab = Bukkit.getPluginManager().getPlugin("TAB");
            if (tab == null || !tab.isEnabled()) return null;
            try {
                ClassLoader loader = tab.getClass().getClassLoader();
                Class<?> apiClass = Class.forName("me.neznamy.tab.api.TabAPI", false, loader);
                Class<?> playerClass = Class.forName("me.neznamy.tab.api.TabPlayer", false, loader);
                Class<?> managerClass = Class.forName("me.neznamy.tab.api.nametag.NameTagManager", false, loader);
                Object api = apiClass.getMethod("getInstance").invoke(null);
                Object manager = apiClass.getMethod("getNameTagManager").invoke(api);
                return new TabNameTags(api, manager, apiClass.getMethod("getPlayer", UUID.class),
                        managerClass.getMethod("getOriginalReplacedPrefix", playerClass),
                        managerClass.getMethod("getOriginalReplacedSuffix", playerClass));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                Bukkit.getLogger().warning("Kill Effects could not connect to TAB name tags: " + e);
                return null;
            }
        }

        NameTag capture(Player player) {
            try {
                Object tabPlayer = getPlayer.invoke(api, player.getUniqueId());
                if (tabPlayer == null) return null;
                return new NameTag((String) getPrefix.invoke(manager, tabPlayer),
                        (String) getSuffix.invoke(manager, tabPlayer));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                return null;
            }
        }
    }
}
