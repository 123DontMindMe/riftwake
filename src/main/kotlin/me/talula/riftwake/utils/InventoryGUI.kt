package me.talula.riftwake.utils

import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent
import me.talula.riftwake.Riftwake
import me.talula.riftwake.RiftwakePlayer
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.*
import java.util.function.Consumer

abstract class InventoryGUI(val player: RiftwakePlayer, numRows: Int, title: Component) : InventoryHolder {
    val numSlots = numRows * 9
    private val inventory = Riftwake.server.createInventory(this, numSlots, title)

    private val buttons = arrayOfNulls<Button?>(numSlots)

    override fun getInventory(): Inventory {
        return inventory
    }

    fun open() {
        player.openInventory(inventory)
    }

    fun close() {
        player.closeInventory()
    }

    fun getButton(index: Int): Button? {
        return buttons[index]
    }

    fun handleClickEvent(event: InventoryClickEvent) {
        onClick(event)
        if (!event.isCancelled)
            processPotentialButtonClick(event)
    }

    protected fun fillEmpty() {
        for (i in 0 ..< numSlots)
            if (inventory.getItem(i) == null)
                EmptyButton(i)
    }

    protected fun fillClear() {
        for (i in 0 ..< numSlots)
            if (inventory.getItem(i) == null)
                StaticButton(i, clearIcon)
    }

    open fun onClick(event: InventoryClickEvent) {}
    open fun onDrag(event: InventoryDragEvent) {}
    open fun onPlayerInventoryClick(event: InventoryClickEvent) {}
    open fun onPlayerInventoryChange(event: PlayerInventorySlotChangeEvent) {}
    open fun onClose(event: InventoryCloseEvent) {}

    private fun processPotentialButtonClick(event: InventoryClickEvent) {
        val button = buttons[event.slot]
        if (button != null) {
            event.isCancelled = true
            button.onClick(event)
        }
    }

    abstract inner class Button {
        val index: Int
        lateinit var icon: ItemStack
            private set
        var isHidden: Boolean = false
            private set

        constructor(index: Int, icon: ItemStack?) {
            this.index = index
            buttons[index] = this

            if (icon != null)
                this.icon = icon
            inventory.setItem(index, icon)
        }

        constructor(index: Int, name: Component?, icon: Material, amount: Int, vararg loreLines: String) {
            this.index = index
            buttons[index] = this

            val item = ItemStack.of(icon, amount)
            item.editMeta { meta ->
                meta.itemName(name)
                meta.setMaxStackSize(amount)
                meta.lore(Components.loreLines(*loreLines))
            }
            inventory.setItem(index, item)
            this.icon = item
        }

        fun show() {
            inventory.setItem(index, icon)
            isHidden = false
        }

        fun hide() {
            inventory.setItem(index, emptyIcon)
            isHidden = true
        }

        fun setIcon(item: ItemStack) {
            this.icon = item
            if (!isHidden) inventory.setItem(index, item)
        }

        fun editIcon(editor: Consumer<ItemStack?>) {
            editor.accept(icon)
            if (!isHidden) inventory.setItem(index, icon)
        }

        abstract fun onClick(event: InventoryClickEvent)
    }

    inner class SimpleButton(index: Int, icon: ItemStack, val onClick: (InventoryClickEvent) -> Unit) : Button(index, icon) {
        override fun onClick(event: InventoryClickEvent) = onClick.invoke(event)

        constructor(x: Int, y: Int, icon: ItemStack, onClick: (InventoryClickEvent) -> Unit): this(x + y * 9, icon, onClick)
    }

    open inner class StaticButton(index: Int, icon: ItemStack?) : Button(index, icon) {
        override fun onClick(event: InventoryClickEvent) {}
    }

    inner class EmptyButton(index: Int) : StaticButton(index, emptyIcon)

    companion object {
        var emptyIcon = createIcon(null, Material.GRAY_STAINED_GLASS_PANE)
        var clearIcon = createIcon(null, Material.LIGHT_GRAY_STAINED_GLASS_PANE)

        fun createIcon(name: Component?, item: ItemStack, amount: Int, vararg lore: Component): ItemStack {
            val button = item.asQuantity(amount)
            button.editMeta { meta ->
                if (name == null)
                    meta.isHideTooltip = true
                else
                    meta.itemName(name)
                meta.addAttributeModifier(
                    Attribute.ATTACK_DAMAGE, AttributeModifier(
                        NamespacedKey("riftwake", "here-to-override-default-modifiers"),
                        0.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY
                    )
                )
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
                meta.setMaxStackSize(1)
            }
            button.lore(listOf(*lore))
            return button
        }

        fun createIcon(name: Component?, material: Material, amount: Int = 1, glint: Boolean = false): ItemStack {
            val button = ItemStack.of(material, amount)
            button.editMeta { meta ->
                if (glint)
                    meta.setEnchantmentGlintOverride(true)
                if (name == null)
                    meta.isHideTooltip = true
                else
                    meta.itemName(name)
                meta.addAttributeModifier(
                    Attribute.ATTACK_DAMAGE, AttributeModifier(
                        NamespacedKey("riftwake", "here-to-override-default-modifiers"),
                        0.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY
                    )
                )
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
                meta.setMaxStackSize(amount)
            }
            return button
        }

        fun createIcon(name: Component?, material: Material, amount: Int, vararg lore: String, glint: Boolean = false): ItemStack {
            val button = createIcon(name, material, amount, glint)
            button.lore(Components.loreLines(*lore))
            return button
        }

        fun createIcon(name: Component?, material: Material, amount: Int, vararg lore: Component, glint: Boolean = false): ItemStack {
            val button = createIcon(name, material, amount, glint)
            button.lore(listOf(*lore))
            return button
        }

        fun createIcon(name: Component?, material: Material, amount: Int, lore: List<Component>, glint: Boolean = false): ItemStack {
            val button = createIcon(name, material, amount, glint)
            button.lore(lore)
            return button
        }

        fun createIcon(name: Component?, material: Material, vararg lore: String, glint: Boolean = false): ItemStack {
            val button = createIcon(name, material, 1, glint)
            button.lore(Components.loreLines(*lore))
            return button
        }

        fun createIcon(name: Component?, material: Material, vararg lore: Component, glint: Boolean = false): ItemStack {
            val button = createIcon(name, material, 1, glint)
            button.lore(listOf(*lore))
            return button
        }

        fun createIcon(name: Component?, material: Material, lore: List<Component>, glint: Boolean = false): ItemStack {
            val button = createIcon(name, material, 1, glint)
            button.lore(lore)
            return button
        }
    }
}
