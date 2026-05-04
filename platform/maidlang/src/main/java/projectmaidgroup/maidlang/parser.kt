package projectmaidgroup.maidlang

enum class OpType {
    ADD,
    SUB,
    MUL,
    DIV,
    MOD,
    GREATER,
    LESS,
    GREATER_EQUAL,
    LESS_EQUAL,
    EQUAL,
    NOT_EQUAL,
    LOGICAL_AND,
    LOGICAL_OR,
    BIT_AND,
    BIT_OR,
    BIT_XOR,
    SELF_ADD,
    SELF_MINUS,
    NOT,
    ASSIGN
}

/**
 * # 抽象语法树 (AST) 核心节点定义
 *
 * 本编译器采用递归下降法构建 AST，节点设计遵循“原子-运算-语句-结构”的层级。
 */
sealed class AstNode {
    data class IntNode(val value: Int) : AstNode()
    data class FloatNode(val value: Float) : AstNode()
    data class StringNode(val value: String) : AstNode()
    data class CharNode(val value: Char) : AstNode()
    data class Variable(val name: String) : AstNode()
    data class FuncCall(val funName: String, val args: ArrayList<AstNode>) : AstNode()
    data class BinaryOp(val op: OpType, val arg1: AstNode, val arg2: AstNode) : AstNode()
    data class Unary(val op: OpType, val arg: AstNode) : AstNode() //前缀表达式
    data class CodeBlock(val codes: ArrayList<AstNode>) : AstNode() //代码块
    data class ProxyVar(val name: String, val position: AstNode) : AstNode() //Kotlin 代理对象，表示name[position]
    data class Assign(val left: AstNode, val right: AstNode) : AstNode()
    data class FuncDefNode(val name: String, val args: List<String>, val body: CodeBlock) : AstNode()
    data class If(val condition: AstNode, val thenBranch: AstNode, val elseBranch: AstNode?) : AstNode()
    data class While(val condition: AstNode, val body: AstNode) : AstNode()
    data class For(val initializer: AstNode?, val condition: AstNode?, val increment: AstNode?, val body: AstNode) : AstNode()
    data class Return(val value: AstNode?) : AstNode()
    data class ImportNode(val importPath: String, val alias: String) : AstNode()
    data class ExternalFuncDecl(
        val name: String,
        val paramTypes: List<Type>,
        val returnType: Type
    ) : AstNode()
}

sealed class Type {
    object IntType : Type()
    object FloatType : Type()
    object CharType : Type()
    object StringType : Type()
    object BoolType : Type()
    object VoidType : Type()
    object KotlinType : Type()
    data class FunctionType(val paramTypes: List<Type>, val returnType: Type) : Type()
}

class parser(val rawToken: MutableList<Token>) {
    val nameTable = mutableMapOf<String, Type>()
    val funcDefine = mutableMapOf<String, Type.FunctionType>()
    var nowElement: Int = 0

    init {
        // 预定义布尔常量 true 和 false 为 int 类型，值分别为 1 和 0
        nameTable["true"] = Type.IntType
        nameTable["false"] = Type.IntType
    }

    private val typeKeywords = setOf("int", "float", "char", "string", "bool", "void")

    private fun isTypeKeyword(token: Token?): Boolean {
        return token != null && token.type == LexState.IDENTIFIER && token.value in typeKeywords
    }

    private fun isAtEnd(): Boolean = nowElement >= rawToken.size

    private fun peek(): Token? = rawToken.getOrNull(nowElement)

    private fun previous(): Token? = rawToken.getOrNull(nowElement - 1)

    private fun check(type: LexState, value: String? = null): Boolean {
        val token = peek() ?: return false
        if (token.type != type) return false
        if (value != null && token.value != value) return false
        return true
    }

    private fun match(type: LexState, value: String? = null): Boolean {
        if (check(type, value)) {
            nowElement++
            return true
        }
        return false
    }

