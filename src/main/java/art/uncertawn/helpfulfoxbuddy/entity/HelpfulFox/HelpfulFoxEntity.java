// TODO
/*
make the fox sit down and stand up when right clicking
fix sit down and look around goal cuz it sits the fox down and you cant do anything about it if you leave it and unload it

 */

package art.uncertawn.helpfulfoxbuddy.entity.HelpfulFox;

import art.uncertawn.helpfulfoxbuddy.entity.ModEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.block.*;
import net.minecraft.component.ComponentType;
import net.minecraft.component.ComponentsAccess;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.ai.control.LookControl;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.ai.pathing.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.function.ValueLists;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.*;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class HelpfulFoxEntity extends AnimalEntity {
    private static final TrackedData<Integer> VARIANT;
    private static final TrackedData<Byte> FOX_FLAGS;
    private static final TrackedData<Integer> FOX_TYPE = DataTracker.registerData(HelpfulFoxEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final int SITTING_FLAG = 1;
    public static final int CROUCHING_FLAG = 4;
    public static final int ROLLING_HEAD_FLAG = 8;
    public static final int CHASING_FLAG = 16;
    private static final int SLEEPING_FLAG = 32;
    private static final int WALKING_FLAG = 64;
    private static final int AGGRESSIVE_FLAG = 128;
    private static final TrackedData<Optional<LazyEntityReference<LivingEntity>>> OWNER;
    private static final TrackedData<Optional<LazyEntityReference<LivingEntity>>> OTHER_TRUSTED;
    private static final EntityDimensions BABY_BASE_DIMENSIONS;
    private float headRollProgress;
    private float lastHeadRollProgress;
    float extraRollingHeight;
    float lastExtraRollingHeight;
    private int eatingTime;

    public HelpfulFoxEntity(EntityType<? extends HelpfulFoxEntity> entityType, World world) {
        super(ModEntity.FOXY, world);
        this.lookControl = new HelpfulFoxEntity.FoxLookControl();
        this.moveControl = new HelpfulFoxEntity.FoxMoveControl();
        this.setPathfindingPenalty(PathNodeType.DANGER_OTHER, 0.0F);
        this.setPathfindingPenalty(PathNodeType.DAMAGE_OTHER, 0.0F);
        this.setCanPickUpLoot(false);
        this.getNavigation().setMaxFollowRange(32.0F);
        this.setInvulnerable(true);
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return AnimalEntity.createAnimalAttributes()
                .add(EntityAttributes.MAX_HEALTH, 100.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    public boolean isInvulnerable() {
        return true;
    }

    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(OWNER, Optional.empty());
        builder.add(OTHER_TRUSTED, Optional.empty());
        builder.add(VARIANT, HelpfulFoxEntity.Variant.DEFAULT.getIndex());
        builder.add(FOX_FLAGS, (byte)0);
        builder.add(FOX_TYPE, 0);
    }

    protected void initGoals() {
        this.goalSelector.add(0, new HelpfulFoxEntity.FoxSwimGoal());
        this.goalSelector.add(1, new HelpfulFoxEntity.StopWanderingGoal());
        this.goalSelector.add(1, new WanderAroundFarGoal(this, 1.0));
        this.goalSelector.add(2, new CustomFollowOwnerGoal(this, 1.0, 3.0F, 8.0F));
        this.goalSelector.add(3, new HelpfulFoxEntity.LookAtEntityGoal(this, PlayerEntity.class, 24.0F));
        this.goalSelector.add(3, new HelpfulFoxEntity.SitDownAndLookAroundGoal());
        /// idk
        this.goalSelector.add(4, new FleeEntityGoal(this, WolfEntity.class, 8.0F, 1.6, 1.4, (entity) -> {
            return !((WolfEntity)entity).isTamed() && !this.isAggressive();
        }));
        this.goalSelector.add(4, new FleeEntityGoal(this, PolarBearEntity.class, 8.0F, 1.6, 1.4, (entity) -> {
            return !this.isAggressive();
        }));
        /// /idk
        this.goalSelector.add(5, new HelpfulFoxEntity.EatBerriesGoal(1.2000000476837158, 12, 1));
        this.goalSelector.add(7, new HelpfulFoxEntity.DelayedCalmDownGoal());
    }

    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack itemStack = player.getStackInHand(hand);
        Item item = itemStack.getItem();
        if (!this.getWorld().isClient && (itemStack.isOf(Items.SWEET_BERRIES) || itemStack.isOf(Items.GLOW_BERRIES))) {
            itemStack.decrementUnlessCreative(1, player);
//            this.setTamed(true, true);
            this.dataTracker.set(OWNER, Optional.of(player).map(LazyEntityReference::new));
            if (player instanceof ServerPlayerEntity serverPlayerEntity) {
                Criteria.TAME_ANIMAL.trigger(serverPlayerEntity, this);
            }
            this.navigation.stop();
//            this.setTarget((LivingEntity)null);
            this.setSitting(true);
            this.getWorld().sendEntityStatus(this, EntityStatuses.ADD_POSITIVE_PLAYER_REACTION_PARTICLES);
            return ActionResult.SUCCESS_SERVER;
        } else {
            ActionResult actionResult = super.interactMob(player, hand);
            if (!actionResult.isAccepted() && this.isOwner(player)) {
                this.setSitting(!this.isSitting());
                this.jumping = false;
                this.navigation.stop();
                this.setTarget((LivingEntity)null);
                return ActionResult.SUCCESS.noIncrementStat();
            } else {
                return actionResult;
            }
        }
    }

    public boolean isOwner(LivingEntity entity) {
        return entity == this.getOwner();
    }

    public void tickMovement() {
        if (!this.getWorld().isClient && this.isAlive() && this.canActVoluntarily()) {
            ++this.eatingTime;
            ItemStack itemStack = this.getEquippedStack(EquipmentSlot.MAINHAND);
            if (this.canEat(itemStack)) {
                if (this.eatingTime > 600) {
                    ItemStack itemStack2 = itemStack.finishUsing(this.getWorld(), this);
                    if (!itemStack2.isEmpty()) {
                        this.equipStack(EquipmentSlot.MAINHAND, itemStack2);
                    }

                    this.eatingTime = 0;
                } else if (this.eatingTime > 560 && this.random.nextFloat() < 0.1F) {
                    this.playEatSound();
                    this.getWorld().sendEntityStatus(this, EntityStatuses.CREATE_EATING_PARTICLES);
                }
            }

            LivingEntity livingEntity = this.getTarget();
            if (livingEntity == null || !livingEntity.isAlive()) {
                this.setCrouching(false);
                this.setRollingHead(false);
            }
        }

        if (this.isSleeping() || this.isImmobile()) {
            this.jumping = false;
            this.sidewaysSpeed = 0.0F;
            this.forwardSpeed = 0.0F;
        }

        super.tickMovement();
        if (this.isAggressive() && this.random.nextFloat() < 0.05F) {
            this.playSound(SoundEvents.ENTITY_FOX_AGGRO, 1.0F, 1.0F);
        }

    }

    protected boolean isImmobile() {
        return this.isDead();
    }

    private boolean canEat(ItemStack stack) {
        return stack.contains(DataComponentTypes.FOOD) && this.getTarget() == null && this.isOnGround() && !this.isSleeping();
    }

    protected void initEquipment(Random random, LocalDifficulty localDifficulty) {
        if (random.nextFloat() < 0.2F) {
            float f = random.nextFloat();
            ItemStack itemStack;
            if (f < 0.05F) {
                itemStack = new ItemStack(Items.FOX_SPAWN_EGG);
            } else if (f < 0.2F) {
                itemStack = new ItemStack(Items.FOX_SPAWN_EGG);
            } else if (f < 0.6F) {
                itemStack = new ItemStack(Items.FOX_SPAWN_EGG);
            } else if (f < 0.8F) {
                itemStack = new ItemStack(Items.FOX_SPAWN_EGG);
            } else {
                itemStack = new ItemStack(Items.FOX_SPAWN_EGG);
            }

            this.equipStack(EquipmentSlot.MAINHAND, itemStack);
        }

    }

    public void handleStatus(byte status) {
        if (status == EntityStatuses.CREATE_EATING_PARTICLES) {
            ItemStack itemStack = this.getEquippedStack(EquipmentSlot.MAINHAND);
            if (!itemStack.isEmpty()) {
                for(int i = 0; i < 8; ++i) {
                    Vec3d vec3d = (new Vec3d(((double)this.random.nextFloat() - 0.5) * 0.1, Math.random() * 0.1 + 0.1, 0.0)).rotateX(-this.getPitch() * 0.017453292F).rotateY(-this.getYaw() * 0.017453292F);
                    this.getWorld().addParticleClient(new ItemStackParticleEffect(ParticleTypes.ITEM, itemStack), this.getX() + this.getRotationVector().x / 2.0, this.getY(), this.getZ() + this.getRotationVector().z / 2.0, vec3d.x, vec3d.y + 0.05, vec3d.z);
                }
            }
        } else {
            super.handleStatus(status);
        }

    }

    public static DefaultAttributeContainer.Builder createFoxAttributes() {
        return AnimalEntity.createAnimalAttributes().add(EntityAttributes.MOVEMENT_SPEED, 0.30000001192092896).add(EntityAttributes.MAX_HEALTH, 10.0).add(EntityAttributes.ATTACK_DAMAGE, 2.0).add(EntityAttributes.SAFE_FALL_DISTANCE, 5.0).add(EntityAttributes.FOLLOW_RANGE, 32.0);
    }

    @Nullable
    public FoxEntity createChild(ServerWorld serverWorld, PassiveEntity passiveEntity) {
        return null;
    }

    public static boolean canSpawn(EntityType<HelpfulFoxEntity> type, WorldAccess world, SpawnReason spawnReason, BlockPos pos, Random random) {
        return world.getBlockState(pos.down()).isIn(BlockTags.FOXES_SPAWNABLE_ON) && isLightLevelValidForNaturalSpawn(world, pos);
    }

    @Nullable
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, @Nullable EntityData entityData) {
        RegistryEntry<Biome> registryEntry = world.getBiome(this.getBlockPos());
        HelpfulFoxEntity.Variant variant = HelpfulFoxEntity.Variant.fromBiome(registryEntry);
        boolean bl = false;
        if (entityData instanceof HelpfulFoxEntity.FoxData foxData) {
            variant = foxData.type;
            if (foxData.getSpawnedCount() >= 2) {
                bl = true;
            }
        } else {
            entityData = new HelpfulFoxEntity.FoxData(variant);
        }

        this.setVariant(variant);
        if (bl) {
            this.setBreedingAge(-24000);
        }

        if (world instanceof ServerWorld) {
            this.addTypeSpecificGoals();
        }

        this.initEquipment(world.getRandom(), difficulty);
        this.setInvulnerable(true);
        return super.initialize(world, difficulty, spawnReason, (EntityData)entityData);
    }

    private void addTypeSpecificGoals() {

    }

    protected void playEatSound() {
        this.playSound(SoundEvents.ENTITY_FOX_EAT, 1.0F, 1.0F);
    }

    public EntityDimensions getBaseDimensions(EntityPose pose) {
        return this.isBaby() ? BABY_BASE_DIMENSIONS : super.getBaseDimensions(pose);
    }

    public HelpfulFoxEntity.Variant getVariant() {
        return HelpfulFoxEntity.Variant.byIndex((Integer)this.dataTracker.get(VARIANT));
    }

    private void setVariant(HelpfulFoxEntity.Variant variant) {
        this.dataTracker.set(VARIANT, variant.getIndex());
    }

    @Nullable
    public <T> T get(ComponentType<? extends T> type) {
        return type == DataComponentTypes.FOX_VARIANT ? castComponentValue(type, this.getVariant()) : super.get(type);
    }

    protected void copyComponentsFrom(ComponentsAccess from) {
        this.copyComponentFrom(from, DataComponentTypes.FOX_VARIANT);
        super.copyComponentsFrom(from);
    }

    protected <T> boolean setApplicableComponent(ComponentType<T> type, T value) {
        if (type == DataComponentTypes.FOX_VARIANT) {
//            this.setVariant((HelpfulFoxEntity.Variant)castComponentValue(DataComponentTypes.FOX_VARIANT, value));
            return true;
        } else {
            return super.setApplicableComponent(type, value);
        }
    }

    Stream<LazyEntityReference<LivingEntity>> getTrustedEntities() {
        return Stream.concat(((Optional)this.dataTracker.get(OWNER)).stream(), ((Optional)this.dataTracker.get(OTHER_TRUSTED)).stream());
    }

    void trust(Object entity) {
        this.trust(new LazyEntityReference((LivingEntity) entity));
    }

    void trust(LivingEntity entity) {
        this.trust(new LazyEntityReference(entity));
    }

    private void trust(LazyEntityReference<LivingEntity> entity) {
        if (((Optional)this.dataTracker.get(OWNER)).isPresent()) {
            this.dataTracker.set(OTHER_TRUSTED, Optional.of(entity));
        } else {
            this.dataTracker.set(OWNER, Optional.of(entity));
        }

    }

    protected void writeCustomData(WriteView view) {
        super.writeCustomData(view);
//        view.put("Trusted", TRUSTED_ENTITIES_CODEC, this.getTrustedEntities().toList());
        view.putBoolean("Sleeping", this.isSleeping());
        view.put("Type", HelpfulFoxEntity.Variant.CODEC, this.getVariant());
        view.putBoolean("Sitting", this.isSitting());
        view.putBoolean("Crouching", this.isInSneakingPose());
    }

    protected void readCustomData(ReadView view) {
        super.readCustomData(view);
        this.clearTrusted();
//        ((List)view.read("Trusted", TRUSTED_ENTITIES_CODEC).orElse(List.of())).forEach(this::trust);
        this.setSleeping(view.getBoolean("Sleeping", false));
        this.setVariant((HelpfulFoxEntity.Variant)view.read("Type", HelpfulFoxEntity.Variant.CODEC).orElse(HelpfulFoxEntity.Variant.DEFAULT));
        this.setSitting(view.getBoolean("Sitting", false));
        this.setCrouching(view.getBoolean("Crouching", false));
        if (this.getWorld() instanceof ServerWorld) {
            this.addTypeSpecificGoals();
        }

    }

    private void clearTrusted() {
        this.dataTracker.set(OWNER, Optional.empty());
        this.dataTracker.set(OTHER_TRUSTED, Optional.empty());
    }

    public boolean isSitting() {
        return this.getFoxFlag(SITTING_FLAG);
    }

    public void setSitting(boolean sitting) {
        this.setFoxFlag(SITTING_FLAG, sitting);
    }

    public boolean isWalking() {
        return this.getFoxFlag(WALKING_FLAG);
    }

    void setWalking(boolean walking) {
        this.setFoxFlag(WALKING_FLAG, walking);
    }

    boolean isAggressive() {
        return this.getFoxFlag(AGGRESSIVE_FLAG);
    }

    void setAggressive(boolean aggressive) {
        this.setFoxFlag(AGGRESSIVE_FLAG, aggressive);
    }

    public boolean isSleeping() {
        return this.getFoxFlag(SLEEPING_FLAG);
    }

    void setSleeping(boolean sleeping) {
        this.setFoxFlag(SLEEPING_FLAG, sleeping);
    }

    private void setFoxFlag(int mask, boolean value) {
        if (value) {
            this.dataTracker.set(FOX_FLAGS, (byte)((Byte)this.dataTracker.get(FOX_FLAGS) | mask));
        } else {
            this.dataTracker.set(FOX_FLAGS, (byte)((Byte)this.dataTracker.get(FOX_FLAGS) & ~mask));
        }

    }

    private boolean getFoxFlag(int bitmask) {
        return ((Byte)this.dataTracker.get(FOX_FLAGS) & bitmask) != 0;
    }

    protected boolean canDispenserEquipSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND && this.canPickUpLoot();
    }

    public boolean canPickupItem(ItemStack stack) {
        ItemStack itemStack = this.getEquippedStack(EquipmentSlot.MAINHAND);
        return itemStack.isEmpty() || this.eatingTime > 0 && stack.contains(DataComponentTypes.FOOD) && !itemStack.contains(DataComponentTypes.FOOD);
    }

    private void spit(ItemStack stack) {
        if (!stack.isEmpty() && !this.getWorld().isClient) {
            ItemEntity itemEntity = new ItemEntity(this.getWorld(), this.getX() + this.getRotationVector().x, this.getY() + 1.0, this.getZ() + this.getRotationVector().z, stack);
            itemEntity.setPickupDelay(40);
            itemEntity.setThrower(this);
            this.playSound(SoundEvents.ENTITY_FOX_SPIT, 1.0F, 1.0F);
            this.getWorld().spawnEntity(itemEntity);
        }
    }

    private void dropItem(ItemStack stack) {
        ItemEntity itemEntity = new ItemEntity(this.getWorld(), this.getX(), this.getY(), this.getZ(), stack);
        this.getWorld().spawnEntity(itemEntity);
    }

    protected void loot(ServerWorld world, ItemEntity itemEntity) {
        ItemStack itemStack = itemEntity.getStack();
        if (this.canPickupItem(itemStack)) {
            int i = itemStack.getCount();
            if (i > 1) {
                this.dropItem(itemStack.split(i - 1));
            }

            this.spit(this.getEquippedStack(EquipmentSlot.MAINHAND));
            this.triggerItemPickedUpByEntityCriteria(itemEntity);
            this.equipStack(EquipmentSlot.MAINHAND, itemStack.split(1));
            this.setDropGuaranteed(EquipmentSlot.MAINHAND);
            this.sendPickup(itemEntity, itemStack.getCount());
            itemEntity.discard();
            this.eatingTime = 0;
        }

    }

    public void tick() {
        super.tick();
        if (this.canActVoluntarily()) {
            boolean bl = this.isTouchingWater();
            if (bl || this.getTarget() != null || this.getWorld().isThundering()) {
                this.stopSleeping();
            }

            if (bl || this.isSleeping()) {
                this.setSitting(false);
            }

            if (this.isWalking() && this.getWorld().random.nextFloat() < 0.2F) {
                BlockPos blockPos = this.getBlockPos();
                BlockState blockState = this.getWorld().getBlockState(blockPos);
                this.getWorld().syncWorldEvent(WorldEvents.BLOCK_BROKEN, blockPos, Block.getRawIdFromState(blockState));
            }
        }

        this.lastHeadRollProgress = this.headRollProgress;
        if (this.isRollingHead()) {
            this.headRollProgress += (1.0F - this.headRollProgress) * 0.4F;
        } else {
            this.headRollProgress += (0.0F - this.headRollProgress) * 0.4F;
        }

        this.lastExtraRollingHeight = this.extraRollingHeight;
        if (this.isInSneakingPose()) {
            this.extraRollingHeight += 0.2F;
            if (this.extraRollingHeight > 3.0F) {
                this.extraRollingHeight = 3.0F;
            }
        } else {
            this.extraRollingHeight = 0.0F;
        }

    }

    public boolean isBreedingItem(ItemStack stack) {
        return stack.isIn(ItemTags.FOX_FOOD);
    }

    protected void onPlayerSpawnedChild(PlayerEntity player, MobEntity child) {
        ((HelpfulFoxEntity)child).trust((LivingEntity)player);
    }

    public boolean isChasing() {
        return this.getFoxFlag(CHASING_FLAG);
    }

    public boolean isFullyCrouched() {
        return this.extraRollingHeight == 3.0F;
    }

    public void setCrouching(boolean crouching) {
        this.setFoxFlag(CROUCHING_FLAG, crouching);
    }

    public boolean isInSneakingPose() {
        return this.getFoxFlag(CROUCHING_FLAG);
    }

    public void setRollingHead(boolean rollingHead) {
        this.setFoxFlag(ROLLING_HEAD_FLAG, rollingHead);
    }

    public boolean isRollingHead() {
        return this.getFoxFlag(ROLLING_HEAD_FLAG);
    }

    public float getHeadRoll(float tickProgress) {
        return MathHelper.lerp(tickProgress, this.lastHeadRollProgress, this.headRollProgress) * 0.11F * 3.1415927F;
    }

    public float getBodyRotationHeightOffset(float tickProgress) {
        return MathHelper.lerp(tickProgress, this.lastExtraRollingHeight, this.extraRollingHeight);
    }

    public void setTarget(@Nullable LivingEntity target) {
        if (this.isAggressive() && target == null) {
            this.setAggressive(false);
        }

        super.setTarget(target);
    }

    void stopSleeping() {
        this.setSleeping(false);
    }

    void stopActions() {
        this.setRollingHead(false);
        this.setCrouching(false);
        this.setSitting(false);
        this.setSleeping(false);
        this.setAggressive(false);
        this.setWalking(false);
    }

    boolean wantsToPickupItem() {
        return !this.isSleeping() && !this.isSitting() && !this.isWalking();
    }

    public void playAmbientSound() {
        SoundEvent soundEvent = this.getAmbientSound();
        if (soundEvent == SoundEvents.ENTITY_FOX_SCREECH) {
            this.playSound(soundEvent, 2.0F, this.getSoundPitch());
        } else {
            super.playAmbientSound();
        }

    }

    @Nullable
    protected SoundEvent getAmbientSound() {
        if (this.isSleeping()) {
            return SoundEvents.ENTITY_FOX_SLEEP;
        } else {
            if (!this.getWorld().isDay() && this.random.nextFloat() < 0.1F) {
                List<PlayerEntity> list = this.getWorld().getEntitiesByClass(PlayerEntity.class, this.getBoundingBox().expand(16.0, 16.0, 16.0), EntityPredicates.EXCEPT_SPECTATOR);
                if (list.isEmpty()) {
                    return SoundEvents.ENTITY_FOX_SCREECH;
                }
            }

            return SoundEvents.ENTITY_FOX_AMBIENT;
        }
    }

    @Nullable
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_FOX_HURT;
    }

    @Nullable
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_FOX_DEATH;
    }

    boolean canTrust(LivingEntity entity) {
        return this.getTrustedEntities().anyMatch((trusted) -> {
            return trusted.uuidEquals(entity);
        });
    }

    protected void drop(ServerWorld world, DamageSource damageSource) {
        ItemStack itemStack = this.getEquippedStack(EquipmentSlot.MAINHAND);
        if (!itemStack.isEmpty()) {
            this.dropStack(world, itemStack);
            this.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }

        super.drop(world, damageSource);
    }

    public static boolean canJumpChase(HelpfulFoxEntity fox, LivingEntity chasedEntity) {
        double d = chasedEntity.getZ() - fox.getZ();
        double e = chasedEntity.getX() - fox.getX();
        double f = d / e;
//        int i = true;

        for(int j = 0; j < 6; ++j) {
            double g = f == 0.0 ? 0.0 : d * (double)((float)j / 6.0F);
            double h = f == 0.0 ? e * (double)((float)j / 6.0F) : g / f;

            for(int k = 1; k < 4; ++k) {
                if (!fox.getWorld().getBlockState(BlockPos.ofFloored(fox.getX() + h, fox.getY() + (double)k, fox.getZ() + g)).isReplaceable()) {
                    return false;
                }
            }
        }

        return true;
    }

    public Vec3d getLeashOffset() {
        return new Vec3d(0.0, (double)(0.55F * this.getStandingEyeHeight()), (double)(this.getWidth() * 0.4F));
    }

    static {
        VARIANT = DataTracker.registerData(HelpfulFoxEntity.class, TrackedDataHandlerRegistry.INTEGER);
        FOX_FLAGS = DataTracker.registerData(HelpfulFoxEntity.class, TrackedDataHandlerRegistry.BYTE);
        OWNER = DataTracker.registerData(HelpfulFoxEntity.class, TrackedDataHandlerRegistry.LAZY_ENTITY_REFERENCE);
        OTHER_TRUSTED = DataTracker.registerData(HelpfulFoxEntity.class, TrackedDataHandlerRegistry.LAZY_ENTITY_REFERENCE);
        BABY_BASE_DIMENSIONS = EntityType.FOX.getDimensions().scaled(0.5F).withEyeHeight(0.2975F);
    }

    public class FoxLookControl extends LookControl {
        public FoxLookControl() {
            super(HelpfulFoxEntity.this);
        }

        public void tick() {
            if (!HelpfulFoxEntity.this.isSleeping()) {
                super.tick();
            }

        }

        protected boolean shouldStayHorizontal() {
            return !HelpfulFoxEntity.this.isChasing() && !HelpfulFoxEntity.this.isInSneakingPose() && !HelpfulFoxEntity.this.isRollingHead() && !HelpfulFoxEntity.this.isWalking();
        }
    }

    private class FoxMoveControl extends MoveControl {
        public FoxMoveControl() {
            super(HelpfulFoxEntity.this);
        }

        public void tick() {
            if (HelpfulFoxEntity.this.wantsToPickupItem()) {
                super.tick();
            }

        }
    }

    public static enum Variant implements StringIdentifiable {
        RED(0, "red"),
        SNOW(1, "snow");

        public static final HelpfulFoxEntity.Variant DEFAULT = RED;
        public static final StringIdentifiable.EnumCodec<HelpfulFoxEntity.Variant> CODEC = StringIdentifiable.createCodec(HelpfulFoxEntity.Variant::values);
        private static final IntFunction<HelpfulFoxEntity.Variant> INDEX_MAPPER = ValueLists.createIndexToValueFunction(HelpfulFoxEntity.Variant::getIndex, values(), (ValueLists.OutOfBoundsHandling)ValueLists.OutOfBoundsHandling.ZERO);
        public static final PacketCodec<ByteBuf, HelpfulFoxEntity.Variant> PACKET_CODEC = PacketCodecs.indexed(INDEX_MAPPER, HelpfulFoxEntity.Variant::getIndex);
        private final int index;
        private final String id;

        private Variant(final int index, final String id) {
            this.index = index;
            this.id = id;
        }

        public String asString() {
            return this.id;
        }

        public int getIndex() {
            return this.index;
        }

        public static HelpfulFoxEntity.Variant byIndex(int index) {
            return (HelpfulFoxEntity.Variant)INDEX_MAPPER.apply(index);
        }

        public static HelpfulFoxEntity.Variant fromBiome(RegistryEntry<Biome> biome) {
            return biome.isIn(BiomeTags.SPAWNS_SNOW_FOXES) ? SNOW : RED;
        }
    }

    private class FoxSwimGoal extends SwimGoal {
        public FoxSwimGoal() {
            super(HelpfulFoxEntity.this);
        }

        public void start() {
            super.start();
            HelpfulFoxEntity.this.stopActions();
        }

        public boolean canStart() {
            return HelpfulFoxEntity.this.isTouchingWater() && HelpfulFoxEntity.this.getFluidHeight(FluidTags.WATER) > 0.25 || HelpfulFoxEntity.this.isInLava();
        }
    }

    private class StopWanderingGoal extends Goal {
        int timer;

        public StopWanderingGoal() {
            this.setControls(EnumSet.of(Goal.Control.LOOK, Goal.Control.JUMP, Goal.Control.MOVE));
        }

        public boolean canStart() {
            return HelpfulFoxEntity.this.isWalking();
        }

        public boolean shouldContinue() {
            return this.canStart() && this.timer > 0;
        }

        public void start() {
            this.timer = this.getTickCount(40);
        }

        public void stop() {
            HelpfulFoxEntity.this.setWalking(false);
        }

        public void tick() {
            --this.timer;
        }
    }

    @Nullable
    public LazyEntityReference<LivingEntity> getOwnerReference() {
        return (LazyEntityReference)((Optional)this.dataTracker.get(OWNER)).orElse((Object)null);
    }

    @Nullable
    LivingEntity getOwner() {
        return (LivingEntity)LazyEntityReference.resolve(this.getOwnerReference(), this.getWorld(), LivingEntity.class);
    }

    class DelayedCalmDownGoal extends HelpfulFoxEntity.CalmDownGoal {
        private static final int MAX_CALM_DOWN_TIME = toGoalTicks(140);
        private int timer;

        public DelayedCalmDownGoal() {
            super();
            this.timer = HelpfulFoxEntity.this.random.nextInt(MAX_CALM_DOWN_TIME);
            this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK, Goal.Control.JUMP));
        }

        public boolean canStart() {
            if (HelpfulFoxEntity.this.sidewaysSpeed == 0.0F && HelpfulFoxEntity.this.upwardSpeed == 0.0F && HelpfulFoxEntity.this.forwardSpeed == 0.0F) {
                return this.canNotCalmDown() || HelpfulFoxEntity.this.isSleeping();
            } else {
                return false;
            }
        }

        public boolean shouldContinue() {
            return this.canNotCalmDown();
        }

        private boolean canNotCalmDown() {
            if (this.timer > 0) {
                --this.timer;
                return false;
            } else {
                return HelpfulFoxEntity.this.getWorld().isDay() && this.isAtFavoredLocation() && !this.canCalmDown() && !HelpfulFoxEntity.this.inPowderSnow;
            }
        }

        public void stop() {
            this.timer = HelpfulFoxEntity.this.random.nextInt(MAX_CALM_DOWN_TIME);
            HelpfulFoxEntity.this.stopActions();
        }

        public void start() {
            HelpfulFoxEntity.this.setSitting(false);
            HelpfulFoxEntity.this.setCrouching(false);
            HelpfulFoxEntity.this.setRollingHead(false);
            HelpfulFoxEntity.this.setJumping(false);
            HelpfulFoxEntity.this.setSleeping(true);
            HelpfulFoxEntity.this.getNavigation().stop();
            HelpfulFoxEntity.this.getMoveControl().moveTo(HelpfulFoxEntity.this.getX(), HelpfulFoxEntity.this.getY(), HelpfulFoxEntity.this.getZ(), 0.0);
        }
    }

    public class EatBerriesGoal extends MoveToTargetPosGoal {
        private static final int EATING_TIME = 40;
        protected int timer;

        public EatBerriesGoal(final double speed, final int range, final int maxYDifference) {
            super(HelpfulFoxEntity.this, speed, range, maxYDifference);
        }

        public double getDesiredDistanceToTarget() {
            return 2.0;
        }

        public boolean shouldResetPath() {
            return this.tryingTime % 100 == 0;
        }

        protected boolean isTargetPos(WorldView world, BlockPos pos) {
            BlockState blockState = world.getBlockState(pos);
            return blockState.isOf(Blocks.SWEET_BERRY_BUSH) && (Integer)blockState.get(SweetBerryBushBlock.AGE) >= 2 || CaveVines.hasBerries(blockState);
        }

        public void tick() {
            if (this.hasReached()) {
                if (this.timer >= 40) {
                    this.eatBerries();
                } else {
                    ++this.timer;
                }
            } else if (!this.hasReached() && HelpfulFoxEntity.this.random.nextFloat() < 0.05F) {
                HelpfulFoxEntity.this.playSound(SoundEvents.ENTITY_FOX_SNIFF, 1.0F, 1.0F);
            }

            super.tick();
        }

        protected void eatBerries() {
            if (castToServerWorld(HelpfulFoxEntity.this.getWorld()).getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) {
                BlockState blockState = HelpfulFoxEntity.this.getWorld().getBlockState(this.targetPos);
                if (blockState.isOf(Blocks.SWEET_BERRY_BUSH)) {
                    this.pickSweetBerries(blockState);
                } else if (CaveVines.hasBerries(blockState)) {
                    this.pickGlowBerries(blockState);
                }

            }
        }

        private void pickGlowBerries(BlockState state) {
            CaveVines.pickBerries(HelpfulFoxEntity.this, state, HelpfulFoxEntity.this.getWorld(), this.targetPos);
        }

        private void pickSweetBerries(BlockState state) {
            int i = (Integer)state.get(SweetBerryBushBlock.AGE);
            state.with(SweetBerryBushBlock.AGE, 1);
            int j = 1 + HelpfulFoxEntity.this.getWorld().random.nextInt(2) + (i == 3 ? 1 : 0);
            ItemStack itemStack = HelpfulFoxEntity.this.getEquippedStack(EquipmentSlot.MAINHAND);
            if (itemStack.isEmpty()) {
                HelpfulFoxEntity.this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.SWEET_BERRIES));
                --j;
            }

            if (j > 0) {
                Block.dropStack(HelpfulFoxEntity.this.getWorld(), this.targetPos, new ItemStack(Items.SWEET_BERRIES, j));
            }

            HelpfulFoxEntity.this.playSound(SoundEvents.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1.0F, 1.0F);
            HelpfulFoxEntity.this.getWorld().setBlockState(this.targetPos, (BlockState)state.with(SweetBerryBushBlock.AGE, 1), Block.NOTIFY_LISTENERS);
            HelpfulFoxEntity.this.getWorld().emitGameEvent(GameEvent.BLOCK_CHANGE, this.targetPos, GameEvent.Emitter.of((Entity)HelpfulFoxEntity.this));
        }

        public boolean canStart() {
            return !HelpfulFoxEntity.this.isSleeping() && super.canStart();
        }

        public void start() {
            this.timer = 0;
            HelpfulFoxEntity.this.setSitting(false);
            super.start();
        }
    }

    private class LookAtEntityGoal extends net.minecraft.entity.ai.goal.LookAtEntityGoal {
        public LookAtEntityGoal(final MobEntity fox, final Class<? extends LivingEntity> targetType, final float range) {
            super(fox, targetType, range);
        }

        public boolean canStart() {
            return super.canStart() && !HelpfulFoxEntity.this.isWalking() && !HelpfulFoxEntity.this.isRollingHead();
        }

        public boolean shouldContinue() {
            return super.shouldContinue() && !HelpfulFoxEntity.this.isWalking() && !HelpfulFoxEntity.this.isRollingHead();
        }
    }

    class SitDownAndLookAroundGoal extends HelpfulFoxEntity.CalmDownGoal {
        private double lookX;
        private double lookZ;
        private int timer;
        private int counter;

        public SitDownAndLookAroundGoal() {
            super();
            this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
        }

        public boolean canStart() {
            return HelpfulFoxEntity.this.getAttacker() == null && HelpfulFoxEntity.this.getRandom().nextFloat() < 0.02F && !HelpfulFoxEntity.this.isSleeping() && HelpfulFoxEntity.this.getTarget() == null && HelpfulFoxEntity.this.getNavigation().isIdle() && !this.canCalmDown() && !HelpfulFoxEntity.this.isChasing() && !HelpfulFoxEntity.this.isInSneakingPose();
        }

        public boolean shouldContinue() {
            return this.counter > 0;
        }

        public void start() {
            this.chooseNewAngle();
            this.counter = 2 + HelpfulFoxEntity.this.getRandom().nextInt(3);
            HelpfulFoxEntity.this.setSitting(true);
            HelpfulFoxEntity.this.getNavigation().stop();
        }

        public void stop() {
            HelpfulFoxEntity.this.setSitting(false);
        }

        public void tick() {
            --this.timer;
            if (this.timer <= 0) {
                --this.counter;
                this.chooseNewAngle();
            }

            HelpfulFoxEntity.this.getLookControl().lookAt(HelpfulFoxEntity.this.getX() + this.lookX, HelpfulFoxEntity.this.getEyeY(), HelpfulFoxEntity.this.getZ() + this.lookZ, (float)HelpfulFoxEntity.this.getMaxHeadRotation(), (float)HelpfulFoxEntity.this.getMaxLookPitchChange());
        }

        private void chooseNewAngle() {
            double d = 6.283185307179586 * HelpfulFoxEntity.this.getRandom().nextDouble();
            this.lookX = Math.cos(d);
            this.lookZ = Math.sin(d);
            this.timer = this.getTickCount(80 + HelpfulFoxEntity.this.getRandom().nextInt(20));
        }
    }

    public static class FoxData extends PassiveEntity.PassiveData {
        public final HelpfulFoxEntity.Variant type;

        public FoxData(HelpfulFoxEntity.Variant type) {
            super(false);
            this.type = type;
        }
    }

    private abstract class CalmDownGoal extends Goal {
        private final TargetPredicate WORRIABLE_ENTITY_PREDICATE = TargetPredicate.createAttackable().setBaseMaxDistance(12.0).ignoreVisibility().setPredicate(HelpfulFoxEntity.this.new WorriableEntityFilter());

        CalmDownGoal() {
        }

        protected boolean isAtFavoredLocation() {
            BlockPos blockPos = BlockPos.ofFloored(HelpfulFoxEntity.this.getX(), HelpfulFoxEntity.this.getBoundingBox().maxY, HelpfulFoxEntity.this.getZ());
            return !HelpfulFoxEntity.this.getWorld().isSkyVisible(blockPos) && HelpfulFoxEntity.this.getPathfindingFavor(blockPos) >= 0.0F;
        }

        protected boolean canCalmDown() {
            return !castToServerWorld(HelpfulFoxEntity.this.getWorld()).getTargets(LivingEntity.class, this.WORRIABLE_ENTITY_PREDICATE, HelpfulFoxEntity.this, HelpfulFoxEntity.this.getBoundingBox().expand(12.0, 6.0, 12.0)).isEmpty();
        }
    }

    public class WorriableEntityFilter implements TargetPredicate.EntityPredicate {
        public boolean test(LivingEntity livingEntity, ServerWorld serverWorld) {
            if (livingEntity instanceof FoxEntity) {
                return false;
            } else if (!(livingEntity instanceof ChickenEntity) && !(livingEntity instanceof RabbitEntity) && !(livingEntity instanceof HostileEntity)) {
                if (livingEntity instanceof TameableEntity) {
                    return !((TameableEntity)livingEntity).isTamed();
                } else {
                    if (livingEntity instanceof PlayerEntity) {
                        PlayerEntity playerEntity = (PlayerEntity)livingEntity;
                        if (playerEntity.isSpectator() || playerEntity.isCreative()) {
                            return false;
                        }
                    }

                    if (HelpfulFoxEntity.this.canTrust(livingEntity)) {
                        return false;
                    } else {
                        return !livingEntity.isSleeping() && !livingEntity.isSneaky();
                    }
                }
            } else {
                return true;
            }
        }
    }

    public final boolean cannotFollowOwner() {
//        return this.isSitting() || this.hasVehicle() || this.mightBeLeashed() || this.getOwner() != null && this.getOwner().isSpectator();
        return this.hasVehicle() || this.mightBeLeashed() || this.getOwner() != null && this.getOwner().isSpectator();
    }

    public boolean shouldTryTeleportToOwner() {
        LivingEntity livingEntity = this.getOwner();
        return livingEntity != null && this.squaredDistanceTo(this.getOwner()) >= (double)144.0F;
    }

    public void tryTeleportToOwner() {
        LivingEntity livingEntity = this.getOwner();
        if (livingEntity != null) {
            this.tryTeleportNear(livingEntity.getBlockPos());
        }

    }

    private void tryTeleportNear(BlockPos pos) {
        for(int i = 0; i < 10; ++i) {
            int j = this.random.nextBetween(-3, 3);
            int k = this.random.nextBetween(-3, 3);
            if (Math.abs(j) >= 2 || Math.abs(k) >= 2) {
                int l = this.random.nextBetween(-1, 1);
                if (this.tryTeleportTo(pos.getX() + j, pos.getY() + l, pos.getZ() + k)) {
                    return;
                }
            }
        }

    }

    private boolean tryTeleportTo(int x, int y, int z) {
        if (!this.canTeleportTo(new BlockPos(x, y, z))) {
            return false;
        } else {
            this.refreshPositionAndAngles((double)x + (double)0.5F, (double)y, (double)z + (double)0.5F, this.getYaw(), this.getPitch());
            this.navigation.stop();
            return true;
        }
    }

    private boolean canTeleportTo(BlockPos pos) {
        PathNodeType pathNodeType = LandPathNodeMaker.getLandNodeType(this, pos);
        if (pathNodeType != PathNodeType.WALKABLE) {
            return false;
        } else {
            BlockState blockState = this.getWorld().getBlockState(pos.down());
            if (!this.canTeleportOntoLeaves() && blockState.getBlock() instanceof LeavesBlock) {
                return false;
            } else {
                BlockPos blockPos = pos.subtract(this.getBlockPos());
                return this.getWorld().isSpaceEmpty(this, this.getBoundingBox().offset(blockPos));
            }
        }
    }

    boolean canTeleportOntoLeaves() {return true;}

    public class CustomFollowOwnerGoal extends Goal {
        private final HelpfulFoxEntity helpfulFox;
        @Nullable
        private LivingEntity owner;
        private final double speed;
        private final EntityNavigation navigation;
        private int updateCountdownTicks;
        private final float maxDistance;
        private final float minDistance;
        private float oldWaterPathfindingPenalty;

        public CustomFollowOwnerGoal(HelpfulFoxEntity tameable, double speed, float minDistance, float maxDistance) {
            this.helpfulFox = tameable;
            this.speed = speed;
            this.navigation = tameable.getNavigation();
            this.minDistance = minDistance;
            this.maxDistance = maxDistance;
            this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
            if (!(tameable.getNavigation() instanceof MobNavigation) && !(tameable.getNavigation() instanceof BirdNavigation)) {
                throw new IllegalArgumentException("Unsupported mob type for FollowOwnerGoal");
            }
        }

        public boolean canStart() {
            LivingEntity livingEntity = this.helpfulFox.getOwner();
            if (livingEntity == null) {
                return false;
            } else if (this.helpfulFox.cannotFollowOwner()) {
                return false;
            } else if (this.helpfulFox.squaredDistanceTo(livingEntity) < (double)(this.minDistance * this.minDistance)) {
                return false;
            } else {
                this.owner = livingEntity;
                return true;
            }
        }

        public boolean shouldContinue() {
            if (this.navigation.isIdle()) {
                return false;
            } else if (this.helpfulFox.cannotFollowOwner()) {
                return false;
            } else {
                return !(this.helpfulFox.squaredDistanceTo(this.owner) <= (double)(this.maxDistance * this.maxDistance));
            }
        }

        public void start() {
            this.updateCountdownTicks = 0;
            this.oldWaterPathfindingPenalty = this.helpfulFox.getPathfindingPenalty(PathNodeType.WATER);
            this.helpfulFox.setPathfindingPenalty(PathNodeType.WATER, 0.0F);
        }

        public void stop() {
            this.owner = null;
            this.navigation.stop();
            this.helpfulFox.setPathfindingPenalty(PathNodeType.WATER, this.oldWaterPathfindingPenalty);
        }

        public void tick() {
            boolean bl = this.helpfulFox.shouldTryTeleportToOwner();
            if (!bl) {
                this.helpfulFox.getLookControl().lookAt(this.owner, 10.0F, (float)this.helpfulFox.getMaxLookPitchChange());
            }

            if (--this.updateCountdownTicks <= 0) {
                this.updateCountdownTicks = this.getTickCount(10);
                if (bl) {
                    this.helpfulFox.tryTeleportToOwner();
                } else {
                    this.navigation.startMovingTo(this.owner, this.speed);
                }

            }
        }
    }
}