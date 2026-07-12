package me.talula.riftwake.utils

import com.mojang.brigadier.Message
import io.papermc.paper.command.brigadier.MessageComponentSerializer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import java.util.Locale
import kotlin.Any
import kotlin.Array
import kotlin.Char
import kotlin.IllegalArgumentException
import kotlin.Int
import kotlin.collections.ArrayList
import kotlin.collections.MutableList
import kotlin.collections.indices

fun List<Component>.andJoin(): Component {
    if (isEmpty())
        return "".comp()
    if (size == 1)
        return get(0)
    if (size == 2)
        return get(0).append(" and ".comp()).append(get(1))
    val builder = Component.text()
    for ((index, component) in this.withIndex()) {
        builder.append(component)
        if (index < size - 2)
            builder.append(", ".comp())
        else if (index == size - 2)
            builder.append(", and ".comp())
    }
    return builder.build()
}

fun Component.toMessage(): Message = MessageComponentSerializer.message().serialize(this)
fun String.toMessage(): Message = Component.text(this).toMessage()

operator fun Component.plus(other: Component): Component {
    return this.append(other)
}

fun TextColor.joinLoreLine(vararg args: Any): Component {
    return Components.joinLoreLine(this, *args)
}

fun TextColor.joinLore(vararg args: Any): MutableList<Component> {
    return Components.joinLore(this, *args)
}

fun TextColor.join(vararg args: Any): TextComponent {
    return Components.join(this, *args)
}

fun String.parseLore(vararg args: Any): Component {
    return Components.loreLine(this, *args)
}

fun String.parse(vararg args: Any): Component {
    return Components.line(this, *args)
}

fun String.comp(): Component {
    return Component.text(this)
}

fun String.lore(): Component {
    return Component.text(this).decoration(TextDecoration.ITALIC, false).color(NamedTextColor.GRAY)
}

fun List<String>.parseLore(vararg args: Any) = Components.loreLines(this, *args)

object Components {
    fun content(component: TextComponent): String {
        val result = StringBuilder()
        result.append(component.content())
        for (child in component.children())
            if (child is TextComponent)
                result.append(content(child))
        return result.toString()
    }

    fun toString(component: TextComponent): String {
        val text = StringBuilder(component.content())

        val modifierStrings: MutableList<String?> = ArrayList()

        val color: TextColor? = component.color()
        if (color != null) {
            val namedColor = NamedTextColor.namedColor(color.value())
            if (namedColor == null) modifierStrings.add("HEX(" + color.asHexString() + ")")
            else modifierStrings.add(namedColor.toString().uppercase(Locale.getDefault()))
        }

        if (component.hasDecoration(TextDecoration.BOLD)) modifierStrings.add("BOLD")
        if (component.hasDecoration(TextDecoration.ITALIC)) modifierStrings.add("ITALIC")
        if (component.hasDecoration(TextDecoration.UNDERLINED)) modifierStrings.add("UNDERLINED")
        if (component.hasDecoration(TextDecoration.OBFUSCATED)) modifierStrings.add("OBFUSCATED")
        if (component.hasDecoration(TextDecoration.STRIKETHROUGH)) modifierStrings.add("STRIKETHROUGH")

        for (child in component.children()) if (child is TextComponent) text.append(toString(child))

        if (modifierStrings.isEmpty()) return text.toString()
        return "<" + modifierStrings.joinToString(",") + "|" + text + ">"
    }

    fun joinLines(lines: MutableList<TextComponent?>): TextComponent {
        val builder = Component.text()
        for (i in lines.indices) {
            builder.append(lines[i]!!)
            if (i != lines.size - 1) builder.appendNewline()
        }
        return builder.build()
    }

    fun join(vararg args: Any): TextComponent {
        val builder = Component.text()
        for (arg in args) {
            when (arg) {
                is TextComponent, is TranslatableComponent -> builder.append(arg)
                else -> builder.append(Component.text(arg.toString()))
            }
        }
        return builder.build()
    }

    fun join(color: TextColor, vararg args: Any): TextComponent {
        val builder = Component.text().color(color)
        for (arg in args) {
            when (arg) {
                is TextComponent, is TranslatableComponent -> builder.append(arg)
                else -> builder.append(Component.text(arg.toString()))
            }
        }
        return builder.build()
    }

    fun joinLoreLine(vararg args: Any): Component {
        return Components.joinLoreLine(NamedTextColor.GRAY, *args)
    }

    fun joinLoreLine(color: TextColor, vararg args: Any): Component {
        val line = Component.text().color(color).decoration(TextDecoration.ITALIC, false)
        for (arg in args) {
            when (arg) {
                is TextComponent, is TranslatableComponent -> line.append(arg)
                else -> line.append(Component.text(arg.toString()))
            }
        }
        return line.build()
    }