    private fun consume(token: Token) {
        val current = rawToken.getOrNull(nowElement)
            ?: throw IllegalStateException("Unexpected end of input, expected token ${token.value}")
        if (current != token) {
            throw IllegalStateException("there should be a projectmaidgroup.maidlang.Token ${token.value} , but we can't find it")
        }
        nowElement++
    }

    private fun consumeOperator(op: String) {
        val current = rawToken.getOrNull(nowElement)
            ?: throw IllegalStateException("Unexpected end of input, expected operator '$op'")
        if (current.type != LexState.OPERATOR || current.value != op) {
            throw IllegalStateException("Expected operator '$op', but found '${current.value}'")
        }
        nowElement++
    }

    private fun consumeIdentifier(expected: String? = null): String {
        val current = rawToken.getOrNull(nowElement)
            ?: throw IllegalStateException("Unexpected end of input, expected identifier")
        if (current.type != LexState.IDENTIFIER) {
            throw IllegalStateException("Expected identifier, but found '${current.value}'")
        }
        if (expected != null && current.value != expected) {
            throw IllegalStateException("Expected identifier '$expected', but found '${current.value}'")
        }
        nowElement++
        return current.value
    }

    /** 解析整个程序：由多个声明或语句组成 */
    suspend fun program(): AstNode.CodeBlock {
        val codes = arrayListOf<AstNode>()
        while (!isAtEnd()) {
            codes.add(declaration())
        }
        return AstNode.CodeBlock(codes)
    }

    /** 声明层级：处理变量定义(var)、函数定义(fun)等，若不是声明则降级为 statement */
    private suspend fun declaration(): AstNode {
        val current = peek() ?: throw IllegalStateException("Unexpected end of input in declaration")
        return when {
            current.type == LexState.IDENTIFIER && current.value == "import" -> importDeclaration()
            isTypeKeyword(current) -> {
                // 检查是否是函数声明（类型关键字后接标识符再接 '('）
                val next1 = rawToken.getOrNull(nowElement + 1)
                val next2 = rawToken.getOrNull(nowElement + 2)
                if (next1?.type == LexState.IDENTIFIER && next2?.type == LexState.OPERATOR && next2.value == "(") {
                    functionDeclarationWithoutFun()
                } else {
                    typedVariableDeclaration()
                }
            }
            current.type == LexState.IDENTIFIER && current.value == "var" -> variableDeclaration()
            current.type == LexState.IDENTIFIER && current.value == "fun" -> functionDeclaration()
            current.type == LexState.IDENTIFIER && current.value == "external" -> externalDeclaration()
            else -> statement()
        }
    }

    private fun parseType(): Type {
        val token = peek() ?: throw IllegalStateException("Expected type keyword")
        if (!isTypeKeyword(token)) {
            throw IllegalStateException("Expected type keyword but got ${token.value}")
        }
        nowElement++
        return when (token.value) {
            "int" -> Type.IntType
            "float" -> Type.FloatType
            "char" -> Type.CharType
            "string" -> Type.StringType
            "bool" -> Type.BoolType
            "void" -> Type.VoidType
            else -> throw IllegalStateException("Unknown type keyword ${token.value}")
        }
    }

    private suspend fun typedVariableDeclaration(): AstNode {
        val type = parseType()
        val name = consumeIdentifier()
        val initExpr = if (match(LexState.OPERATOR, "=")) {
            expression()
        } else {
            throw IllegalStateException("Variable declaration requires initializer currently")
        }
        consumeOperator(";")
        nameTable[name] = type
        return AstNode.Assign(AstNode.Variable(name), initExpr)
    }

    private suspend fun variableDeclaration(): AstNode {
        consumeIdentifier("var")
        val name = consumeIdentifier()

        val initExpr = if (match(LexState.OPERATOR, "=")) {
            expression()
        } else {
            throw IllegalStateException("Variable declaration requires initializer currently")
        }

        consumeOperator(";")

        val inferredType = when (initExpr) {
            is AstNode.IntNode -> Type.IntType
            is AstNode.FloatNode -> Type.FloatType
            is AstNode.CharNode -> Type.CharType
            is AstNode.StringNode -> Type.StringType
            is AstNode.FuncCall -> Type.KotlinType
            else -> Type.KotlinType
        }

        nameTable[name] = inferredType
        return AstNode.Assign(AstNode.Variable(name), initExpr)
    }

