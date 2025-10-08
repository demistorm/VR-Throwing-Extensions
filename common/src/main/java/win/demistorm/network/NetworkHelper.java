package win.demistorm.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import win.demistorm.ModCompat;
import win.demistorm.ThrownProjectileEntity;

import static win.demistorm.VRThrowingExtensions.log;

// Handles spawning and launching the thrown item
public final class NetworkHelper {

    public static final Identifier THROW_CHANNEL =
            Identifier.of("vr-throwing-extensions", "throw_packet");
    public static final Identifier CATCH_CHANNEL =
            Identifier.of("vr-throwing-extensions", "catch_packet");
    public static final Identifier CATCH_UPDATE_CHANNEL =
            Identifier.of("vr-throwing-extensions", "catch_update_packet");
    public static final Identifier CATCH_COMPLETE_CHANNEL =
            Identifier.of("vr-throwing-extensions", "catch_complete_packet");

    public record ThrowPacket(Vec3d pos, Vec3d vel, boolean wholeStack, float rollDeg)
            implements CustomPayload {

        public static final Id<ThrowPacket> ID = new Id<>(THROW_CHANNEL);

        public static final PacketCodec<RegistryByteBuf, ThrowPacket> CODEC =
                PacketCodec.of(
                        (value, buf) -> {
                            buf.writeDouble(value.pos.x);
                            buf.writeDouble(value.pos.y);
                            buf.writeDouble(value.pos.z);
                            buf.writeDouble(value.vel.x);
                            buf.writeDouble(value.vel.y);
                            buf.writeDouble(value.vel.z);
                            buf.writeBoolean(value.wholeStack);
                            buf.writeFloat(value.rollDeg);
                        },
                        buf -> new ThrowPacket(               // read
                                new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                                new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                                buf.readBoolean(),
                                buf.readFloat())
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record CatchPacket(int entityId, boolean startCatch)
            implements CustomPayload {

        public static final Id<CatchPacket> ID = new Id<>(CATCH_CHANNEL);

        public static final PacketCodec<RegistryByteBuf, CatchPacket> CODEC =
                PacketCodec.of(
                        (value, buf) -> {
                            buf.writeInt(value.entityId);
                            buf.writeBoolean(value.startCatch);
                        },
                        buf -> new CatchPacket(buf.readInt(), buf.readBoolean())
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record CatchUpdatePacket(int entityId, Vec3d velocity, float rollDeg)
            implements CustomPayload {

        public static final Id<CatchUpdatePacket> ID = new Id<>(CATCH_UPDATE_CHANNEL);

        public static final PacketCodec<RegistryByteBuf, CatchUpdatePacket> CODEC =
                PacketCodec.of(
                        (value, buf) -> {
                            buf.writeInt(value.entityId);
                            buf.writeDouble(value.velocity.x);
                            buf.writeDouble(value.velocity.y);
                            buf.writeDouble(value.velocity.z);
                            buf.writeFloat(value.rollDeg);
                        },
                        buf -> new CatchUpdatePacket(
                                buf.readInt(),
                                new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                                buf.readFloat())
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record CatchCompletePacket(int entityId)
            implements CustomPayload {

        public static final Id<CatchCompletePacket> ID = new Id<>(CATCH_COMPLETE_CHANNEL);

        public static final PacketCodec<RegistryByteBuf, CatchCompletePacket> CODEC =
                PacketCodec.of(
                        (value, buf) -> buf.writeInt(value.entityId),
                        buf -> new CatchCompletePacket(buf.readInt())
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record BloodParticlePacket(Vec3d pos, Vec3d velocity) implements CustomPayload {
        public static final Id<BloodParticlePacket> ID =
                new Id<>(Identifier.of("vr-throwing-extensions", "blood_particle"));

        public static final PacketCodec<RegistryByteBuf, BloodParticlePacket> CODEC =
                PacketCodec.of(
                        (value, buf) -> {
                            buf.writeDouble(value.pos.x);
                            buf.writeDouble(value.pos.y);
                            buf.writeDouble(value.pos.z);
                            buf.writeDouble(value.velocity.x);
                            buf.writeDouble(value.velocity.y);
                            buf.writeDouble(value.velocity.z);
                        },
                        buf -> new BloodParticlePacket(
                                new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                                new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()))
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record BleedParticlePacket(Vec3d pos) implements CustomPayload {
        public static final Id<BleedParticlePacket> ID =
                new Id<>(Identifier.of("vr-throwing-extensions", "bleed_particle"));

        public static final PacketCodec<RegistryByteBuf, BleedParticlePacket> CODEC =
                PacketCodec.of(
                        (value, buf) -> {
                            buf.writeDouble(value.pos.x);
                            buf.writeDouble(value.pos.y);
                            buf.writeDouble(value.pos.z);
                        },
                        buf -> new BleedParticlePacket(
                                new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()))
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void initServer() {
        // Tell Netty how to encode/decode
        PayloadTypeRegistry.playC2S().register(ThrowPacket.ID, ThrowPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(CatchPacket.ID, CatchPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(CatchUpdatePacket.ID, CatchUpdatePacket.CODEC);
        PayloadTypeRegistry.playC2S().register(CatchCompletePacket.ID, CatchCompletePacket.CODEC);
        PayloadTypeRegistry.playS2C().register(BloodParticlePacket.ID, BloodParticlePacket.CODEC);
        PayloadTypeRegistry.playS2C().register(BleedParticlePacket.ID, BleedParticlePacket.CODEC);

        // Handles the packets
        ServerPlayNetworking.registerGlobalReceiver(ThrowPacket.ID, (payload, context) -> {
            PlayerEntity sender = context.player();
            context.server().execute(() -> handleThrow(sender, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(CatchPacket.ID, (payload, context) -> {
            PlayerEntity sender = context.player();
            context.server().execute(() -> handleCatch(sender, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(CatchUpdatePacket.ID, (payload, context) -> {
            PlayerEntity sender = context.player();
            context.server().execute(() -> handleCatchUpdate(sender, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(CatchCompletePacket.ID, (payload, context) -> {
            PlayerEntity sender = context.player();
            context.server().execute(() -> handleCatchComplete(sender, payload));
        });
    }

    // Spawns thrown item as entity from packet info
    private static void handleThrow(PlayerEntity player, ThrowPacket packet) {
        if (player == null || !player.isAlive()) return;

        ItemStack heldStack = player.getMainHandStack();
        if (heldStack.isEmpty() || ModCompat.throwingDisabled(heldStack)) return;

        // Create projectile with the correct stack size
        ThrownProjectileEntity proj = new ThrownProjectileEntity(
                player.getWorld(), player, heldStack, packet.wholeStack());

        proj.setPosition(packet.pos());
        proj.setOriginalThrowPos(packet.pos()); // Boomerang reasons

        // Rename velocity for readability
        Vec3d velocity = packet.vel();
        proj.setVelocity(velocity);

        // Sets arm roll degree
        proj.setHandRoll(packet.rollDeg());

        // Launches/spawns the entity
        player.getWorld().spawnEntity(proj);

        // Check item attack damage to determine which sound to play
        float attackDamage = ThrownProjectileEntity.stackBaseDamage(heldStack);
        log.debug("[Network] Thrown item attack damage = {}", attackDamage);

        // Check item damage
        if (attackDamage <= 1.0F) {
            // Play throw sound
            if (!player.getWorld().isClient()) {
                player.getWorld().playSound(null, player.getBlockPos(),
                        SoundEvents.ENTITY_WITCH_THROW, SoundCategory.PLAYERS,
                        0.6f, 1.05f);
            }
        } else {
            // Play weapon/tool throw sound
            if (!player.getWorld().isClient()) {
                player.getWorld().playSound(null, player.getBlockPos(),
                        SoundEvents.ITEM_TRIDENT_THROW.value(), SoundCategory.PLAYERS,
                        0.6f, 1.33f);
            }
        }

        // Remove items from player's hand
        if (packet.wholeStack()) {
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        } else {
            if (heldStack.getCount() > 1) {
                heldStack.decrement(1);
            } else {
                player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            }
        }
    }

    // Handles catch start/cancel packet
    private static void handleCatch(PlayerEntity player, CatchPacket packet) {
        if (player == null || !player.isAlive()) return;

        ServerWorld world = (ServerWorld) player.getWorld();
        Entity entity = world.getEntityById(packet.entityId());

        if (!(entity instanceof ThrownProjectileEntity projectile)) return;

        if (packet.startCatch()) {
            projectile.startCatch();
        } else {
            projectile.cancelCatch();
        }
    }

    // Handles catch velocity and rotation updates
    private static void handleCatchUpdate(PlayerEntity player, CatchUpdatePacket packet) {
        if (player == null || !player.isAlive()) return;

        ServerWorld world = (ServerWorld) player.getWorld();
        Entity entity = world.getEntityById(packet.entityId());

        if (!(entity instanceof ThrownProjectileEntity projectile)) return;
        if (!projectile.isCatching()) return;

        // Update projectile velocity for magnetism effect
        projectile.setVelocity(packet.velocity());

        // Update hand roll for rotation blending
        projectile.setHandRoll(packet.rollDeg());
    }

    // Handles catch completion
    private static void handleCatchComplete(PlayerEntity player, CatchCompletePacket packet) {
        if (player == null || !player.isAlive()) return;

        ServerWorld world = (ServerWorld) player.getWorld();
        Entity entity = world.getEntityById(packet.entityId());

        if (!(entity instanceof ThrownProjectileEntity projectile)) return;
        if (!projectile.isCatching()) return;

        // Check that player's main hand is still empty
        ItemStack mainHand = player.getMainHandStack();
        if (!mainHand.isEmpty()) return;

        // Get the item stack from the projectile
        ItemStack projectileStack = projectile.getStack();
        int stackSize = projectile.getStackSize();

        // Play catch sound
        if (!player.getWorld().isClient()) {
            player.getWorld().playSound(null, player.getBlockPos(),
                    SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS,
                    0.5f, 2.0f);
        }

        // Gives the itemstack to the player
        ItemStack giveStack = projectileStack.copy();
        giveStack.setCount(stackSize);
        player.setStackInHand(Hand.MAIN_HAND, giveStack);

        // Remove the projectile
        projectile.discard();
    }
}