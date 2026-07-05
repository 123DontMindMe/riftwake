package me.talula.riftwake.utils

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

    abstract fun onClick(event: InventoryClickEvent)
    fun onDrag(event: InventoryDragEvent) {}
    fun onPlayerInventoryClick(event: InventoryClickEvent) {}
    fun onClose(event: InventoryCloseEvent) {}

    private fun processPotentialButtonClick(event: InventoryClickEvent) {
        val button = buttons[event.slot]
        if (button != null) {
            event.isCancelled = true
            button.onClick()
        }
    }

    abstract inner class Button {
        val index: Int
        private var item: ItemStack?
        var isHidden: Boolean = false
            private set

        constructor(index: Int, icon: ItemStack?) {
            this.index = index
            buttons[index] = this

            item = icon
            inventory.setItem(index, item)
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
            this.item = item
        }

        fun getItem(): ItemStack? {
            return item
        }

        fun show() {
            inventory.setItem(index, item)
            isHidden = false
        }

        fun hide() {
            inventory.setItem(index, emptyIcon)
            isHidden = true
        }

        fun setItem(item: ItemStack?) {
            this.item = item
            if (!isHidden) inventory.setItem(index, item)
        }

        fun editItem(editor: Consumer<ItemStack?>) {
            editor.accept(item)
            if (!isHidden) inventory.setItem(index, item)
        }

        abstract fun onClick()
    }

    inner class SimpleButton(index: Int, icon: ItemStack, val onClick: () -> Unit) : Button(index, icon) {
        override fun onClick() = onClick.invoke()
    }

    open inner class StaticButton(index: Int, icon: ItemStack?) : Button(index, icon) {
        override fun onClick() {}
    }

    inner class EmptyButton(index: Int) : StaticButton(index, emptyIcon)

    companion object {
        protected var emptyIcon = createIcon(null, Material.GRAY_STAINED_GLASS_PANE)
        protected var clearIcon = createIcon(null, Material.LIGHT_GRAY_STAINED_GLASS_PANE)

        @JvmStatic
        protected fun createIcon(name: Component?, material: Material, amount: Int = 1): ItemStack {
            val button = ItemStack.of(material, amount)
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
                meta.setMaxStackSize(amount)
            }
            return button
        }

        @JvmStatic
        protected fun createIcon(name: Component?, material: Material, amount: Int, vararg lore: String): ItemStack {
            val button = createIcon(name, material, amount)
            button.lore(Components.loreLines(*lore))
            return button
        }

        @JvmStatic
        protected fun createIcon(name: Component?, material: Material, amount: Int, vararg lore: Component): ItemStack {
            val button = createIcon(name, material, amount)
            button.lore(listOf(*lore))
            return button
        }

        @JvmStatic
        protected fun createIcon(name: Component?, material: Material, amount: Int, lore: List<Component>): ItemStack {
            val button = createIcon(name, material, amount)
            button.lore(lore)
            return button
        }

        @JvmStatic
        protected fun createIcon(name: Component?, material: Material, vararg lore: String): ItemStack {
            val button = createIcon(name, material, 1)
            button.lore(Components.loreLines(*lore))
            return button
        }

        @JvmStatic
        protected fun createIcon(name: Component?, material: Material, vararg lore: Component): ItemStack {
            val button = createIcon(name, material, 1)
            button.lore(listOf(*lore))
            return button
        }

        @JvmStatic
        protected fun createIcon(name: Component?, material: Material, lore: List<Component>): ItemStack {
            val button = createIcon(name, material)
            button.lore(lore)
            return button
        }
    }
}
