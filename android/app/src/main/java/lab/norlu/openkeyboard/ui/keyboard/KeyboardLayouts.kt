package lab.norlu.openkeyboard.ui.keyboard

enum class KeyboardState { ALPHA, SYMBOLS, NUMERIC_PAD }
enum class ShiftState { LOWER, SHIFTED, CAPS_LOCK }

data class SuggestionItem(val text: String, val isHighConfidence: Boolean)

sealed class KeyboardKey {
    data class Text(val char: String, val display: String? = null) : KeyboardKey() {
        val upperChar: String = char.uppercase()
        val lowerChar: String = char.lowercase()
    }
    object Shift : KeyboardKey()
    object Backspace : KeyboardKey()
    object Space : KeyboardKey()
    object Search : KeyboardKey()
    object Symbols : KeyboardKey()
    object Numeric : KeyboardKey()
    object Alpha : KeyboardKey()
    object Globe : KeyboardKey()
    object Settings : KeyboardKey()
    object Undo : KeyboardKey()
    data class Suggestion(val index: Int) : KeyboardKey()
    object SettingsClose : KeyboardKey()
    object ThemeLight : KeyboardKey()
    object ThemeDark : KeyboardKey()
    object None : KeyboardKey()

    override fun toString(): String = when(this) {
        is Text -> char
        is Shift -> "⇧"
        is Backspace -> "⌫"
        is Space -> " "
        is Search -> "__SEARCH__"
        is Symbols -> "?123"
        is Numeric -> "NUM"
        is Alpha -> "ABC"
        is Globe -> "🌐"
        is Settings -> "⚙️"
        is Undo -> "__UNDO__"
        is Suggestion -> "__SUG_${index}__"
        is SettingsClose -> "__CLOSE__"
        is ThemeLight -> "__LIGHT__"
        is ThemeDark -> "__DARK__"
        is None -> ""
    }
}

object KeyboardLayouts {
    val alphaRow1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P").map { KeyboardKey.Text(it) }
    val alphaRow2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L").map { KeyboardKey.Text(it) }
    val alphaRow3 = listOf(KeyboardKey.Shift) + listOf("Z", "X", "C", "V", "B", "N", "M").map { KeyboardKey.Text(it) } + listOf(KeyboardKey.Backspace)
    val alphaRow4 = listOf(KeyboardKey.Symbols, KeyboardKey.Text(","), KeyboardKey.Space, KeyboardKey.Text("."), KeyboardKey.Search)

    val symRow1 = listOf("[", "]", "{", "}", "/", "\\", "|", "_", "<", ">").map { KeyboardKey.Text(it) }
    val symRow2 = listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")").map { KeyboardKey.Text(it) }
    val symRow3 = listOf("-", "\"", ":", ";", ",", "?", "ª", "º").map { KeyboardKey.Text(it) }.toMutableList<KeyboardKey>().apply { add(KeyboardKey.Backspace) }
    val symRow4 = listOf(KeyboardKey.Alpha, KeyboardKey.Text(","), KeyboardKey.Space, KeyboardKey.Text("."), KeyboardKey.Search)

    val padRow1 = listOf("1", "2", "3").map { KeyboardKey.Text(it) } + listOf(KeyboardKey.Backspace)
    val padRow2 = listOf("4", "5", "6", ",").map { KeyboardKey.Text(it) }
    val padRow3 = listOf("7", "8", "9", ".").map { KeyboardKey.Text(it) }
    val padRow4 = listOf(KeyboardKey.Alpha, KeyboardKey.Text("0"), KeyboardKey.Space, KeyboardKey.Search)

    val alternatesMap = mapOf(
        "Q" to listOf("1"), "W" to listOf("2"), "E" to listOf("3", "É", "Ê"),
        "R" to listOf("4"), "T" to listOf("5"), "Y" to listOf("6"),
        "U" to listOf("7", "Ú"), "I" to listOf("8", "Í"), "O" to listOf("9", "Ó", "Õ", "Ô"),
        "P" to listOf("0"), "A" to listOf("Á", "À", "Ã", "Â"), "S" to listOf("@"),
        "D" to listOf(), "F" to listOf(), "G" to listOf("%"), "H" to listOf(), "J" to listOf(), "K" to listOf(), "L" to listOf(),
        "Z" to listOf("_"), "X" to listOf(), "C" to listOf("Ç"), "V" to listOf(), "B" to listOf(), "N" to listOf("!"), "M" to listOf("?")
    )

    val hintMap = mapOf(
        "Q" to "1", "W" to "2", "E" to "3", "R" to "4", "T" to "5",
        "Y" to "6", "U" to "7", "I" to "8", "O" to "9", "P" to "0",
        "S" to "@", "N" to "!", "G" to "%", "Z" to "_", "M" to "?"
    )

    fun getKeyWeight(key: KeyboardKey, state: KeyboardState): Float {
        if (state == KeyboardState.NUMERIC_PAD) return 1.0f
        return when (key) {
            is KeyboardKey.Space -> 4.0f
            is KeyboardKey.Shift, is KeyboardKey.Backspace, is KeyboardKey.Symbols, 
            is KeyboardKey.Alpha, is KeyboardKey.Numeric, is KeyboardKey.Search -> 1.5f
            else -> 1.0f
        }
    }
}