    private suspend fun functionDeclaration(): AstNode {
        consumeIdentifier("fun")
        val returnType = if (isTypeKeyword(peek())) {
            parseType()
        } else {
            Type.VoidType
        }
        val name = consumeIdentifier()

        consumeOperator("(")
        val args = mutableListOf<String>()
        val argTypes = mutableListOf<Type>()

        if (!check(LexState.OPERATOR, ")")) {
            do {
                val argType = if (isTypeKeyword(peek())) {
                    parseType()
                } else {
                    Type.KotlinType
                }
                val argName = consumeIdentifier()
                args.add(argName)
                argTypes.add(argType)
                nameTable[argName] = argType
            } while (match(LexState.OPERATOR, ","))
        }

        consumeOperator(")")

        val funcType = Type.FunctionType(argTypes, returnType)
        funcDefine[name] = funcType

        val body = codeBlock()
        return AstNode.FuncDefNode(name, args, body)
    }

    private suspend fun functionDeclarationWithoutFun(): AstNode {
        val returnType = parseType()
        val name = consumeIdentifier()

        consumeOperator("(")
        val args = mutableListOf<String>()
        val argTypes = mutableListOf<Type>()

        if (!check(LexState.OPERATOR, ")")) {
            do {
                val argType = if (isTypeKeyword(peek())) {
                    parseType()
                } else {
                    Type.KotlinType
                }
                val argName = consumeIdentifier()
                args.add(argName)
                argTypes.add(argType)
                nameTable[argName] = argType
            } while (match(LexState.OPERATOR, ","))
        }

        consumeOperator(")")

        val funcType = Type.FunctionType(argTypes, returnType)
        funcDefine[name] = funcType

        val body = codeBlock()
        return AstNode.FuncDefNode(name, args, body)
    }

    private fun importDeclaration(): AstNode {
        consumeIdentifier("import")
        val pathToken = peek() ?: throw IllegalStateException("Expected import path string")
        if (pathToken.type != LexState.STRING) {
            throw IllegalStateException("Expected import path string, but found '${pathToken.value}'")
        }
        val importPath = pathToken.value
        nowElement++

        val alias: String = if (check(LexState.IDENTIFIER, "as")) {
            consumeIdentifier("as")
            consumeIdentifier()
        } else {
            importPath.split(".").last()
        }

        consumeOperator(";")
        return AstNode.ImportNode(importPath, alias)
    }

    private fun externalDeclaration(): AstNode {
        consumeIdentifier("external")
        consumeIdentifier("fun")
        val name = consumeIdentifier()
        consumeOperator("(")
        val paramTypes = mutableListOf<Type>()
        if (!check(LexState.OPERATOR, ")")) {
            do {
                val paramType = parseType()
                paramTypes.add(paramType)
            } while (match(LexState.OPERATOR, ","))
        }
        consumeOperator(")")
        val returnType: Type = if (match(LexState.OPERATOR, "-") && match(LexState.OPERATOR, ">")) {
            parseType()
        } else {
            Type.VoidType
        }
        consumeOperator(";")
        return AstNode.ExternalFuncDecl(name, paramTypes, returnType)
    }

    private suspend fun statement(): AstNode = when {
        check(LexState.OPERATOR, "{") -> codeBlock()
        check(LexState.IDENTIFIER, "if") -> ifStatement()
        check(LexState.IDENTIFIER, "while") -> whileStatement()
        check(LexState.IDENTIFIER, "for") -> forStatement()
        check(LexState.IDENTIFIER, "return") -> returnStatement()
        else -> expressionStatement()
    }

    private suspend fun ifStatement(): AstNode {
        consumeIdentifier("if")
        consumeOperator("(")
        val condition = expression()
        consumeOperator(")")
        val thenBranch = statement()
        val elseBranch = if (check(LexState.IDENTIFIER, "else")) {
            consumeIdentifier("else")
            statement()
        } else null
        return AstNode.If(condition, thenBranch, elseBranch)
    }

