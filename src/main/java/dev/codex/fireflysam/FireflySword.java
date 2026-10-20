package dev.codex.fireflysam;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class FireflySword extends SwordItem {
    public FireflySword(Properties properties) {
        super(Tiers.NETHERITE, properties.attributes(SwordItem.createAttributes(Tiers.NETHERITE, 5, -2.4f)));
    }
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) SamCombat.toggle(serverPlayer);
            else SamCombat.cast(serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.firefly_sam.blade").withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("tooltip.firefly_sam.transform").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.firefly_sam.combo").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.firefly_sam.ultimate").withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("tooltip.firefly_sam.terrain").withStyle(ChatFormatting.RED));
    }
}