    fun joinLore(vararg args: Any): MutableList<Component> {
        return Components.joinLore(NamedTextColor.GRAY, *args)
    }

    fun joinLore(color: TextColor, vararg args: Any): MutableList<Component> {
        val lines: MutableList<Component> = ArrayList()
        var line = Component.text().color(color).decoration(TextDecoration.ITALIC, false)
        for (arg in args) {
            when (arg) {
                is TextComponent, is TranslatableComponent -> line.append(arg)
                is String -> {
                    if (arg == "\n") {
                        lines.add(line.build())
                        line = Component.text().color(color).decoration(TextDecoration.ITALIC, false)
                    } else line.append(Component.text(arg))
                }
                else -> line.append(
                    Component.text(arg.toString()).color(color).decoration(TextDecoration.ITALIC, false)
                )
            }
        }
        lines.add(line.build())
        return lines
    }

    fun build(vararg args: Any?): TextComponent.Builder {
        val builder = Component.text()
        for (arg in args) {
            if (arg is TextComponent) builder.append(arg)
            else if (arg is String) builder.append(Component.text(arg))
        }
        return builder
    }

    fun loreLines(vararg lines: String): MutableList<TextComponent> {
        val components: MutableList<TextComponent> = ArrayList()
        for (line in lines)
            components.add(Components.loreLine(line))
        return components
    }

    fun loreLines(lines: Array<String>, vararg args: Any): MutableList<TextComponent> {
        val components: MutableList<TextComponent> = ArrayList()

        // start at first line
        if (lines.isEmpty()) {
            if (args.isEmpty())
                return components
            throw IllegalArgumentException("Not enough arguments (${args.size}) for placeholders")
        }
        var lineIndex = 0
        var line = lines[lineIndex]

        // for each argument:
        for (arg in args) {
            // advance to next lines until a line contains a placeholder for this argument
            var placeholderIndex = line.indexOf("<>")
            while (placeholderIndex == -1) {
                // parse line and advance to next line
                components.add(Components.loreLine(line))
                lineIndex++
                // if no more lines, then we ran out of placeholders, so return what we have
                if (lineIndex == lines.size)
                    return components

                line = lines[lineIndex]
                placeholderIndex = line.indexOf("<>")
            }
            // replace the placeholder in that line and continue to next argument
            line = line.substring(0, placeholderIndex) + arg.toString() + line.substring(placeholderIndex + 2)
        }
        // no more arguments to replace placeholders on this line, so parse the current line
        components.add(Components.loreLine(line))
        lineIndex++

        // parse remaining lines
        while (lineIndex < lines.size) components.add(Components.loreLine(lines[lineIndex++]))

        return components
    }

    fun loreLines(lines: Iterable<String>): MutableList<TextComponent> {
        val components: MutableList<TextComponent> = ArrayList()
        for (line in lines)
            components.add(Components.loreLine(line))
        return components
    }

    fun loreLines(lines: List<String>, vararg args: Any): MutableList<TextComponent> {
        val components: MutableList<TextComponent> = ArrayList()

        // start at first line
        if (lines.isEmpty()) {
            if (args.isEmpty())
                return components
            throw IllegalArgumentException("Not enough arguments (" + args.size + ") for placeholders")
        }
        var lineIndex = 0
        var line = lines[lineIndex]

        // for each argument:
        for (arg in args) {
            // advance to next lines until a line contains a placeholder for this argument
            var placeholderIndex = line.indexOf("<>")
            while (placeholderIndex == -1) {
                // parse line and advance to next line
                components.add(Components.loreLine(line))
                lineIndex++
                // if no more lines, then we ran out of placeholders, so return what we have
                if (lineIndex == lines.size) return components

                line = lines[lineIndex]
                placeholderIndex = line.indexOf("<>")
            }
            // replace the placeholder in that line and continue to next argument
            line = line.substring(0, placeholderIndex) + arg.toString() + line.substring(placeholderIndex + 2)
        }
        // no more arguments to replace placeholders on this line, so parse the current line
        components.add(Components.loreLine(line))
        lineIndex++

        // parse remaining lines
        while (lineIndex < lines.size) components.add(Components.loreLine(lines[lineIndex++]))

        return components
    }

    fun loreLine(line: String): TextComponent {
        return parseComponent(line)
            .decoration(TextDecoration.ITALIC, false)
            .color(NamedTextColor.GRAY)
            .build()
    }

    fun lines(vararg lines: String): MutableList<TextComponent> {
        val components: MutableList<TextComponent> = ArrayList()
        for (line in lines) components.add(line(line))
        return components
    }

