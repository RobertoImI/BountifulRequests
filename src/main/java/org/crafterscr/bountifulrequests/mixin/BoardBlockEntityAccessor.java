package org.crafterscr.bountifulrequests.mixin;

import io.ejekta.bountiful.content.board.BoardBlockEntity;

import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Sólo abre dos funciones privadas necesarias para publicar/retirar
 * nuestros Bounties.
 */
@Mixin(
        value = BoardBlockEntity.class,
        remap = false
)
public interface BoardBlockEntityAccessor {

    @Invoker("addBountyToRandomSlot")
    void bountifulrequests$addRandom(
            ItemStack stack
    );

    /**
     * Permite insertar en un slot concreto. Es más fiable para nuestras
     * publicaciones porque podemos comprobar nosotros mismos si el Board
     * tiene espacio y, si está lleno, reemplazar un bounty normal.
     */
    @Invoker("addBounty")
    void bountifulrequests$addBounty(
            int slot,
            ItemStack stack
    );

    @Invoker("removeBounty")
    void bountifulrequests$removeBounty(
            int slot
    );
}