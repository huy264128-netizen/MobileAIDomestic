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

    fun consume(token: Token) {
        val current = rawToken.getOrNull(nowElement)
            ?: throw IllegalStateException("Unexpected end of input, expected token ${token.value}")
        if (current != token) {
            throw IllegalStateException("there should be a Token ${token.value} , but we can't find it")
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
    fun program(): AstNode.CodeBlock {
        val codes = arrayListOf<AstNode>()
        while (!isAtEnd()) {
            codes.add(declaration())
        }
        return AstNode.CodeBlock(codes)
    }

    /** 声明层级：处理变量定义(var)、函数定义(fun)等，若不是声明则降级为 statement */
    fun declaration(): AstNode {
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

    private fun typedVariableDeclaration(): AstNode {
        val type = parseType()
        val name = consumeIdentifier()
        val initExpr = if (match(LexState.OPERATOR, "=")) {
            expression()
        } else {
            // 可以没有初始化？暂时要求必须有
            throw IllegalStateException("Variable declaration requires initializer currently")
        }
        consumeOperator(";")
        nameTable[name] = type
        return AstNode.Assign(AstNode.Variable(name), initExpr)
    }

    private fun variableDeclaration(): AstNode {
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
            is AstNode.FuncCall -> Type.KotlinType // 函数调用暂视为 Kotlin 类型
            else -> Type.KotlinType // 未知类型
        }

        nameTable[name] = inferredType
        return AstNode.Assign(AstNode.Variable(name), initExpr)
    }

    private fun functionDeclaration(): AstNode {
        consumeIdentifier("fun")
        // 解析返回类型（可选）
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
                // 解析参数类型（可选）
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

    private fun functionDeclarationWithoutFun(): AstNode {
        // 已经位于类型关键字处，解析返回类型
        val returnType = parseType()  // 消耗类型关键字
        val name = consumeIdentifier()

        consumeOperator("(")
        val args = mutableListOf<String>()
        val argTypes = mutableListOf<Type>()

        if (!check(LexState.OPERATOR, ")")) {
            do {
                // 解析参数类型（可选）
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
        // 解析导入路径（字符串字面量）
        val pathToken = peek() ?: throw IllegalStateException("Expected import path string")
        if (pathToken.type != LexState.STRING) {
            throw IllegalStateException("Expected import path string, but found '${pathToken.value}'")
        }
        val importPath = pathToken.value
        nowElement++

        // 解析可选的 "as alias" 部分
        val alias: String = if (check(LexState.IDENTIFIER, "as")) {
            consumeIdentifier("as")
            consumeIdentifier()
        } else {
            // 如果没有 as，则使用导入路径的最后一段作为别名
            importPath.split(".").last()
        }

        consumeOperator(";")
        return AstNode.ImportNode(importPath, alias)
    }

    /**
     * 外部函数声明：external fun name(type1, type2, ...) -> returnType;
     *
     * 声明一个由 Kotlin 宿主代码注册的外部函数，无需反射即可调用。
     * 例：external fun abs(int) -> int;
     */
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
        // 解析可选的返回类型
        val returnType: Type = if (match(LexState.OPERATOR, "-") && match(LexState.OPERATOR, ">")) {
            parseType()
        } else {
            Type.VoidType
        }
        consumeOperator(";")
        return AstNode.ExternalFuncDecl(name, paramTypes, returnType)
    }

    /** 语句层级：处理 if, while, for, return 或 代码块 { } */
    fun statement(): AstNode = when {
        check(LexState.OPERATOR, "{") -> codeBlock()
        check(LexState.IDENTIFIER, "if") -> ifStatement()
        check(LexState.IDENTIFIER, "while") -> whileStatement()
        check(LexState.IDENTIFIER, "for") -> forStatement()
        check(LexState.IDENTIFIER, "return") -> returnStatement()
        else -> expressionStatement()
    }

    /** If 语句 - if (expr) stmt [else stmt] */
    fun ifStatement(): AstNode {
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

    /** While 语句 - while (expr) stmt */
    fun whileStatement(): AstNode {
        consumeIdentifier("while")
        consumeOperator("(")
        val condition = expression()
        consumeOperator(")")
        val body = statement()
        return AstNode.While(condition, body)
    }

    /**
     * For 语句 - for (init; cond; inc) stmt
     *
     * 支持 C 风格 for 循环：
     *   for (int i = 0; i < 10; i = i + 1) { ... }
     *   for (var i = 0; i < 10; i = i + 1) { ... }
     *   for (i = 0; i < 10; ++i) { ... }
     *   for (;;) { ... }  // 无限循环
     */
    fun forStatement(): AstNode {
        consumeIdentifier("for")
        consumeOperator("(")

        // 解析初始化子句：可以是变量声明、表达式或空
        val initializer: AstNode? = when {
            isTypeKeyword(peek()) -> {
                // 类型化变量声明：int i = 0
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
                // var 变量声明：var i = 0
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
                // 表达式
                expression()
            }
            else -> null // 空初始化子句
        }

        consumeOperator(";")

        // 解析条件子句（可选）
        val condition: AstNode? = if (!check(LexState.OPERATOR, ";")) {
            expression()
        } else null

        consumeOperator(";")

        // 解析增量子句（可选）
        val increment: AstNode? = if (!check(LexState.OPERATOR, ")")) {
            expression()
        } else null

        consumeOperator(")")

        val body = statement()
        return AstNode.For(initializer, condition, increment, body)
    }

    fun returnStatement(): AstNode {
        consumeIdentifier("return")
        val value = if (check(LexState.OPERATOR, ";")) null else expression()
        consumeOperator(";")
        return AstNode.Return(value)
    }

    /** 代码块：解析 { 语句1; 语句2; ... } */
    fun codeBlock(): AstNode.CodeBlock {
        consumeOperator("{")
        val codes = arrayListOf<AstNode>()
        while (!isAtEnd() && !check(LexState.OPERATOR, "}")) {
            codes.add(declaration())
        }
        consumeOperator("}")
        return AstNode.CodeBlock(codes)
    }

    /** 表达式语句：解析 expression() 并消耗末尾的分号 */
    fun expressionStatement(): AstNode {
        val expr = expression()
        consumeOperator(";")
        return expr
    }

    // --- 2. 表达式优先级 (Expression Precedence - 由低到高) ---

    /** 优先级 1: 总入口 */
    fun expression(): AstNode = assignment()

    /** 优先级 2: 赋值 (右结合) -> a = b = 5 */
    fun assignment(): AstNode {
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

    /** 优先级 3: 逻辑或 (||) */
    fun logicalOr(): AstNode {
        var node = logicalAnd()
        while (match(LexState.OPERATOR, "||")) {
            val right = logicalAnd()
            node = AstNode.BinaryOp(OpType.LOGICAL_OR, node, right)
        }
        return node
    }

    /** 优先级 4: 逻辑与 (&&) */
    fun logicalAnd(): AstNode {
        var node = equality()
        while (match(LexState.OPERATOR, "&&")) {
            val right = equality()
            node = AstNode.BinaryOp(OpType.LOGICAL_AND, node, right)
        }
        return node
    }

    /** 优先级 5: 相等性 (==, !=) */
    fun equality(): AstNode {
        var node = relational()
        while (true) {
            node = when {
                match(LexState.OPERATOR, "==") -> AstNode.BinaryOp(OpType.EQUAL, node, relational())
                match(LexState.OPERATOR, "!=") -> AstNode.BinaryOp(OpType.NOT_EQUAL, node, relational())
                else -> return node
            }
        }
    }

    /** 优先级 6: 比较 (<, >, <=, >=) */
    fun relational(): AstNode {
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

    /** 优先级 7: 加减 (+, -) */
    fun additive(): AstNode {
        var node = multiplicative()
        while (true) {
            node = when {
                match(LexState.OPERATOR, "+") -> AstNode.BinaryOp(OpType.ADD, node, multiplicative())
                match(LexState.OPERATOR, "-") -> AstNode.BinaryOp(OpType.SUB, node, multiplicative())
                else -> return node
            }
        }
    }

    /** 优先级 8: 乘除余 (*, /, %) */
    fun multiplicative(): AstNode {
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

    /** 优先级 9: 前缀一元运算 (!, -, ++, --) */
    fun unary(): AstNode {
        return when {
            match(LexState.OPERATOR, "!") -> AstNode.Unary(OpType.NOT, unary())
            match(LexState.OPERATOR, "-") -> AstNode.Unary(OpType.SUB, unary())
            match(LexState.OPERATOR, "++") -> AstNode.Unary(OpType.SELF_ADD, unary())
            match(LexState.OPERATOR, "--") -> AstNode.Unary(OpType.SELF_MINUS, unary())
            else -> postfix()
        }
    }

    fun primary(): AstNode {
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

    fun postfix(): AstNode {
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
