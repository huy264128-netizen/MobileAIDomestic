val operators = setOf(
    '!',
    '@',
    '#',
    '$',
    '%',
    '^',
    '&',
    '*',
    '(',
    ')',
    '[',
    ']',
    '-',
    '+',
    '=',
    '{',
    '}',
    ':',
    ';',
    '<',
    '>',
    ',',
    '.',
    '/',
    '?',
    '|'
)
val enableToLinkWithEqual = setOf("+", "-", "*", "/", "|", "%", "&", "^", "=", "<", ">", "!")
val enableToDouble = setOf("+", "-", "&", "|")
val whiteSpace = setOf(' ', '\n', '\r', '\t')
val escapeCharMap = mapOf('n' to '\n', 't' to '\t', '\'' to '\'')
val typeKeywords = setOf("int", "float", "char", "string", "bool", "void")

data class Token(val type: LexState, val value: String) //define same as state


enum class LexState(val id: Int) {
    IDLE(0), IDENTIFIER(1), INTEGER(2), FLOAT(3), CHAR(4), STRING(5), OPERATOR(6), ANNOTATION(7)
}

suspend fun lexer(rawText: String): MutableList<Token> {
    var state = LexState.IDLE  // 0 -> Null Statement 1-> Identify 2->Int 3->Float 4:Char 5:String 6:Operator
    var catchSlash: Boolean = false //  捕捉到了反斜杠时为 true
    var lexerBuffer: String = ""
    val tokenList = mutableListOf<Token>()
    for (character: Char in rawText) {

        when (state) {
            LexState.IDLE -> {
                when {
                    character.isDigit() -> {
                        lexerBuffer += character
                        state = LexState.INTEGER //switch to Int mode
                    }

                    character == '.' -> {
                        lexerBuffer = "."
                        state = LexState.FLOAT //switch to Float mode
                    }

                    character == '\'' -> state = LexState.CHAR //switch to Char mode

                    character == '"' -> state = LexState.STRING //switch to String mode
                    operators.contains(character) -> {
                        state = LexState.OPERATOR
                        lexerBuffer += character
                    }

                    whiteSpace.contains(character) -> continue
                    else -> {
                        state = LexState.IDENTIFIER
                        lexerBuffer += character
                    }//switch to Identify mode
                }
            }

            LexState.IDENTIFIER -> {
                if (character == '\'' || character == '"') {
                    tokenList.add(Token(LexState.IDENTIFIER, lexerBuffer))
                    lexerBuffer = ""
                    state = when (character) {
                        '\'' -> LexState.CHAR
                        '"' -> LexState.STRING
                        else -> LexState.IDLE
                    }
                    continue
                }
                if (operators.contains(character) || whiteSpace.contains(character)) {
                    tokenList.add(Token(LexState.IDENTIFIER, lexerBuffer))
                    lexerBuffer = ""
                    if (operators.contains(character)) {
                        lexerBuffer += character
                        state = LexState.OPERATOR
                        continue
                    }
                    state = LexState.IDLE
                } else lexerBuffer += character
            }

            LexState.INTEGER -> {
                if (character == '.' || character == 'e') {
                    lexerBuffer += character
                    state = LexState.FLOAT
                    continue
                }
                if (operators.contains(character) || whiteSpace.contains(character)) {
                    tokenList.add(Token(LexState.INTEGER, lexerBuffer))
                    lexerBuffer = ""
                    if (operators.contains(character)) {
                        lexerBuffer += character
                        state = LexState.OPERATOR
                        continue
                    }
                    state = LexState.IDLE
                    continue
                }
                if (character.isDigit()) lexerBuffer += character
                else throw IllegalStateException("string $lexerBuffer is a illegal value")
            }

            LexState.FLOAT -> {
                if (operators.contains(character) || whiteSpace.contains(character)) {
                    tokenList.add(Token(LexState.FLOAT, lexerBuffer))
                    lexerBuffer = ""
                    if (operators.contains(character)) {
                        lexerBuffer += character
                        state = LexState.OPERATOR
                        continue
                    }
                    state = LexState.IDLE
                    continue
                }
                if (character.isDigit()) lexerBuffer += character
                else throw IllegalStateException("string $lexerBuffer is not a illegal value")
            }

            LexState.CHAR -> {
                if (character == '\\' && !catchSlash) {
                    catchSlash = true
                    continue
                }
                val currentCharacter: Char = when (catchSlash) {
                    true -> run {
                        val mappedChar = escapeCharMap[character]
                        if (mappedChar != null) return@run mappedChar
                        return@run character
                    }

                    false -> character
                }


                if (character == '\'') {
                    if (catchSlash) {
                        lexerBuffer += '\''
                        continue
                    }
                    if (lexerBuffer.length != 1) {
                        throw IllegalStateException("Type Char must have only one Character \u270B\uD83D\uDE2D\uD83E\uDD1A")
                    }
                    catchSlash = false
                    tokenList.add(Token(LexState.CHAR, lexerBuffer))
                    lexerBuffer = ""
                    state = LexState.IDLE
                    continue
                }
                catchSlash = false;
                if (lexerBuffer.isNotEmpty()) {
                    throw IllegalStateException("Type Char must have only one Character \u270B\uD83D\uDE2D\uD83E\uDD1A")
                } else {
                    lexerBuffer += currentCharacter
                }
            }

            LexState.STRING -> {
                if (character == '\\' && !catchSlash) {
                    catchSlash = true
                    continue
                }
                if (catchSlash) {
                    val currentChar = escapeCharMap[character];
                    if (currentChar != null) lexerBuffer += currentChar
                    else lexerBuffer += character
                    catchSlash = false
                    continue
                }
                if (character == '"') {
                    tokenList.add(Token(LexState.STRING, lexerBuffer))
                    lexerBuffer = ""
                    state = LexState.IDLE
                    continue
                }
                lexerBuffer += character
            }

            LexState.OPERATOR -> {
                if (character == '\'' || character == '"') {
                    tokenList.add(Token(LexState.OPERATOR, lexerBuffer))
                    lexerBuffer = ""
                    state = when (character) {
                        '\'' -> LexState.CHAR
                        '"' -> LexState.STRING
                        else -> LexState.IDLE
                    }
                    continue
                }
                if (character == '/' && lexerBuffer == "/") {
                    lexerBuffer = ""
                    state = LexState.ANNOTATION
                    continue
                }
                if (whiteSpace.contains(character) || character.isDigit()) {
                    tokenList.add(Token(LexState.OPERATOR, lexerBuffer))
                    lexerBuffer = ""
                    if (character.isDigit()) {
                        lexerBuffer += character
                        state = LexState.INTEGER
                        continue
                    }
                    state = LexState.IDLE
                    continue
                }
                if (enableToLinkWithEqual.contains(lexerBuffer) && character == '=') {
                    lexerBuffer += '='
                    tokenList.add(Token(LexState.OPERATOR, lexerBuffer))
                    lexerBuffer = ""
                    state = LexState.IDLE
                    continue
                }
                if (enableToDouble.contains(character.toString()) && lexerBuffer.length == 1 && lexerBuffer[0] == character) {
                    lexerBuffer += character
                    tokenList.add(Token(LexState.OPERATOR, lexerBuffer))
                    lexerBuffer = ""
                    state = LexState.IDLE
                    continue
                }
                tokenList.add(Token(LexState.OPERATOR, lexerBuffer))
                lexerBuffer = ""
                when {
                    character.isDigit() -> {
                        lexerBuffer += character
                        state = LexState.INTEGER //switch to Int mode
                    }

                    character == '.' -> {
                        lexerBuffer = "."
                        state = LexState.FLOAT
                    }
                    operators.contains(character)->{
                        lexerBuffer+=character
                        state=LexState.OPERATOR
                    }
                    setOf(' ', '\n', '\t').contains(character) -> state=LexState.IDLE
                    else -> {
                        state = LexState.IDENTIFIER
                        lexerBuffer += character
                    }//switch to Identify mode
                }
            }

            LexState.ANNOTATION -> {
                if (character == '\n') state = LexState.IDLE
            }
        }
    }
    if (state == LexState.ANNOTATION)
        return tokenList
    if (state == LexState.CHAR) {
        throw IllegalStateException("are you missing another \'\"\' ?")
    }
    if (state == LexState.STRING) {
        throw IllegalStateException("are you missing another \"'\" ?")
    }
    if (state != LexState.IDLE)
        tokenList.add(Token(state, lexerBuffer))
    return tokenList
}
