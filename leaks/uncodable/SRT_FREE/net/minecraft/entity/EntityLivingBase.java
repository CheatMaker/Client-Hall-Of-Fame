package net.minecraft.entity;

import com.google.common.base.Predicates;
import com.google.common.collect.Maps;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.BaseAttributeMap;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.ai.attributes.ServersideAttributeMap;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.network.play.server.S04PacketEntityEquipment;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.network.play.server.S0DPacketCollectItem;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.potion.PotionHelper;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.CombatTracker;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntitySelectors;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

public abstract class EntityLivingBase extends Entity {
   private static final UUID sprintingSpeedBoostModifierUUID = UUID.fromString("662A6B8D-DA3E-4C1C-8813-96EA6097278D");
   private static final AttributeModifier sprintingSpeedBoostModifier = new AttributeModifier(
         sprintingSpeedBoostModifierUUID, "Sprinting speed boost", 0.3F, 2
      )
      .setSaved(false);
   private BaseAttributeMap attributeMap;
   private final CombatTracker _combatTracker = new CombatTracker(this);
   private final Map<Integer, PotionEffect> activePotionsMap = Maps.newHashMap();
   private final ItemStack[] previousEquipment = new ItemStack[5];
   public boolean isSwingInProgress;
   public int swingProgressInt;
   public int arrowHitTimer;
   public int hurtTime;
   public int maxHurtTime;
   public float attackedAtYaw;
   public int deathTime;
   public float prevSwingProgress;
   public float swingProgress;
   public float prevLimbSwingAmount;
   public float limbSwingAmount;
   public float limbSwing;
   public final int maxHurtResistantTime = 20;
   public float prevCameraPitch;
   public float cameraPitch;
   public final float field_70769_ao;
   public final float field_70770_ap;
   public float renderYawOffset;
   public float prevRenderYawOffset;
   public float rotationYawHead;
   public float prevRotationYawHead;
   public float jumpMovementFactor = 0.02F;
   protected EntityPlayer attackingPlayer;
   protected int recentlyHit;
   protected boolean dead;
   protected int entityAge;
   protected float prevOnGroundSpeedFactor;
   protected float onGroundSpeedFactor;
   protected float movedDistance;
   protected float prevMovedDistance;
   protected float field_70741_aB;
   protected int scoreValue;
   protected float lastDamage;
   protected boolean isJumping;
   public float moveStrafing;
   public float moveForward;
   protected float randomYawVelocity;
   protected int newPosRotationIncrements;
   protected double newPosX;
   protected double newPosY;
   protected double newPosZ;
   protected double newRotationYaw;
   protected double newRotationPitch;
   private boolean potionsNeedUpdate = true;
   private EntityLivingBase entityLivingToAttack;
   private int revengeTimer;
   private EntityLivingBase lastAttacker;
   private int lastAttackerTime;
   private float landMovementFactor;
   private int jumpTicks;
   private float absorptionAmount;

   @Override
   public void onKillCommand() {
      this.attackEntityFrom(DamageSource.outOfWorld, Float.MAX_VALUE);
   }

   public EntityLivingBase(World worldIn) {
      super(worldIn);
      this.applyEntityAttributes();
      this.setHealth(this.getMaxHealth());
      this.preventEntitySpawning = true;
      this.field_70770_ap = (float)((Math.random() + 1.0) * 0.01F);
      this.setPosition(this.posX, this.posY, this.posZ);
      this.field_70769_ao = (float)Math.random() * 12398.0F;
      this.rotationYaw = (float)(Math.random() * Math.PI * 2.0);
      this.rotationYawHead = this.rotationYaw;
      this.stepHeight = 0.6F;
   }

   @Override
   protected void entityInit() {
      this.dataWatcher.addObject(7, 0);
      this.dataWatcher.addObject(8, (byte)0);
      this.dataWatcher.addObject(9, (byte)0);
      this.dataWatcher.addObject(6, 1.0F);
   }

   protected void applyEntityAttributes() {
      this.getAttributeMap().registerAttribute(SharedMonsterAttributes.maxHealth);
      this.getAttributeMap().registerAttribute(SharedMonsterAttributes.knockbackResistance);
      this.getAttributeMap().registerAttribute(SharedMonsterAttributes.movementSpeed);
   }

   @Override
   protected void updateFallState(double y, boolean onGroundIn, Block blockIn, BlockPos pos) {
      if (!this.isInWater()) {
         this.handleWaterMovement();
      }

      if (!this.worldObj.isRemote && this.fallDistance > 3.0F && onGroundIn) {
         IBlockState iblockstate = this.worldObj.getBlockState(pos);
         Block block = iblockstate.getBlock();
         float f = (float)MathHelper.ceiling_float_int(this.fallDistance - 3.0F);
         if (block.getMaterial() != Material.air) {
            double d0 = (double)Math.min(0.2F + f / 15.0F, 10.0F);
            if (d0 > 2.5) {
               d0 = 2.5;
            }

            int i = (int)(150.0 * d0);
            ((WorldServer)this.worldObj)
               .spawnParticle(EnumParticleTypes.BLOCK_DUST, this.posX, this.posY, this.posZ, i, 0.0, 0.0, 0.0, 0.15F, Block.getStateId(iblockstate));
         }
      }

      super.updateFallState(y, onGroundIn, blockIn, pos);
   }

   public boolean canBreatheUnderwater() {
      return false;
   }

   @Override
   public void onEntityUpdate() {
      this.prevSwingProgress = this.swingProgress;
      super.onEntityUpdate();
      this.worldObj.theProfiler.startSection("livingEntityBaseTick");
      boolean flag = this instanceof EntityPlayer;
      if (this.isEntityAlive()) {
         if (this.isEntityInsideOpaqueBlock()) {
            this.attackEntityFrom(DamageSource.inWall, 1.0F);
         } else if (flag && !this.worldObj.getWorldBorder().contains(this.getEntityBoundingBox())) {
            double d0 = this.worldObj.getWorldBorder().getClosestDistance(this) + this.worldObj.getWorldBorder().getDamageBuffer();
            if (d0 < 0.0) {
               this.attackEntityFrom(DamageSource.inWall, (float)Math.max(1, MathHelper.floor_double(-d0 * this.worldObj.getWorldBorder().getDamageAmount())));
            }
         }
      }

      if (this.isImmuneToFire() || this.worldObj.isRemote) {
         this.extinguish();
      }

      boolean flag1 = flag && ((EntityPlayer)this).capabilities.disableDamage;
      if (this.isEntityAlive()) {
         if (this.isInsideOfMaterial(Material.water)) {
            if (!this.canBreatheUnderwater() && !this.isPotionActive(Potion.waterBreathing.id) && !flag1) {
               this.setAir(this.decreaseAirSupply(this.getAir()));
               if (this.getAir() == -20) {
                  this.setAir(0);

                  for(int i = 0; i < 8; ++i) {
                     float f = this.rand.nextFloat() - this.rand.nextFloat();
                     float f1 = this.rand.nextFloat() - this.rand.nextFloat();
                     float f2 = this.rand.nextFloat() - this.rand.nextFloat();
                     this.worldObj
                        .spawnParticle(
                           EnumParticleTypes.WATER_BUBBLE,
                           this.posX + (double)f,
                           this.posY + (double)f1,
                           this.posZ + (double)f2,
                           this.motionX,
                           this.motionY,
                           this.motionZ
                        );
                  }

                  this.attackEntityFrom(DamageSource.drown, 2.0F);
               }
            }

            if (!this.worldObj.isRemote && this.isRiding() && this.ridingEntity instanceof EntityLivingBase) {
               this.mountEntity(null);
            }
         } else {
            this.setAir(300);
         }
      }

      if (this.isEntityAlive() && this.isWet()) {
         this.extinguish();
      }

      this.prevCameraPitch = this.cameraPitch;
      if (this.hurtTime > 0) {
         --this.hurtTime;
      }

      if (this.hurtResistantTime > 0 && !(this instanceof EntityPlayerMP)) {
         --this.hurtResistantTime;
      }

      if (this.getHealth() <= 0.0F) {
         this.onDeathUpdate();
      }

      if (this.recentlyHit > 0) {
         --this.recentlyHit;
      } else {
         this.attackingPlayer = null;
      }

      if (this.lastAttacker != null && !this.lastAttacker.isEntityAlive()) {
         this.lastAttacker = null;
      }

      if (this.entityLivingToAttack != null) {
         if (!this.entityLivingToAttack.isEntityAlive()) {
            this.setRevengeTarget(null);
         } else if (this.ticksExisted - this.revengeTimer > 100) {
            this.setRevengeTarget(null);
         }
      }

      this.updatePotionEffects();
      this.prevMovedDistance = this.movedDistance;
      this.prevRenderYawOffset = this.renderYawOffset;
      this.prevRotationYawHead = this.rotationYawHead;
      this.prevRotationYaw = this.rotationYaw;
      this.prevRotationPitch = this.rotationPitch;
      this.worldObj.theProfiler.endSection();
   }