    private suspend fun whileStatement(): AstNode {
        consumeIdentifier("while")
        consumeOperator("(")
        val condition = expression()
        consumeOperator(")")
        val body = statement()
        return AstNode.While(condition, body)
    }

    private suspend fun forStatement(): AstNode {
        consumeIdentifier("for")
        consumeOperator("(")

        val initializer: AstNode? = when {
            isTypeKeyword(peek()) -> {
                val type = parseType()
                val name = consumeIdentifier()
                val initExpr = if (match(LexState.OPERATOR, "=")) {
                    expression()
                } else {
                    null
                }
                nameTable[name] = type
                AstNode.Assign(AstNode.Variable(name), initExpr ?: AstNode.IntNode(0))
            }
            check(LexState.IDENTIFIER, "var") -> {
                consumeIdentifier("var")
                val name = consumeIdentifier()
                val initExpr = if (match(LexState.OPERATOR, "=")) {
                    expression()
                } else {
                    throw IllegalStateException("for loop var declaration requires initializer")
                }
                val inferredType = when (initExpr) {
                    is AstNode.IntNode -> Type.IntType
                    is AstNode.FloatNode -> Type.FloatType
                    is AstNode.CharNode -> Type.CharType
                    is AstNode.StringNode -> Type.StringType
                    else -> Type.KotlinType
                }
                nameTable[name] = inferredType
                AstNode.Assign(AstNode.Variable(name), initExpr)
            }
            !check(LexState.OPERATOR, ";") -> {
                expression()
            }
            else -> null
        }

        consumeOperator(";")

        val condition: AstNode? = if (!check(LexState.OPERATOR, ";")) {
            expression()
        } else null

        consumeOperator(";")

        val increment: AstNode? = if (!check(LexState.OPERATOR, ")")) {
            expression()
        } else null

        consumeOperator(")")

        val body = statement()
        return AstNode.For(initializer, condition, increment, body)
    }

    private suspend fun returnStatement(): AstNode {
        consumeIdentifier("return")
        val value = if (check(LexState.OPERATOR, ";")) null else expression()
        consumeOperator(";")
        return AstNode.Return(value)
    }

    private suspend fun codeBlock(): AstNode.CodeBlock {
        consumeOperator("{")
        val codes = arrayListOf<AstNode>()
        while (!isAtEnd() && !check(LexState.OPERATOR, "}")) {
            codes.add(declaration())
        }
        consumeOperator("}")
        return AstNode.CodeBlock(codes)
    }

    private suspend fun expressionStatement(): AstNode {
        val expr = expression()
        consumeOperator(";")
        return expr
    }

    private suspend fun expression(): AstNode = assignment()

    private suspend fun assignment(): AstNode {
        val left = logicalOr()

        if (match(LexState.OPERATOR, "=")) {
            val right = assignment()
            if (left is AstNode.Variable || left is AstNode.ProxyVar) {
                return AstNode.Assign(left, right)
            }
            throw IllegalStateException("Invalid assignment target")
        }

        return left
    }

    private suspend fun logicalOr(): AstNode {
        var node = logicalAnd()
        while (match(LexState.OPERATOR, "||")) {
            val right = logicalAnd()
            node = AstNode.BinaryOp(OpType.LOGICAL_OR, node, right)
        }
        return node
    }

    private suspend fun logicalAnd(): AstNode {
        var node = equality()
        while (match(LexState.OPERATOR, "&&")) {
            val right = equality()
            node = AstNode.BinaryOp(OpType.LOGICAL_AND, node, right)
        }
        return node
    }

    private suspend fun equality(): AstNode {
        var node = relational()
        while (true) {
            node = when {
                match(LexState.OPERATOR, "==") -> AstNode.BinaryOp(OpType.EQUAL, node, relational())
                match(LexState.OPERATOR, "!=") -> AstNode.BinaryOp(OpType.NOT_EQUAL, node, relational())
                else -> return node
            }
        }
    }