    fun lines(lines: MutableList<String>): MutableList<TextComponent> {
        val components: MutableList<TextComponent> = ArrayList()
        for (line in lines) components.add(line(line))
        return components
    }

    fun line(line: String, vararg args: Any): TextComponent {
        var line = line
        for (arg in args) {
            val placeholderIndex = line.indexOf("<>")
            line = line.substring(0, placeholderIndex) + arg.toString() + line.substring(placeholderIndex + 2)
        }
        return parseComponent(line).build()
    }

    // private static final Pattern placeholderPattern = Pattern.compile("<[0-9]+>");
    // public static TextComponent indexArgs(String line, Object... args) {
    // 	Matcher matcher = placeholderPattern.matcher(line);
    // 	return parseComponent(
    // 		matcher.replaceAll(result -> {
    // 			String placeholder = result.group();
    // 			int argIndex = Integer.parseUnsignedInt(placeholder.substring(1, placeholder.length() - 1));
    // 			return args[argIndex].toString();
    // 		})
    // 	).build();
    // }
    fun loreLine(line: String, vararg args: Any): TextComponent {
        var line = line
        for (arg in args) {
            val placeholderIndex = line.indexOf("<>")
            line = line.substring(0, placeholderIndex) + arg.toString() + line.substring(placeholderIndex + 2)
        }
        return Components.loreLine(line)
    }

    private fun parseComponent(text: String): TextComponent.Builder {
        val builder = Component.text()

        var modifiedSectionStart: Int = text.indexOf('<')
        while (true) {
            if (modifiedSectionStart == -1) {
                builder.append(Component.text(text.replace(Regex("\\\\"), "")))
                return builder
            }
            if (modifiedSectionStart == text.length - 1) break
            if (modifiedSectionStart != 0 && text[modifiedSectionStart - 1] == '\\') {
                modifiedSectionStart = text.indexOf('<', modifiedSectionStart + 1)
                continue
            }
            if (text[modifiedSectionStart + 1] == '>') modifiedSectionStart =
                text.indexOf('<', modifiedSectionStart + 2)
            else break
        }

        var modifiedSectionEnd = -1
        var nestingLevel = 0
        for (i in modifiedSectionStart + 1..<text.length) {
            val c: Char = text[i]
            if (c == '<' && text[i - 1] != '\\') {
                nestingLevel++
                continue
            }
            if (c != '>')
                continue
            if (nestingLevel > 0) {
                nestingLevel--
                continue
            }
            modifiedSectionEnd = i
        }

        if (modifiedSectionEnd == -1) {
            builder.append(Component.text(text.replace(Regex("\\\\"), "")))
            return builder
        }

        // don't do this, it fucks with overriding default lore formatting
        // if (modifiedSectionStart == 0 && modifiedSectionEnd == text.length() - 1)
        // 	return parseModifiedSection(text.substring(modifiedSectionStart + 1, modifiedSectionEnd)).toBuilder();
        val textBeforeModifier = text.substring(0, modifiedSectionStart)
        builder.append(Component.text(textBeforeModifier.replace(Regex("\\\\"), "")))
        val modifiedSection = text.substring(modifiedSectionStart + 1, modifiedSectionEnd)
        builder.append(parseModifiedSection(modifiedSection))

        val textAfterModifier = text.substring(modifiedSectionEnd + 1)
        if (textAfterModifier.isEmpty()) return builder
        builder.append(parseComponent(textAfterModifier))

        return builder
    }