   public boolean isChild() {
      return false;
   }

   protected void onDeathUpdate() {
      ++this.deathTime;
      if (this.deathTime == 20) {
         if (!this.worldObj.isRemote
            && (this.recentlyHit > 0 || this.isPlayer())
            && this.canDropLoot()
            && this.worldObj.getGameRules().getGameRuleBooleanValue("doMobLoot")) {
            int i = this.getExperiencePoints(this.attackingPlayer);

            while(i > 0) {
               int j = EntityXPOrb.getXPSplit(i);
               i -= j;
               this.worldObj.spawnEntityInWorld(new EntityXPOrb(this.worldObj, this.posX, this.posY, this.posZ, j));
            }
         }

         this.setDead();

         for(int k = 0; k < 20; ++k) {
            double d2 = this.rand.nextGaussian() * 0.02;
            double d0 = this.rand.nextGaussian() * 0.02;
            double d1 = this.rand.nextGaussian() * 0.02;
            this.worldObj
               .spawnParticle(
                  EnumParticleTypes.EXPLOSION_NORMAL,
                  this.posX + (double)(this.rand.nextFloat() * this.width * 2.0F) - (double)this.width,
                  this.posY + (double)(this.rand.nextFloat() * this.height),
                  this.posZ + (double)(this.rand.nextFloat() * this.width * 2.0F) - (double)this.width,
                  d2,
                  d0,
                  d1
               );
         }
      }
   }

   protected boolean canDropLoot() {
      return !this.isChild();
   }

   protected int decreaseAirSupply(int p_70682_1_) {
      int i = EnchantmentHelper.getRespiration(this);
      return i > 0 && this.rand.nextInt(i + 1) > 0 ? p_70682_1_ : p_70682_1_ - 1;
   }

   protected int getExperiencePoints(EntityPlayer player) {
      return 0;
   }

   protected boolean isPlayer() {
      return false;
   }

   public Random getRNG() {
      return this.rand;
   }

   public EntityLivingBase getAITarget() {
      return this.entityLivingToAttack;
   }

   public int getRevengeTimer() {
      return this.revengeTimer;
   }

   public void setRevengeTarget(EntityLivingBase livingBase) {
      this.entityLivingToAttack = livingBase;
      this.revengeTimer = this.ticksExisted;
   }

   public EntityLivingBase getLastAttacker() {
      return this.lastAttacker;
   }

   public int getLastAttackerTime() {
      return this.lastAttackerTime;
   }

   public void setLastAttacker(Entity entityIn) {
      if (entityIn instanceof EntityLivingBase) {
         this.lastAttacker = (EntityLivingBase)entityIn;
      } else {
         this.lastAttacker = null;
      }

      this.lastAttackerTime = this.ticksExisted;
   }

   public int getAge() {
      return this.entityAge;
   }

   @Override
   public void writeEntityToNBT(NBTTagCompound tagCompound) {
      tagCompound.setFloat("HealF", this.getHealth());
      tagCompound.setShort("Health", (short)((int)Math.ceil((double)this.getHealth())));
      tagCompound.setShort("HurtTime", (short)this.hurtTime);
      tagCompound.setInteger("HurtByTimestamp", this.revengeTimer);
      tagCompound.setShort("DeathTime", (short)this.deathTime);
      tagCompound.setFloat("AbsorptionAmount", this.getAbsorptionAmount());

      for(ItemStack itemstack : this.getInventory()) {
         if (itemstack != null) {
            this.attributeMap.removeAttributeModifiers(itemstack.getAttributeModifiers());
         }
      }

      tagCompound.setTag("Attributes", SharedMonsterAttributes.writeBaseAttributeMapToNBT(this.getAttributeMap()));

      for(ItemStack itemstack1 : this.getInventory()) {
         if (itemstack1 != null) {
            this.attributeMap.applyAttributeModifiers(itemstack1.getAttributeModifiers());
         }
      }

      if (!this.activePotionsMap.isEmpty()) {
         NBTTagList nbttaglist = new NBTTagList();

         for(PotionEffect potioneffect : this.activePotionsMap.values()) {
            nbttaglist.appendTag(potioneffect.writeCustomPotionEffectToNBT(new NBTTagCompound()));
         }

         tagCompound.setTag("ActiveEffects", nbttaglist);
      }
   }

   @Override
   public void readEntityFromNBT(NBTTagCompound tagCompund) {
      this.setAbsorptionAmount(tagCompund.getFloat("AbsorptionAmount"));
      if (tagCompund.hasKey("Attributes", 9) && this.worldObj != null && !this.worldObj.isRemote) {
         SharedMonsterAttributes.func_151475_a(this.getAttributeMap(), tagCompund.getTagList("Attributes", 10));
      }

      if (tagCompund.hasKey("ActiveEffects", 9)) {
         NBTTagList nbttaglist = tagCompund.getTagList("ActiveEffects", 10);

         for(int i = 0; i < nbttaglist.tagCount(); ++i) {
            NBTTagCompound nbttagcompound = nbttaglist.getCompoundTagAt(i);
            PotionEffect potioneffect = PotionEffect.readCustomPotionEffectFromNBT(nbttagcompound);
            if (potioneffect != null) {
               this.activePotionsMap.put(potioneffect.getPotionID(), potioneffect);
            }
         }
      }

      if (tagCompund.hasKey("HealF", 99)) {
         this.setHealth(tagCompund.getFloat("HealF"));
      } else {
         NBTBase nbtbase = tagCompund.getTag("Health");
         if (nbtbase == null) {
            this.setHealth(this.getMaxHealth());
         } else if (nbtbase.getId() == 5) {
            this.setHealth(((NBTTagFloat)nbtbase).getFloat());
         } else if (nbtbase.getId() == 2) {
            this.setHealth((float)((NBTTagShort)nbtbase).getShort());
         }
      }

      this.hurtTime = tagCompund.getShort("HurtTime");
      this.deathTime = tagCompund.getShort("DeathTime");
      this.revengeTimer = tagCompund.getInteger("HurtByTimestamp");
   }