    private suspend fun relational(): AstNode {
        var node = additive()
        while (true) {
            node = when {
                match(LexState.OPERATOR, "<") -> AstNode.BinaryOp(OpType.LESS, node, additive())
                match(LexState.OPERATOR, ">") -> AstNode.BinaryOp(OpType.GREATER, node, additive())
                match(LexState.OPERATOR, "<=") -> AstNode.BinaryOp(OpType.LESS_EQUAL, node, additive())
                match(LexState.OPERATOR, ">=") -> AstNode.BinaryOp(OpType.GREATER_EQUAL, node, additive())
                else -> return node
            }
        }
    }

    private suspend fun additive(): AstNode {
        var node = multiplicative()
        while (true) {
            node = when {
                match(LexState.OPERATOR, "+") -> AstNode.BinaryOp(OpType.ADD, node, multiplicative())
                match(LexState.OPERATOR, "-") -> AstNode.BinaryOp(OpType.SUB, node, multiplicative())
                else -> return node
            }
        }
    }

    private suspend fun multiplicative(): AstNode {
        var node = unary()
        while (true) {
            node = when {
                match(LexState.OPERATOR, "*") -> AstNode.BinaryOp(OpType.MUL, node, unary())
                match(LexState.OPERATOR, "/") -> AstNode.BinaryOp(OpType.DIV, node, unary())
                match(LexState.OPERATOR, "%") -> AstNode.BinaryOp(OpType.MOD, node, unary())
                else -> return node
            }
        }
    }

    private suspend fun unary(): AstNode {
        return when {
            match(LexState.OPERATOR, "!") -> AstNode.Unary(OpType.NOT, unary())
            match(LexState.OPERATOR, "-") -> AstNode.Unary(OpType.SUB, unary())
            match(LexState.OPERATOR, "++") -> AstNode.Unary(OpType.SELF_ADD, unary())
            match(LexState.OPERATOR, "--") -> AstNode.Unary(OpType.SELF_MINUS, unary())
            else -> postfix()
        }
    }

    private suspend fun primary(): AstNode {
        val current = rawToken.getOrNull(nowElement)
            ?: throw IllegalStateException("Unexpected end of input at position $nowElement")

        val ans = when (current.type) {
            LexState.INTEGER -> AstNode.IntNode(current.value.toInt())
            LexState.FLOAT -> AstNode.FloatNode(current.value.toFloat())
            LexState.STRING -> AstNode.StringNode(current.value)
            LexState.CHAR -> AstNode.CharNode(current.value[0])

            LexState.IDENTIFIER -> {
                val name = current.value
                AstNode.Variable(name)
            }

            LexState.OPERATOR -> {
                if (current.value == "(") {
                    nowElement++
                    val node = expression()
                    consume(Token(LexState.OPERATOR, ")"))
                    return node
                } else {
                    throw IllegalArgumentException("Unexpected operator '${current.value}' in primary expression")
                }
            }

            else -> throw IllegalArgumentException("Unsupported token type ${current.type} in primary")
        }

        nowElement++
        return ans
    }

    private suspend fun postfix(): AstNode {
        var node = primary()
        while (true) {
            when {
                match(LexState.OPERATOR, "(") -> {
                    val args = arrayListOf<AstNode>()
                    if (!check(LexState.OPERATOR, ")")) {
                        do {
                            args.add(expression())
                        } while (match(LexState.OPERATOR, ","))
                    }
                    consumeOperator(")")
                    node = if (node is AstNode.Variable) {
                        AstNode.FuncCall(node.name, args)
                    } else {
                        throw IllegalStateException("Can only call variables as functions")
                    }
                }
                match(LexState.OPERATOR, "[") -> {
                    val index = expression()
                    consumeOperator("]")
                    node = if (node is AstNode.Variable) {
                        AstNode.ProxyVar(node.name, index)
                    } else {
                        throw IllegalStateException("Can only use [] on variables")
                    }
                }
                else -> return node
            }
        }
    }
}
