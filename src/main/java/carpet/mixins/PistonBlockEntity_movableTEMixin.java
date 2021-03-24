package carpet.mixins;

import carpet.CarpetSettings;
import carpet.fakes.PistonBlockEntityInterface;
import carpet.fakes.WorldInterface;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.PistonBlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonBlockEntity.class)
public abstract class PistonBlockEntity_movableTEMixin extends BlockEntity implements PistonBlockEntityInterface
{
    @Shadow
    private boolean source;
    @Shadow
    private BlockState pushedBlock;
    
    private BlockEntity carriedBlockEntity;
    private boolean renderCarriedBlockEntity = false;
    private boolean renderSet = false;

    public PistonBlockEntity_movableTEMixin(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }


    /**
     * @author 2No2Name
     */
    public BlockEntity getCarriedBlockEntity()
    {
        return carriedBlockEntity;
    }

    @Override
    public void setWorld(World world) {
        super.setWorld(world);
        if (carriedBlockEntity != null) carriedBlockEntity.setWorld(world);
    }

    public void setCarriedBlockEntity(BlockEntity blockEntity)
    {
        this.carriedBlockEntity = blockEntity;
        if (this.carriedBlockEntity != null)
        {
            ((BlockEntity_movableBEMixin)carriedBlockEntity).setPos(pos);
            // this might be little dangerous since pos is final for a hashing reason?
            if (world != null) carriedBlockEntity.setWorld(world);
        }
        //    this.carriedBlockEntity.setPos(this.pos);
    }
    
    public boolean isRenderModeSet()
    {
        return renderSet;
    }
    
    public boolean getRenderCarriedBlockEntity()
    {
        return renderCarriedBlockEntity;
    }
    
    public void setRenderCarriedBlockEntity(boolean b)
    {
        renderCarriedBlockEntity = b;
        renderSet = true;
    }
    
    /**
     * @author 2No2Name
     */
    @Redirect(method = "tick", at = @At(value = "INVOKE",
              target = "Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z"))
    private static boolean movableTEsetBlockState0(
            World world, BlockPos blockPos_1, BlockState blockAState_2, int int_1,
            World world2, BlockPos blockPos, BlockState blockState, PistonBlockEntity pistonBlockEntity)
    {
        if (!CarpetSettings.movableBlockEntities)
            return world.setBlockState(blockPos_1, blockAState_2, int_1);
        else
            return ((WorldInterface) (world)).setBlockStateWithBlockEntity(blockPos_1, blockAState_2, ((PistonBlockEntityInterface)pistonBlockEntity).getCarriedBlockEntity(), int_1);
    }
    
    @Redirect(method = "finish", at = @At(value = "INVOKE",
              target = "Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z"))
    private boolean movableTEsetBlockState1(World world, BlockPos blockPos_1, BlockState blockState_2, int int_1)
    {
        if (!CarpetSettings.movableBlockEntities)
            return world.setBlockState(blockPos_1, blockState_2, int_1);
        else
        {
            boolean ret = ((WorldInterface) (world)).setBlockStateWithBlockEntity(blockPos_1, blockState_2, this.carriedBlockEntity, int_1);
            this.carriedBlockEntity = null; //this will cancel the finishHandleBroken
            return ret;
        }
    }
    
    @Inject(method = "finish", at = @At(value = "RETURN"))
    private void finishHandleBroken(CallbackInfo cir)
    {
        //Handle TNT Explosions or other ways the moving Block is broken
        //Also /setblock will cause this to be called, and drop e.g. a moving chest's contents.
        // This is MC-40380 (BlockEntities that aren't Inventories drop stuff when setblock is called )
        if (CarpetSettings.movableBlockEntities && this.carriedBlockEntity != null && !this.world.isClient && this.world.getBlockState(this.pos).getBlock() == Blocks.AIR)
        {
            BlockState blockState_2;
            if (this.source)
                blockState_2 = Blocks.AIR.getDefaultState();
            else
                blockState_2 = Block.postProcessState(this.pushedBlock, this.world, this.pos);
            ((WorldInterface) (this.world)).setBlockStateWithBlockEntity(this.pos, blockState_2, this.carriedBlockEntity, 3);
            this.world.breakBlock(this.pos, false, null);
        }
    }
    
    @Inject(method = "readNbt", at = @At(value = "TAIL"))
    private void onFromTag(NbtCompound NbtCompound_1, CallbackInfo ci)
    {
        if (CarpetSettings.movableBlockEntities && NbtCompound_1.contains("carriedTileEntityCM", 10))
        {
            if (this.pushedBlock.getBlock() instanceof BlockEntityProvider)
                this.carriedBlockEntity = ((BlockEntityProvider) (this.pushedBlock.getBlock())).createBlockEntity(pos, pushedBlock);//   this.world);
            if (carriedBlockEntity != null) //Can actually be null, as BlockPistonMoving.createNewTileEntity(...) returns null
                this.carriedBlockEntity.readNbt(NbtCompound_1.getCompound("carriedTileEntityCM"));
            setCarriedBlockEntity(carriedBlockEntity);
        }
    }
    
    @Inject(method = "writeNbt", at = @At(value = "RETURN", shift = At.Shift.BEFORE))
    private void onToTag(NbtCompound NbtCompound_1, CallbackInfoReturnable<NbtCompound> cir)
    {
        if (CarpetSettings.movableBlockEntities && this.carriedBlockEntity != null)
        {
            //Leave name "carriedTileEntityCM" instead of "carriedBlockEntityCM" for upgrade compatibility with 1.13.2 movable TE
            NbtCompound_1.put("carriedTileEntityCM", this.carriedBlockEntity.writeNbt(new NbtCompound()));
        }
    }
}