   protected void updatePotionEffects() {
      Iterator<Integer> iterator = this.activePotionsMap.keySet().iterator();

      while(iterator.hasNext()) {
         Integer integer = iterator.next();
         PotionEffect potioneffect = this.activePotionsMap.get(integer);
         if (!potioneffect.onUpdate(this)) {
            if (!this.worldObj.isRemote) {
               iterator.remove();
               this.onFinishedPotionEffect(potioneffect);
            }
         } else if (potioneffect.getDuration() % 600 == 0) {
            this.onChangedPotionEffect(potioneffect, false);
         }
      }

      if (this.potionsNeedUpdate) {
         if (!this.worldObj.isRemote) {
            this.updatePotionMetadata();
         }

         this.potionsNeedUpdate = false;
      }

      int i = this.dataWatcher.getWatchableObjectInt(7);
      boolean flag1 = this.dataWatcher.getWatchableObjectByte(8) > 0;
      if (i > 0) {
         boolean flag;
         if (!this.isInvisible()) {
            flag = this.rand.nextBoolean();
         } else {
            flag = this.rand.nextInt(15) == 0;
         }

         if (flag1) {
            flag &= this.rand.nextInt(5) == 0;
         }

         if (flag) {
            double d0 = (double)(i >> 16 & 0xFF) / 255.0;
            double d1 = (double)(i >> 8 & 0xFF) / 255.0;
            double d2 = (double)(i & 0xFF) / 255.0;
            this.worldObj
               .spawnParticle(
                  flag1 ? EnumParticleTypes.SPELL_MOB_AMBIENT : EnumParticleTypes.SPELL_MOB,
                  this.posX + (this.rand.nextDouble() - 0.5) * (double)this.width,
                  this.posY + this.rand.nextDouble() * (double)this.height,
                  this.posZ + (this.rand.nextDouble() - 0.5) * (double)this.width,
                  d0,
                  d1,
                  d2
               );
         }
      }
   }

   protected void updatePotionMetadata() {
      if (this.activePotionsMap.isEmpty()) {
         this.resetPotionEffectMetadata();
         this.setInvisible(false);
      } else {
         int i = PotionHelper.calcPotionLiquidColor(this.activePotionsMap.values());
         this.dataWatcher.updateObject(8, (byte)(PotionHelper.getAreAmbient(this.activePotionsMap.values()) ? 1 : 0));
         this.dataWatcher.updateObject(7, i);
         this.setInvisible(this.isPotionActive(Potion.invisibility.id));
      }
   }

   protected void resetPotionEffectMetadata() {
      this.dataWatcher.updateObject(8, (byte)0);
      this.dataWatcher.updateObject(7, 0);
   }

   public void clearActivePotions() {
      Iterator<Integer> iterator = this.activePotionsMap.keySet().iterator();

      while(iterator.hasNext()) {
         Integer integer = iterator.next();
         PotionEffect potioneffect = this.activePotionsMap.get(integer);
         if (!this.worldObj.isRemote) {
            iterator.remove();
            this.onFinishedPotionEffect(potioneffect);
         }
      }
   }

   public Collection<PotionEffect> getActivePotionEffects() {
      return this.activePotionsMap.values();
   }

   public boolean isPotionActive(int potionId) {
      return this.activePotionsMap.containsKey(potionId);
   }

   public boolean isPotionActive(Potion potionIn) {
      return this.activePotionsMap.containsKey(potionIn.id);
   }

   public PotionEffect getActivePotionEffect(Potion potionIn) {
      return this.activePotionsMap.get(potionIn.id);
   }

   public void addPotionEffect(PotionEffect potioneffectIn) {
      if (this.isPotionApplicable(potioneffectIn)) {
         if (this.activePotionsMap.containsKey(potioneffectIn.getPotionID())) {
            this.activePotionsMap.get(potioneffectIn.getPotionID()).combine(potioneffectIn);
            this.onChangedPotionEffect(this.activePotionsMap.get(potioneffectIn.getPotionID()), true);
         } else {
            this.activePotionsMap.put(potioneffectIn.getPotionID(), potioneffectIn);
            this.onNewPotionEffect(potioneffectIn);
         }
      }
   }

   public boolean isPotionApplicable(PotionEffect potioneffectIn) {
      if (this.getCreatureAttribute() != EnumCreatureAttribute.UNDEAD) {
         return true;
      } else {
         int i = potioneffectIn.getPotionID();
         return i != Potion.regeneration.id && i != Potion.poison.id;
      }
   }

   public boolean isEntityUndead() {
      return this.getCreatureAttribute() == EnumCreatureAttribute.UNDEAD;
   }

   public void removePotionEffectClient(int potionId) {
      this.activePotionsMap.remove(potionId);
   }

   public void removePotionEffect(int potionId) {
      PotionEffect potioneffect = this.activePotionsMap.remove(potionId);
      if (potioneffect != null) {
         this.onFinishedPotionEffect(potioneffect);
      }
   }

   protected void onNewPotionEffect(PotionEffect id) {
      this.potionsNeedUpdate = true;
      if (!this.worldObj.isRemote) {
         Potion.potionTypes[id.getPotionID()].applyAttributesModifiersToEntity(this, this.getAttributeMap(), id.getAmplifier());
      }
   }

   protected void onChangedPotionEffect(PotionEffect id, boolean p_70695_2_) {
      this.potionsNeedUpdate = true;
      if (p_70695_2_ && !this.worldObj.isRemote) {
         Potion.potionTypes[id.getPotionID()].removeAttributesModifiersFromEntity(this, this.getAttributeMap(), id.getAmplifier());
         Potion.potionTypes[id.getPotionID()].applyAttributesModifiersToEntity(this, this.getAttributeMap(), id.getAmplifier());
      }
   }

   protected void onFinishedPotionEffect(PotionEffect p_70688_1_) {
      this.potionsNeedUpdate = true;
      if (!this.worldObj.isRemote) {
         Potion.potionTypes[p_70688_1_.getPotionID()].removeAttributesModifiersFromEntity(this, this.getAttributeMap(), p_70688_1_.getAmplifier());
      }
   }

   public void heal(float healAmount) {
      float f = this.getHealth();
      if (f > 0.0F) {
         this.setHealth(f + healAmount);
      }
   }

   public final float getHealth() {
      return this.dataWatcher.getWatchableObjectFloat(6);
   }

   public void setHealth(float health) {
      this.dataWatcher.updateObject(6, MathHelper.clamp_float(health, 0.0F, this.getMaxHealth()));
   }