    private fun parseModifiedSection(text: String): TextComponent {
        val modifiersEnd: Int = text.indexOf('|')
        val modifiedText = text.substring(modifiersEnd + 1)
        val builder = parseComponent(modifiedText)

        val modifiers: List<String> = text.substring(0, modifiersEnd).split(",")
        for (modifier in modifiers) {
            var modifier = modifier
            modifier = modifier.trim()
            when (modifier.uppercase()) {
                "BLACK" -> builder.color(NamedTextColor.BLACK)
                "DARK_BLUE" -> builder.color(NamedTextColor.DARK_BLUE)
                "DARK_GREEN" -> builder.color(NamedTextColor.DARK_GREEN)
                "DARK_AQUA" -> builder.color(NamedTextColor.DARK_AQUA)
                "DARK_RED" -> builder.color(NamedTextColor.DARK_RED)
                "DARK_PURPLE" -> builder.color(NamedTextColor.DARK_PURPLE)
                "GOLD" -> builder.color(NamedTextColor.GOLD)
                "GRAY", "GREY" -> builder.color(NamedTextColor.GRAY)
                "DARK_GRAY", "DARK_GREY" -> builder.color(NamedTextColor.DARK_GRAY)
                "BLUE" -> builder.color(NamedTextColor.BLUE)
                "GREEN" -> builder.color(NamedTextColor.GREEN)
                "AQUA" -> builder.color(NamedTextColor.AQUA)
                "RED" -> builder.color(NamedTextColor.RED)
                "LIGHT_PURPLE" -> builder.color(NamedTextColor.LIGHT_PURPLE)
                "YELLOW" -> builder.color(NamedTextColor.YELLOW)
                "WHITE" -> builder.color(NamedTextColor.WHITE)
                "BOLD" -> builder.decorate(TextDecoration.BOLD)
                "ITALIC" -> builder.decorate(TextDecoration.ITALIC)
                "UNDERLINED", "UNDERLINE" -> builder.decorate(TextDecoration.UNDERLINED)
                "OBFUSCATED" -> builder.decorate(TextDecoration.OBFUSCATED)
                "STRIKETHROUGH" -> builder.decorate(TextDecoration.STRIKETHROUGH)
                else -> {
                    if (!modifier.endsWith(")")) break
                    if (modifier.uppercase().startsWith("RGB(")) {
                        val values: List<String> =
                            modifier.substring("RGB(".length, modifier.length - ")".length).split(",")
                        builder.color(
                            TextColor.color(
                                Integer.parseInt(values[0]),
                                Integer.parseInt(values[1]),
                                Integer.parseInt(values[2])
                            )
                        )
                    } else if (modifier.uppercase().startsWith("HEX(")) {
                        var hex = modifier.substring("HEX(".length, modifier.length - ")".length)
                        if (!hex.startsWith("#")) hex = "#" + hex
                        builder.color(TextColor.fromHexString(hex))
                    } else if (modifier.uppercase().startsWith("LINK(")) {
                        val link = modifier.substring("LINK(".length, modifier.length - ")".length)
                        builder.hoverEvent(HoverEvent.showText(Component.text("Click to open link!")))
                        builder.clickEvent(ClickEvent.openUrl(link))
                    }
                }
            }
        }
        return builder.build()
    }
}

fun Component.bold() = decorate(TextDecoration.BOLD)
fun Component.unitalic() = decoration(TextDecoration.ITALIC, false)
fun Component.strikethrough() = decorate(TextDecoration.STRIKETHROUGH)
fun Component.black() = color(NamedTextColor.BLACK)
fun Component.darkBlue() = color(NamedTextColor.DARK_BLUE)
fun Component.darkGreen() = color(NamedTextColor.DARK_GREEN)
fun Component.darkAqua() = color(NamedTextColor.DARK_AQUA)
fun Component.darkRed() = color(NamedTextColor.DARK_RED)
fun Component.darkPurple() = color(NamedTextColor.DARK_PURPLE)
fun Component.darkGray() = color(NamedTextColor.DARK_GRAY)
fun Component.gold() = color(NamedTextColor.GOLD)
fun Component.gray() = color(NamedTextColor.GRAY)
fun Component.blue() = color(NamedTextColor.BLUE)
fun Component.green() = color(NamedTextColor.GREEN)
fun Component.aqua() = color(NamedTextColor.AQUA)
fun Component.red() = color(NamedTextColor.RED)
fun Component.lightPurple() = color(NamedTextColor.LIGHT_PURPLE)
fun Component.yellow() = color(NamedTextColor.YELLOW)
fun Component.white() = color(NamedTextColor.WHITE)

fun String.bold() = Component.text(this).decorate(TextDecoration.BOLD)
fun String.black() = Component.text(this).color(NamedTextColor.BLACK)
fun String.darkBlue() = Component.text(this).color(NamedTextColor.DARK_BLUE)
fun String.darkGreen() = Component.text(this).color(NamedTextColor.DARK_GREEN)
fun String.darkAqua() = Component.text(this).color(NamedTextColor.DARK_AQUA)
fun String.darkRed() = Component.text(this).color(NamedTextColor.DARK_RED)
fun String.darkPurple() = Component.text(this).color(NamedTextColor.DARK_PURPLE)
fun String.darkGray() = Component.text(this).color(NamedTextColor.DARK_GRAY)
fun String.gold() = Component.text(this).color(NamedTextColor.GOLD)
fun String.gray() = Component.text(this).color(NamedTextColor.GRAY)
fun String.blue() = Component.text(this).color(NamedTextColor.BLUE)
fun String.green() = Component.text(this).color(NamedTextColor.GREEN)
fun String.aqua() = Component.text(this).color(NamedTextColor.AQUA)
fun String.red() = Component.text(this).color(NamedTextColor.RED)
fun String.lightPurple() = Component.text(this).color(NamedTextColor.LIGHT_PURPLE)
fun String.yellow() = Component.text(this).color(NamedTextColor.YELLOW)
fun String.white() = Component.text(this).color(NamedTextColor.WHITE)