   @Override
   public boolean attackEntityFrom(DamageSource source, float amount) {
      if (this.isEntityInvulnerable(source)) {
         return false;
      } else if (this.worldObj.isRemote) {
         return false;
      } else {
         this.entityAge = 0;
         if (this.getHealth() <= 0.0F) {
            return false;
         } else if (source.isFireDamage() && this.isPotionActive(Potion.fireResistance)) {
            return false;
         } else {
            if ((source == DamageSource.anvil || source == DamageSource.fallingBlock) && this.getEquipmentInSlot(4) != null) {
               this.getEquipmentInSlot(4).damageItem((int)(amount * 4.0F + this.rand.nextFloat() * amount * 2.0F), this);
               amount *= 0.75F;
            }

            this.limbSwingAmount = 1.5F;
            boolean flag = true;
            if ((float)this.hurtResistantTime > 20.0F / 2.0F) {
               if (amount <= this.lastDamage) {
                  return false;
               }

               this.damageEntity(source, amount - this.lastDamage);
               this.lastDamage = amount;
               flag = false;
            } else {
               this.lastDamage = amount;
               this.hurtResistantTime = 20;
               this.damageEntity(source, amount);
               this.hurtTime = this.maxHurtTime = 10;
            }

            this.attackedAtYaw = 0.0F;
            Entity entity = source.getEntity();
            if (entity != null) {
               if (entity instanceof EntityLivingBase) {
                  this.setRevengeTarget((EntityLivingBase)entity);
               }

               if (entity instanceof EntityPlayer) {
                  this.recentlyHit = 100;
                  this.attackingPlayer = (EntityPlayer)entity;
               } else if (entity instanceof EntityWolf) {
                  EntityWolf entitywolf = (EntityWolf)entity;
                  if (entitywolf.isTamed()) {
                     this.recentlyHit = 100;
                     this.attackingPlayer = null;
                  }
               }
            }

            if (flag) {
               this.worldObj.setEntityState(this, (byte)2);
               if (source != DamageSource.drown) {
                  this.setBeenAttacked();
               }

               if (entity != null) {
                  double d1 = entity.posX - this.posX;

                  double d0;
                  for(d0 = entity.posZ - this.posZ; d1 * d1 + d0 * d0 < 1.0E-4; d0 = (Math.random() - Math.random()) * 0.01) {
                     d1 = (Math.random() - Math.random()) * 0.01;
                  }

                  this.attackedAtYaw = (float)(MathHelper.func_181159_b(d0, d1) * 180.0 / Math.PI - (double)this.rotationYaw);
                  this.knockBack(entity, amount, d1, d0);
               } else {
                  this.attackedAtYaw = (float)((int)(Math.random() * 2.0) * 180);
               }
            }

            if (this.getHealth() <= 0.0F) {
               String s = this.getDeathSound();
               if (flag && s != null) {
                  this.playSound(s, this.getSoundVolume(), this.getSoundPitch());
               }

               this.onDeath(source);
            } else {
               String s1 = this.getHurtSound();
               if (flag && s1 != null) {
                  this.playSound(s1, this.getSoundVolume(), this.getSoundPitch());
               }
            }

            return true;
         }
      }
   }

   public void renderBrokenItemStack(ItemStack stack) {
      this.playSound("random.break", 0.8F, 0.8F + this.worldObj.rand.nextFloat() * 0.4F);

      for(int i = 0; i < 5; ++i) {
         Vec3 vec3 = new Vec3(((double)this.rand.nextFloat() - 0.5) * 0.1, Math.random() * 0.1 + 0.1, 0.0);
         vec3 = vec3.rotatePitch(-this.rotationPitch * (float) Math.PI / 180.0F);
         vec3 = vec3.rotateYaw(-this.rotationYaw * (float) Math.PI / 180.0F);
         double d0 = (double)(-this.rand.nextFloat()) * 0.6 - 0.3;
         Vec3 vec31 = new Vec3(((double)this.rand.nextFloat() - 0.5) * 0.3, d0, 0.6);
         vec31 = vec31.rotatePitch(-this.rotationPitch * (float) Math.PI / 180.0F);
         vec31 = vec31.rotateYaw(-this.rotationYaw * (float) Math.PI / 180.0F);
         vec31 = vec31.addVector(this.posX, this.posY + (double)this.getEyeHeight(), this.posZ);
         this.worldObj
            .spawnParticle(
               EnumParticleTypes.ITEM_CRACK,
               vec31.xCoord,
               vec31.yCoord,
               vec31.zCoord,
               vec3.xCoord,
               vec3.yCoord + 0.05,
               vec3.zCoord,
               Item.getIdFromItem(stack.getItem())
            );
      }
   }

   public void onDeath(DamageSource cause) {
      Entity entity = cause.getEntity();
      EntityLivingBase entitylivingbase = this.func_94060_bK();
      if (this.scoreValue >= 0 && entitylivingbase != null) {
         entitylivingbase.addToPlayerScore(this, this.scoreValue);
      }

      if (entity != null) {
         entity.onKillEntity(this);
      }

      this.dead = true;
      this.getCombatTracker().reset();
      if (!this.worldObj.isRemote) {
         int i = 0;
         if (entity instanceof EntityPlayer) {
            i = EnchantmentHelper.getLootingModifier((EntityLivingBase)entity);
         }

         if (this.canDropLoot() && this.worldObj.getGameRules().getGameRuleBooleanValue("doMobLoot")) {
            this.dropFewItems(this.recentlyHit > 0, i);
            this.dropEquipment(this.recentlyHit > 0, i);
            if (this.recentlyHit > 0 && this.rand.nextFloat() < 0.025F + (float)i * 0.01F) {
               this.addRandomDrop();
            }
         }
      }

      this.worldObj.setEntityState(this, (byte)3);
   }

   protected void dropEquipment(boolean p_82160_1_, int p_82160_2_) {
   }

   public void knockBack(Entity entityIn, float p_70653_2_, double p_70653_3_, double p_70653_5_) {
      if (this.rand.nextDouble() >= this.getEntityAttribute(SharedMonsterAttributes.knockbackResistance).getAttributeValue()) {
         this.isAirBorne = true;
         float f = MathHelper.sqrt_double(p_70653_3_ * p_70653_3_ + p_70653_5_ * p_70653_5_);
         float f1 = 0.4F;
         this.motionX /= 2.0;
         this.motionY /= 2.0;
         this.motionZ /= 2.0;
         this.motionX -= p_70653_3_ / (double)f * (double)f1;
         this.motionY += (double)f1;
         this.motionZ -= p_70653_5_ / (double)f * (double)f1;
         if (this.motionY > 0.4F) {
            this.motionY = 0.4F;
         }
      }
   }

   protected String getHurtSound() {
      return "game.neutral.hurt";
   }

   protected String getDeathSound() {
      return "game.neutral.die";
   }

   protected void addRandomDrop() {
   }

   protected void dropFewItems(boolean p_70628_1_, int p_70628_2_) {
   }

   public boolean isOnLadder() {
      int i = MathHelper.floor_double(this.posX);
      int j = MathHelper.floor_double(this.getEntityBoundingBox().minY);
      int k = MathHelper.floor_double(this.posZ);
      Block block = this.worldObj.getBlockState(new BlockPos(i, j, k)).getBlock();
      return (block == Blocks.ladder || block == Blocks.vine) && (!(this instanceof EntityPlayer) || !((EntityPlayer)this).isSpectator());
   }

   @Override
   public boolean isEntityAlive() {
      return !this.isDead && this.getHealth() > 0.0F;
   }

   @Override
   public void fall(float distance, float damageMultiplier) {
      super.fall(distance, damageMultiplier);
      PotionEffect potioneffect = this.getActivePotionEffect(Potion.jump);
      float f = potioneffect != null ? (float)(potioneffect.getAmplifier() + 1) : 0.0F;
      int i = MathHelper.ceiling_float_int((distance - 3.0F - f) * damageMultiplier);
      if (i > 0) {
         this.playSound(this.getFallSoundString(i), 1.0F, 1.0F);
         this.attackEntityFrom(DamageSource.fall, (float)i);
         int j = MathHelper.floor_double(this.posX);
         int k = MathHelper.floor_double(this.posY - 0.2F);
         int l = MathHelper.floor_double(this.posZ);
         Block block = this.worldObj.getBlockState(new BlockPos(j, k, l)).getBlock();
         if (block.getMaterial() != Material.air) {
            Block.SoundType block$soundtype = block.stepSound;
            this.playSound(block$soundtype.getStepSound(), block$soundtype.getVolume() * 0.5F, block$soundtype.getFrequency() * 0.75F);
         }
      }
   }

   protected String getFallSoundString(int damageValue) {
      return damageValue > 4 ? "game.neutral.hurt.fall.big" : "game.neutral.hurt.fall.small";
   }

   @Override
   public void performHurtAnimation() {
      this.hurtTime = this.maxHurtTime = 10;
      this.attackedAtYaw = 0.0F;
   }

   public int getTotalArmorValue() {
      int i = 0;

      for(ItemStack itemstack : this.getInventory()) {
         if (itemstack != null && itemstack.getItem() instanceof ItemArmor) {
            int j = ((ItemArmor)itemstack.getItem()).damageReduceAmount;
            i += j;
         }
      }

      return i;
   }

   protected void damageArmor(float p_70675_1_) {
   }

   protected float applyArmorCalculations(DamageSource source, float damage) {
      if (!source.isUnblockable()) {
         int i = 25 - this.getTotalArmorValue();
         float f = damage * (float)i;
         this.damageArmor(damage);
         damage = f / 25.0F;
      }

      return damage;
   }

   protected float applyPotionDamageCalculations(DamageSource source, float damage) {
      if (source.isDamageAbsolute()) {
         return damage;
      } else {
         if (this.isPotionActive(Potion.resistance) && source != DamageSource.outOfWorld) {
            int i = (this.getActivePotionEffect(Potion.resistance).getAmplifier() + 1) * 5;
            int j = 25 - i;
            float f = damage * (float)j;
            damage = f / 25.0F;
         }

         if (damage <= 0.0F) {
            return 0.0F;
         } else {
            int k = EnchantmentHelper.getEnchantmentModifierDamage(this.getInventory(), source);
            if (k > 20) {
               k = 20;
            }

            if (k > 0) {
               int l = 25 - k;
               float f1 = damage * (float)l;
               damage = f1 / 25.0F;
            }

            return damage;
         }
      }
   }

   protected void damageEntity(DamageSource damageSrc, float damageAmount) {
      if (!this.isEntityInvulnerable(damageSrc)) {
         damageAmount = this.applyArmorCalculations(damageSrc, damageAmount);
         damageAmount = this.applyPotionDamageCalculations(damageSrc, damageAmount);
         float var7 = Math.max(damageAmount - this.getAbsorptionAmount(), 0.0F);
         this.setAbsorptionAmount(this.getAbsorptionAmount() - (damageAmount - var7));
         if (var7 != 0.0F) {
            float f1 = this.getHealth();
            this.setHealth(f1 - var7);
            this.getCombatTracker().trackDamage(damageSrc, f1, var7);
            this.setAbsorptionAmount(this.getAbsorptionAmount() - var7);
         }
      }
   }

   public CombatTracker getCombatTracker() {
      return this._combatTracker;
   }

   public EntityLivingBase func_94060_bK() {
      return (EntityLivingBase)(this._combatTracker.func_94550_c() != null
         ? this._combatTracker.func_94550_c()
         : (this.attackingPlayer != null ? this.attackingPlayer : (this.entityLivingToAttack != null ? this.entityLivingToAttack : null)));
   }

   public final float getMaxHealth() {
      return (float)this.getEntityAttribute(SharedMonsterAttributes.maxHealth).getAttributeValue();
   }

   public final int getArrowCountInEntity() {
      return this.dataWatcher.getWatchableObjectByte(9);
   }

   public final void setArrowCountInEntity(int count) {
      this.dataWatcher.updateObject(9, (byte)count);
   }

   private int getArmSwingAnimationEnd() {
      return this.isPotionActive(Potion.digSpeed)
         ? 6 - (1 + this.getActivePotionEffect(Potion.digSpeed).getAmplifier())
         : (this.isPotionActive(Potion.digSlowdown) ? 6 + (1 + this.getActivePotionEffect(Potion.digSlowdown).getAmplifier()) * 2 : 6);
   }

   public void swingItem() {
      if (!this.isSwingInProgress || this.swingProgressInt >= this.getArmSwingAnimationEnd() / 2 || this.swingProgressInt < 0) {
         this.swingProgressInt = -1;
         this.isSwingInProgress = true;
         if (this.worldObj instanceof WorldServer) {
            ((WorldServer)this.worldObj).getEntityTracker().sendToAllTrackingEntity(this, new S0BPacketAnimation(this, 0));
         }
      }
   }

   @Override
   public void handleHealthUpdate(byte id) {
      if (id == 2) {
         this.limbSwingAmount = 1.5F;
         this.hurtResistantTime = 20;
         this.hurtTime = this.maxHurtTime = 10;
         this.attackedAtYaw = 0.0F;
         String s = this.getHurtSound();
         if (s != null) {
            this.playSound(this.getHurtSound(), this.getSoundVolume(), (this.rand.nextFloat() - this.rand.nextFloat()) * 0.2F + 1.0F);
         }

         this.attackEntityFrom(DamageSource.generic, 0.0F);
      } else if (id == 3) {
         String s1 = this.getDeathSound();
         if (s1 != null) {
            this.playSound(this.getDeathSound(), this.getSoundVolume(), (this.rand.nextFloat() - this.rand.nextFloat()) * 0.2F + 1.0F);
         }

         this.setHealth(0.0F);
         this.onDeath(DamageSource.generic);
      } else {
         super.handleHealthUpdate(id);
      }
   }

   @Override
   protected void kill() {
      this.attackEntityFrom(DamageSource.outOfWorld, 4.0F);
   }

   protected void updateArmSwingProgress() {
      int i = this.getArmSwingAnimationEnd();
      if (this.isSwingInProgress) {
         ++this.swingProgressInt;
         if (this.swingProgressInt >= i) {
            this.swingProgressInt = 0;
            this.isSwingInProgress = false;
         }
      } else {
         this.swingProgressInt = 0;
      }

      this.swingProgress = (float)this.swingProgressInt / (float)i;
   }

   public IAttributeInstance getEntityAttribute(IAttribute attribute) {
      return this.getAttributeMap().getAttributeInstance(attribute);
   }

   public BaseAttributeMap getAttributeMap() {
      if (this.attributeMap == null) {
         this.attributeMap = new ServersideAttributeMap();
      }

      return this.attributeMap;
   }

   public EnumCreatureAttribute getCreatureAttribute() {
      return EnumCreatureAttribute.UNDEFINED;
   }

   public abstract ItemStack getHeldItem();

   public abstract ItemStack getEquipmentInSlot(int var1);

   public abstract ItemStack getCurrentArmor(int var1);

   @Override
   public abstract void setCurrentItemOrArmor(int var1, ItemStack var2);

   @Override
   public void setSprinting(boolean sprinting) {
      super.setSprinting(sprinting);
      IAttributeInstance iattributeinstance = this.getEntityAttribute(SharedMonsterAttributes.movementSpeed);
      if (iattributeinstance.getModifier(sprintingSpeedBoostModifierUUID) != null) {
         iattributeinstance.removeModifier(sprintingSpeedBoostModifier);
      }

      if (sprinting) {
         iattributeinstance.applyModifier(sprintingSpeedBoostModifier);
      }
   }

   @Override
   public abstract ItemStack[] getInventory();

   protected float getSoundVolume() {
      return 1.0F;
   }

   protected float getSoundPitch() {
      return this.isChild() ? (this.rand.nextFloat() - this.rand.nextFloat()) * 0.2F + 1.5F : (this.rand.nextFloat() - this.rand.nextFloat()) * 0.2F + 1.0F;
   }

   protected boolean isMovementBlocked() {
      return this.getHealth() <= 0.0F;
   }

   public void dismountEntity(Entity p_110145_1_) {
      double d0 = p_110145_1_.posX;
      double d1 = p_110145_1_.getEntityBoundingBox().minY + (double)p_110145_1_.height;
      double d2 = p_110145_1_.posZ;
      int i = 1;

      for(int j = -i; j <= i; ++j) {
         for(int k = -i; k < i; ++k) {
            if (j != 0 || k != 0) {
               int l = (int)(this.posX + (double)j);
               int i1 = (int)(this.posZ + (double)k);
               AxisAlignedBB axisalignedbb = this.getEntityBoundingBox().offset((double)j, 1.0, (double)k);
               if (this.worldObj.func_147461_a(axisalignedbb).isEmpty()) {
                  if (World.doesBlockHaveSolidTopSurface(this.worldObj, new BlockPos(l, (int)this.posY, i1))) {
                     this.setPositionAndUpdate(this.posX + (double)j, this.posY + 1.0, this.posZ + (double)k);
                     return;
                  }

                  if (World.doesBlockHaveSolidTopSurface(this.worldObj, new BlockPos(l, (int)this.posY - 1, i1))
                     || this.worldObj.getBlockState(new BlockPos(l, (int)this.posY - 1, i1)).getBlock().getMaterial() == Material.water) {
                     d0 = this.posX + (double)j;
                     d1 = this.posY + 1.0;
                     d2 = this.posZ + (double)k;
                  }
               }
            }
         }
      }

      this.setPositionAndUpdate(d0, d1, d2);
   }

   @Override
   public boolean getAlwaysRenderNameTagForRender() {
      return false;
   }

   protected float getJumpUpwardsMotion() {
      return 0.42F;
   }

   protected void jump() {
      this.motionY = (double)this.getJumpUpwardsMotion();
      if (this.isPotionActive(Potion.jump)) {
         this.motionY += (double)((float)(this.getActivePotionEffect(Potion.jump).getAmplifier() + 1) * 0.1F);
      }

      if (this.isSprinting()) {
         float f = this.rotationYaw * (float) (Math.PI / 180.0);
         this.motionX -= (double)(MathHelper.sin(f) * 0.2F);
         this.motionZ += (double)(MathHelper.cos(f) * 0.2F);
      }

      this.isAirBorne = true;
   }

   protected void updateAITick() {
      this.motionY += 0.04F;
   }

   protected void handleJumpLava() {
      this.motionY += 0.04F;
   }

   public void moveEntityWithHeading(float strafe, float forward) {
      if (this.isServerWorld()) {
         if (!this.isInWater() || this instanceof EntityPlayer && ((EntityPlayer)this).capabilities.isFlying) {
            if (!this.isInLava() || this instanceof EntityPlayer && ((EntityPlayer)this).capabilities.isFlying) {
               float f4 = 0.91F;
               if (this.onGround) {
                  f4 = this.worldObj
                        .getBlockState(
                           new BlockPos(
                              MathHelper.floor_double(this.posX),
                              MathHelper.floor_double(this.getEntityBoundingBox().minY) - 1,
                              MathHelper.floor_double(this.posZ)
                           )
                        )
                        .getBlock()
                        .slipperiness
                     * 0.91F;
               }

               float f = 0.16277136F / (f4 * f4 * f4);
               float f5;
               if (this.onGround) {
                  f5 = this.getAIMoveSpeed() * f;
               } else {
                  f5 = this.jumpMovementFactor;
               }

               this.moveFlying(strafe, forward, f5);
               f4 = 0.91F;
               if (this.onGround) {
                  f4 = this.worldObj
                        .getBlockState(
                           new BlockPos(
                              MathHelper.floor_double(this.posX),
                              MathHelper.floor_double(this.getEntityBoundingBox().minY) - 1,
                              MathHelper.floor_double(this.posZ)
                           )
                        )
                        .getBlock()
                        .slipperiness
                     * 0.91F;
               }

               if (this.isOnLadder()) {
                  float f6 = 0.15F;
                  this.motionX = MathHelper.clamp_double(this.motionX, (double)(-f6), (double)f6);
                  this.motionZ = MathHelper.clamp_double(this.motionZ, (double)(-f6), (double)f6);
                  this.fallDistance = 0.0F;
                  if (this.motionY < -0.15) {
                     this.motionY = -0.15;
                  }

                  boolean flag = this.isSneaking() && this instanceof EntityPlayer;
                  if (flag && this.motionY < 0.0) {
                     this.motionY = 0.0;
                  }
               }

               this.moveEntity(this.motionX, this.motionY, this.motionZ);
               if (this.isCollidedHorizontally && this.isOnLadder()) {
                  this.motionY = 0.2;
               }

               if (this.worldObj.isRemote
                  && (
                     !this.worldObj.isBlockLoaded(new BlockPos((int)this.posX, 0, (int)this.posZ))
                        || this.worldObj.getChunkFromBlockCoords(new BlockPos((int)this.posX, 0, (int)this.posZ)).isLoaded()
                  )) {
                  if (this.posY > 0.0) {
                     this.motionY = -0.1;
                  } else {
                     this.motionY = 0.0;
                  }
               } else {
                  this.motionY -= 0.08;
               }

               this.motionY *= 0.98F;
               this.motionX *= (double)f4;
               this.motionZ *= (double)f4;
            } else {
               double d1 = this.posY;
               this.moveFlying(strafe, forward, 0.02F);
               this.moveEntity(this.motionX, this.motionY, this.motionZ);
               this.motionX *= 0.5;
               this.motionY *= 0.5;
               this.motionZ *= 0.5;
               this.motionY -= 0.02;
               if (this.isCollidedHorizontally && this.isOffsetPositionInLiquid(this.motionX, this.motionY + 0.6F - this.posY + d1, this.motionZ)) {
                  this.motionY = 0.3F;
               }
            }
         } else {
            double d0 = this.posY;
            float f1 = 0.8F;
            float f2 = 0.02F;
            float f3 = (float)EnchantmentHelper.getDepthStriderModifier(this);
            if (f3 > 3.0F) {
               f3 = 3.0F;
            }

            if (!this.onGround) {
               f3 *= 0.5F;
            }

            if (f3 > 0.0F) {
               f1 += (0.54600006F - f1) * f3 / 3.0F;
               f2 += (this.getAIMoveSpeed() - f2) * f3 / 3.0F;
            }

            this.moveFlying(strafe, forward, f2);
            this.moveEntity(this.motionX, this.motionY, this.motionZ);
            this.motionX *= (double)f1;
            this.motionY *= 0.8F;
            this.motionZ *= (double)f1;
            this.motionY -= 0.02;
            if (this.isCollidedHorizontally && this.isOffsetPositionInLiquid(this.motionX, this.motionY + 0.6F - this.posY + d0, this.motionZ)) {
               this.motionY = 0.3F;
            }
         }
      }

      this.prevLimbSwingAmount = this.limbSwingAmount;
      double d2 = this.posX - this.prevPosX;
      double d3 = this.posZ - this.prevPosZ;
      float f7 = MathHelper.sqrt_double(d2 * d2 + d3 * d3) * 4.0F;
      if (f7 > 1.0F) {
         f7 = 1.0F;
      }

      this.limbSwingAmount += (f7 - this.limbSwingAmount) * 0.4F;
      this.limbSwing += this.limbSwingAmount;
   }

   public float getAIMoveSpeed() {
      return this.landMovementFactor;
   }

   public void setAIMoveSpeed(float speedIn) {
      this.landMovementFactor = speedIn;
   }

   public boolean attackEntityAsMob(Entity entityIn) {
      this.setLastAttacker(entityIn);
      return false;
   }

   public boolean isPlayerSleeping() {
      return false;
   }

   @Override
   public void onUpdate() {
      super.onUpdate();
      if (!this.worldObj.isRemote) {
         int i = this.getArrowCountInEntity();
         if (i > 0) {
            if (this.arrowHitTimer <= 0) {
               this.arrowHitTimer = 20 * (30 - i);
            }

            --this.arrowHitTimer;
            if (this.arrowHitTimer <= 0) {
               this.setArrowCountInEntity(i - 1);
            }
         }

         for(int j = 0; j < 5; ++j) {
            ItemStack itemstack = this.previousEquipment[j];
            ItemStack itemstack1 = this.getEquipmentInSlot(j);
            if (!ItemStack.areItemStacksEqual(itemstack1, itemstack)) {
               ((WorldServer)this.worldObj).getEntityTracker().sendToAllTrackingEntity(this, new S04PacketEntityEquipment(this.getEntityId(), j, itemstack1));
               if (itemstack != null) {
                  this.attributeMap.removeAttributeModifiers(itemstack.getAttributeModifiers());
               }

               if (itemstack1 != null) {
                  this.attributeMap.applyAttributeModifiers(itemstack1.getAttributeModifiers());
               }

               this.previousEquipment[j] = itemstack1 == null ? null : itemstack1.copy();
            }
         }

         if (this.ticksExisted % 20 == 0) {
            this.getCombatTracker().reset();
         }
      }

      this.onLivingUpdate();
      double d0 = this.posX - this.prevPosX;
      double d1 = this.posZ - this.prevPosZ;
      float f = (float)(d0 * d0 + d1 * d1);
      float f1 = this.renderYawOffset;
      float f2 = 0.0F;
      this.prevOnGroundSpeedFactor = this.onGroundSpeedFactor;
      float f3 = 0.0F;
      if (f > 0.0025000002F) {
         f3 = 1.0F;
         f2 = (float)Math.sqrt((double)f) * 3.0F;
         f1 = (float)MathHelper.func_181159_b(d1, d0) * 180.0F / (float) Math.PI - 90.0F;
      }

      if (this.swingProgress > 0.0F) {
         f1 = this.rotationYaw;
      }

      if (!this.onGround) {
         f3 = 0.0F;
      }

      this.onGroundSpeedFactor += (f3 - this.onGroundSpeedFactor) * 0.3F;
      this.worldObj.theProfiler.startSection("headTurn");
      f2 = this.func_110146_f(f1, f2);
      this.worldObj.theProfiler.endSection();
      this.worldObj.theProfiler.startSection("rangeChecks");

      while(this.rotationYaw - this.prevRotationYaw < -180.0F) {
         this.prevRotationYaw -= 360.0F;
      }

      while(this.rotationYaw - this.prevRotationYaw >= 180.0F) {
         this.prevRotationYaw += 360.0F;
      }

      while(this.renderYawOffset - this.prevRenderYawOffset < -180.0F) {
         this.prevRenderYawOffset -= 360.0F;
      }

      while(this.renderYawOffset - this.prevRenderYawOffset >= 180.0F) {
         this.prevRenderYawOffset += 360.0F;
      }

      while(this.rotationPitch - this.prevRotationPitch < -180.0F) {
         this.prevRotationPitch -= 360.0F;
      }

      while(this.rotationPitch - this.prevRotationPitch >= 180.0F) {
         this.prevRotationPitch += 360.0F;
      }

      while(this.rotationYawHead - this.prevRotationYawHead < -180.0F) {
         this.prevRotationYawHead -= 360.0F;
      }

      while(this.rotationYawHead - this.prevRotationYawHead >= 180.0F) {
         this.prevRotationYawHead += 360.0F;
      }

      this.worldObj.theProfiler.endSection();
      this.movedDistance += f2;
   }

   protected float func_110146_f(float p_110146_1_, float p_110146_2_) {
      float f = MathHelper.wrapAngleTo180_float(p_110146_1_ - this.renderYawOffset);
      this.renderYawOffset += f * 0.3F;
      float f1 = MathHelper.wrapAngleTo180_float(this.rotationYaw - this.renderYawOffset);
      boolean flag = f1 < -90.0F || f1 >= 90.0F;
      if (f1 < -75.0F) {
         f1 = -75.0F;
      }

      if (f1 >= 75.0F) {
         f1 = 75.0F;
      }

      this.renderYawOffset = this.rotationYaw - f1;
      if (f1 * f1 > 2500.0F) {
         this.renderYawOffset += f1 * 0.2F;
      }

      if (flag) {
         p_110146_2_ *= -1.0F;
      }

      return p_110146_2_;
   }

   public void onLivingUpdate() {
      if (this.jumpTicks > 0) {
         --this.jumpTicks;
      }

      if (this.newPosRotationIncrements > 0) {
         double d0 = this.posX + (this.newPosX - this.posX) / (double)this.newPosRotationIncrements;
         double d1 = this.posY + (this.newPosY - this.posY) / (double)this.newPosRotationIncrements;
         double d2 = this.posZ + (this.newPosZ - this.posZ) / (double)this.newPosRotationIncrements;
         double d3 = MathHelper.wrapAngleTo180_double(this.newRotationYaw - (double)this.rotationYaw);
         this.rotationYaw = (float)((double)this.rotationYaw + d3 / (double)this.newPosRotationIncrements);
         this.rotationPitch = (float)(
            (double)this.rotationPitch + (this.newRotationPitch - (double)this.rotationPitch) / (double)this.newPosRotationIncrements
         );
         --this.newPosRotationIncrements;
         this.setPosition(d0, d1, d2);
         this.setRotation(this.rotationYaw, this.rotationPitch);
      } else if (!this.isServerWorld()) {
         this.motionX *= 0.98;
         this.motionY *= 0.98;
         this.motionZ *= 0.98;
      }

      if (Math.abs(this.motionX) < 0.005) {
         this.motionX = 0.0;
      }

      if (Math.abs(this.motionY) < 0.005) {
         this.motionY = 0.0;
      }

      if (Math.abs(this.motionZ) < 0.005) {
         this.motionZ = 0.0;
      }

      this.worldObj.theProfiler.startSection("ai");
      if (this.isMovementBlocked()) {
         this.isJumping = false;
         this.moveStrafing = 0.0F;
         this.moveForward = 0.0F;
         this.randomYawVelocity = 0.0F;
      } else if (this.isServerWorld()) {
         this.worldObj.theProfiler.startSection("newAi");
         this.updateEntityActionState();
         this.worldObj.theProfiler.endSection();
      }

      this.worldObj.theProfiler.endSection();
      this.worldObj.theProfiler.startSection("jump");
      if (this.isJumping) {
         if (this.isInWater()) {
            this.updateAITick();
         } else if (this.isInLava()) {
            this.handleJumpLava();
         } else if (this.onGround && this.jumpTicks == 0) {
            this.jump();
            this.jumpTicks = 10;
         }
      } else {
         this.jumpTicks = 0;
      }

      this.worldObj.theProfiler.endSection();
      this.worldObj.theProfiler.startSection("travel");
      this.moveStrafing *= 0.98F;
      this.moveForward *= 0.98F;
      this.randomYawVelocity *= 0.9F;
      this.moveEntityWithHeading(this.moveStrafing, this.moveForward);
      this.worldObj.theProfiler.endSection();
      this.worldObj.theProfiler.startSection("push");
      if (!this.worldObj.isRemote) {
         this.collideWithNearbyEntities();
      }

      this.worldObj.theProfiler.endSection();
   }

   protected void updateEntityActionState() {
   }

   protected void collideWithNearbyEntities() {
      List<Entity> list = this.worldObj
         .getEntitiesInAABBexcluding(
            this, this.getEntityBoundingBox().expand(0.2F, 0.0, 0.2F), Predicates.and(EntitySelectors.NOT_SPECTATING, Entity::canBePushed)
         );
      if (!list.isEmpty()) {
         for(Entity entity : list) {
            this.collideWithEntity(entity);
         }
      }
   }

   protected void collideWithEntity(Entity p_82167_1_) {
      p_82167_1_.applyEntityCollision(this);
   }

   @Override
   public void mountEntity(Entity entityIn) {
      if (this.ridingEntity != null && entityIn == null) {
         if (!this.worldObj.isRemote) {
            this.dismountEntity(this.ridingEntity);
         }

         if (this.ridingEntity != null) {
            this.ridingEntity.riddenByEntity = null;
         }

         this.ridingEntity = null;
      } else {
         super.mountEntity(entityIn);
      }
   }

   @Override
   public void updateRidden() {
      super.updateRidden();
      this.prevOnGroundSpeedFactor = this.onGroundSpeedFactor;
      this.onGroundSpeedFactor = 0.0F;
      this.fallDistance = 0.0F;
   }

   @Override
   public void setPositionAndRotation2(double x, double y, double z, float yaw, float pitch, int posRotationIncrements, boolean p_180426_10_) {
      this.newPosX = x;
      this.newPosY = y;
      this.newPosZ = z;
      this.newRotationYaw = (double)yaw;
      this.newRotationPitch = (double)pitch;
      this.newPosRotationIncrements = posRotationIncrements;
   }

   public void setJumping(boolean p_70637_1_) {
      this.isJumping = p_70637_1_;
   }

   public void onItemPickup(Entity p_71001_1_, int p_71001_2_) {
      if (!p_71001_1_.isDead && !this.worldObj.isRemote) {
         EntityTracker entitytracker = ((WorldServer)this.worldObj).getEntityTracker();
         if (p_71001_1_ instanceof EntityItem) {
            entitytracker.sendToAllTrackingEntity(p_71001_1_, new S0DPacketCollectItem(p_71001_1_.getEntityId(), this.getEntityId()));
         }

         if (p_71001_1_ instanceof EntityArrow) {
            entitytracker.sendToAllTrackingEntity(p_71001_1_, new S0DPacketCollectItem(p_71001_1_.getEntityId(), this.getEntityId()));
         }

         if (p_71001_1_ instanceof EntityXPOrb) {
            entitytracker.sendToAllTrackingEntity(p_71001_1_, new S0DPacketCollectItem(p_71001_1_.getEntityId(), this.getEntityId()));
         }
      }
   }

   public boolean canEntityBeSeen(Entity entityIn) {
      return this.worldObj
            .rayTraceBlocks(
               new Vec3(this.posX, this.posY + (double)this.getEyeHeight(), this.posZ),
               new Vec3(entityIn.posX, entityIn.posY + (double)entityIn.getEyeHeight(), entityIn.posZ)
            )
         == null;
   }

   @Override
   public Vec3 getLookVec() {
      return this.getLook(1.0F);
   }

   @Override
   public Vec3 getLook(float partialTicks) {
      if (this instanceof EntityPlayerSP) {
         return super.getLook(partialTicks);
      } else if (partialTicks == 1.0F) {
         return this.getVectorForRotation(this.rotationPitch, this.rotationYawHead);
      } else {
         float f = this.prevRotationPitch + (this.rotationPitch - this.prevRotationPitch) * partialTicks;
         float f1 = this.prevRotationYawHead + (this.rotationYawHead - this.prevRotationYawHead) * partialTicks;
         return this.getVectorForRotation(f, f1);
      }
   }

   public float getSwingProgress(float partialTickTime) {
      float f = this.swingProgress - this.prevSwingProgress;
      if (f < 0.0F) {
         ++f;
      }

      return this.prevSwingProgress + f * partialTickTime;
   }

   public boolean isServerWorld() {
      return !this.worldObj.isRemote;
   }

   @Override
   public boolean canBeCollidedWith() {
      return !this.isDead;
   }

   @Override
   public boolean canBePushed() {
      return !this.isDead;
   }

   @Override
   protected void setBeenAttacked() {
      this.velocityChanged = this.rand.nextDouble() >= this.getEntityAttribute(SharedMonsterAttributes.knockbackResistance).getAttributeValue();
   }

   @Override
   public float getRotationYawHead() {
      return this.rotationYawHead;
   }

   @Override
   public void setRotationYawHead(float rotation) {
      this.rotationYawHead = rotation;
   }

   @Override
   public void func_181013_g(float p_181013_1_) {
      this.renderYawOffset = p_181013_1_;
   }

   public float getAbsorptionAmount() {
      return this.absorptionAmount;
   }

   public void setAbsorptionAmount(float amount) {
      if (amount < 0.0F) {
         amount = 0.0F;
      }

      this.absorptionAmount = amount;
   }

   public Team getTeam() {
      return this.worldObj.getScoreboard().getPlayersTeam(this.getUniqueID().toString());
   }

   public boolean isOnSameTeam(EntityLivingBase otherEntity) {
      return this.isOnTeam(otherEntity.getTeam());
   }

   public boolean isOnTeam(Team p_142012_1_) {
      return this.getTeam() != null && this.getTeam().isSameTeam(p_142012_1_);
   }

   public void sendEnterCombat() {
   }

   public void sendEndCombat() {
   }

   protected void markPotionsDirty() {
      this.potionsNeedUpdate = true;
   }
}
