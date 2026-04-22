import kotlin.math.*
import java.lang.reflect.Method
import java.lang.reflect.Modifier

sealed class MaidValue {
    data class IntVal(var value: Int) : MaidValue()
    data class FloatVal(var value: Float) : MaidValue()
    data class StringVal(var value: String) : MaidValue()
    data class CharVal(var value: Char) : MaidValue()
    data class FunctionVal(val node: AstNode.FuncDefNode, val closure: Scope) : MaidValue()
    data class KotlinFuncVal(
        val funcName: String,
        val className: String,
        val methodName: String
    ) : MaidValue()
    /** 原生函数值：由 Kotlin 宿主代码注册的外部函数，无需反射即可调用 */
    data class NativeFuncVal(
        val name: String,
        val func: (List<MaidValue>) -> MaidValue
    ) : MaidValue()
    object NullVal : MaidValue()

    fun asInt(): Int = when (this) {
        is IntVal -> value
        is FloatVal -> value.toInt()
        is StringVal -> value.toIntOrNull() ?: 0
        is CharVal -> value.code
        else -> 0
    }

    fun asFloat(): Float = when (this) {
        is IntVal -> value.toFloat()
        is FloatVal -> value
        is StringVal -> value.toFloatOrNull() ?: 0f
        is CharVal -> value.code.toFloat()
        else -> 0f
    }

    fun asBool(): Boolean = asInt() != 0

    override fun toString(): String = when (this) {
        is IntVal -> value.toString()
        is FloatVal -> value.toString()
        is StringVal -> value
        is CharVal -> value.toString()
        is FunctionVal -> "<function ${node.name}>"
        is KotlinFuncVal -> "<kotlin function $funcName>"
        is NativeFuncVal -> "<native function $name>"
        NullVal -> "null"
    }
}

class Scope(val parent: Scope? = null) {
    private val variables = mutableMapOf<String, MaidValue>()

    fun define(name: String, value: MaidValue) {
        variables[name] = value
    }

    fun assign(name: String, value: MaidValue) {
        if (variables.containsKey(name)) {
            variables[name] = value
        } else if (parent != null) {
            parent.assign(name, value)
        } else {
            // 变量未定义，自动定义它（隐式声明）
            variables[name] = value
        }
    }

    fun get(name: String): MaidValue {
        return variables[name] ?: parent?.get(name) ?: throw RuntimeException("Undefined variable: $name")
    }
}

class ReturnException(val value: MaidValue) : RuntimeException()

class Interpreter {
    val globalScope = Scope()
    /** 原生函数注册表：名称 -> (参数列表) -> MaidValue */
    private val nativeFunctions = mutableMapOf<String, (List<MaidValue>) -> MaidValue>()

    /**
     * 注册一个原生函数，供 MaidLang 中的 external fun 声明使用。
     * 注册后，MaidLang 代码可通过 external fun 声明后直接调用，无需反射。
     */
    fun registerNative(name: String, func: (List<MaidValue>) -> MaidValue) {
        nativeFunctions[name] = func
    }

    init {
        // 预定义布尔常量 true 和 false
        globalScope.define("true", MaidValue.IntVal(1))
        globalScope.define("false", MaidValue.IntVal(0))
        // 可选：预定义一些数学常量
        globalScope.define("PI", MaidValue.FloatVal(3.1415927f))
        // 内置函数 print 和 println
        globalScope.define("print", MaidValue.IntVal(0))
        globalScope.define("println", MaidValue.IntVal(0))
    }

    fun interpret(node: AstNode): MaidValue {
        return try {
            eval(node, globalScope)
        } catch (e: ReturnException) {
            e.value
        } catch (e: Exception) {
            println("Runtime Error: ${e.message}")
            MaidValue.NullVal
        }
    }

    private fun eval(node: AstNode, scope: Scope): MaidValue {
        return when (node) {
            is AstNode.IntNode -> MaidValue.IntVal(node.value)
            is AstNode.FloatNode -> MaidValue.FloatVal(node.value)
            is AstNode.StringNode -> MaidValue.StringVal(node.value)
            is AstNode.CharNode -> MaidValue.CharVal(node.value)
            is AstNode.Variable -> scope.get(node.name)
            
            is AstNode.CodeBlock -> {
                var lastValue: MaidValue = MaidValue.NullVal
                val innerScope = Scope(scope)
                for (code in node.codes) {
                    lastValue = eval(code, innerScope)
                }
                lastValue
            }

            is AstNode.BinaryOp -> {
                val left = eval(node.arg1, scope)
                val right = eval(node.arg2, scope)
                applyBinaryOp(node.op, left, right)
            }

            is AstNode.Unary -> {
                val value = eval(node.arg, scope)
                applyUnaryOp(node.op, value, node.arg, scope)
            }

            is AstNode.Assign -> {
                val value = eval(node.right, scope)
                when (val left = node.left) {
                    is AstNode.Variable -> scope.assign(left.name, value)
                    is AstNode.ProxyVar -> {
                        // TODO: Implement proxy variable assignment if needed
                        println("Warning: ProxyVar assignment not fully implemented")
                    }
                    else -> throw RuntimeException("Invalid assignment target")
                }
                value
            }

            is AstNode.If -> {
                val condition = eval(node.condition, scope)
                if (condition.asBool()) {
                    eval(node.thenBranch, scope)
                } else if (node.elseBranch != null) {
                    eval(node.elseBranch, scope)
                } else {
                    MaidValue.NullVal
                }
            }

            is AstNode.While -> {
                var lastValue: MaidValue = MaidValue.NullVal
                while (eval(node.condition, scope).asBool()) {
                    lastValue = eval(node.body, scope)
                }
                lastValue
            }

            is AstNode.For -> {
                // for (init; cond; inc) body
                // 使用新的作用域，使 init 中声明的变量不会泄漏到外部
                val forScope = Scope(scope)
                // 执行初始化子句
                node.initializer?.let { eval(it, forScope) }
                var lastValue: MaidValue = MaidValue.NullVal
                // 循环：条件为 null 时视为 true（无限循环）
                while (node.condition == null || eval(node.condition, forScope).asBool()) {
                    lastValue = eval(node.body, forScope)
                    // 执行增量子句
                    node.increment?.let { eval(it, forScope) }
                }
                lastValue
            }

            is AstNode.Return -> {
                val value = node.value?.let { eval(it, scope) } ?: MaidValue.NullVal
                throw ReturnException(value)
            }

            is AstNode.FuncDefNode -> {
                val func = MaidValue.FunctionVal(node, scope)
                scope.define(node.name, func)
                func
            }

            is AstNode.FuncCall -> {
                val funcValue = scope.get(node.funName)
                if (funcValue is MaidValue.FunctionVal) {
                    val funcDef = funcValue.node
                    val callScope = Scope(funcValue.closure)
                    
                    if (node.args.size != funcDef.args.size) {
                        throw RuntimeException("Function ${node.funName} expects ${funcDef.args.size} arguments, but got ${node.args.size}")
                    }

                    for (i in node.args.indices) {
                        callScope.define(funcDef.args[i], eval(node.args[i], scope))
                    }

                    try {
                        eval(funcDef.body, callScope)
                    } catch (e: ReturnException) {
                        e.value
                    }
                } else if (funcValue is MaidValue.KotlinFuncVal) {
                    // 调用 Kotlin 函数（通过反射）
                    callKotlinFunction(funcValue, node.args, scope)
                } else if (funcValue is MaidValue.NativeFuncVal) {
                    // 调用原生函数（免反射，直接调用 Kotlin lambda）
                    val args = node.args.map { eval(it, scope) }
                    funcValue.func(args)
                } else {
                    // Built-in functions or errors
                    when (node.funName) {
                        "print", "println" -> {
                            val args = node.args.map { eval(it, scope) }
                            // 自定义格式化，避免输出类型信息
                            val formatted = args.joinToString(" ") { value ->
                                when (value) {
                                    is MaidValue.IntVal -> value.value.toString()
                                    is MaidValue.FloatVal -> value.value.toString()
                                    is MaidValue.StringVal -> value.value
                                    is MaidValue.CharVal -> value.value.toString()
                                    is MaidValue.FunctionVal -> "<function ${value.node.name}>"
                                    is MaidValue.KotlinFuncVal -> "<kotlin function ${value.funcName}>"
                                    is MaidValue.NativeFuncVal -> "<native function ${value.name}>"
                                    MaidValue.NullVal -> "null"
                                }
                            }
                            if (node.funName == "println") {
                                println(formatted)
                            } else {
                                print(formatted)
                            }
                            MaidValue.NullVal
                        }
                        else -> throw RuntimeException("${node.funName} is not a function")
                    }
                }
            }
            
            is AstNode.ImportNode -> {
                // 解析导入路径：格式为 "fully.qualified.ClassName.methodName"
                val path = node.importPath
                val alias = node.alias
                
                // 从路径中提取类名和方法名
                val lastDot = path.lastIndexOf('.')
                if (lastDot == -1) {
                    throw RuntimeException("Invalid import path '$path'. Expected format: 'package.ClassName.methodName'")
                }
                val className = path.substring(0, lastDot)
                val methodName = path.substring(lastDot + 1)
                
                // 注册为 KotlinFuncVal
                val kotlinFunc = MaidValue.KotlinFuncVal(alias, className, methodName)
                scope.define(alias, kotlinFunc)
                println("Imported Kotlin function: $alias -> $className.$methodName")
                MaidValue.NullVal
            }

            is AstNode.ExternalFuncDecl -> {
                // 在原生函数注册表中查找并绑定
                val nativeFunc = nativeFunctions[node.name]
                if (nativeFunc == null) {
                    throw RuntimeException("External function '${node.name}' is not registered. " +
                            "Use interpreter.registerNative(\"${node.name}\") { ... } in Kotlin host code.")
                }
                val funcVal = MaidValue.NativeFuncVal(node.name, nativeFunc)
                scope.define(node.name, funcVal)
                MaidValue.NullVal
            }

            is AstNode.ProxyVar -> {
                // For now, let's treat it as a mock or simple array access if we had arrays
                // The user mentioned it's a "Kotlin 代理对象"
                val base = scope.get(node.name)
                val index = eval(node.position, scope)
                println("Accessing proxy ${node.name} at index $index")
                MaidValue.NullVal
            }
        }
    }

    private fun applyBinaryOp(op: OpType, left: MaidValue, right: MaidValue): MaidValue {
        return when (op) {
            OpType.ADD -> {
                if (left is MaidValue.StringVal || right is MaidValue.StringVal) {
                    MaidValue.StringVal(left.toString() + right.toString())
                } else if (left is MaidValue.FloatVal || right is MaidValue.FloatVal) {
                    MaidValue.FloatVal(left.asFloat() + right.asFloat())
                } else {
                    MaidValue.IntVal(left.asInt() + right.asInt())
                }
            }
            OpType.SUB -> if (left is MaidValue.FloatVal || right is MaidValue.FloatVal) MaidValue.FloatVal(left.asFloat() - right.asFloat()) else MaidValue.IntVal(left.asInt() - right.asInt())
            OpType.MUL -> if (left is MaidValue.FloatVal || right is MaidValue.FloatVal) MaidValue.FloatVal(left.asFloat() * right.asFloat()) else MaidValue.IntVal(left.asInt() * right.asInt())
            OpType.DIV -> if (left is MaidValue.FloatVal || right is MaidValue.FloatVal) MaidValue.FloatVal(left.asFloat() / right.asFloat()) else MaidValue.IntVal(left.asInt() / right.asInt())
            OpType.MOD -> MaidValue.IntVal(left.asInt() % right.asInt())
            
            OpType.EQUAL -> MaidValue.IntVal(if (compareValues(left, right) == 0) 1 else 0)
            OpType.NOT_EQUAL -> MaidValue.IntVal(if (compareValues(left, right) != 0) 1 else 0)
            OpType.GREATER -> MaidValue.IntVal(if (compareValues(left, right) > 0) 1 else 0)
            OpType.LESS -> MaidValue.IntVal(if (compareValues(left, right) < 0) 1 else 0)
            OpType.GREATER_EQUAL -> MaidValue.IntVal(if (compareValues(left, right) >= 0) 1 else 0)
            OpType.LESS_EQUAL -> MaidValue.IntVal(if (compareValues(left, right) <= 0) 1 else 0)
            
            OpType.LOGICAL_AND -> MaidValue.IntVal(if (left.asBool() && right.asBool()) 1 else 0)
            OpType.LOGICAL_OR -> MaidValue.IntVal(if (left.asBool() || right.asBool()) 1 else 0)
            
            OpType.BIT_AND -> MaidValue.IntVal(left.asInt() and right.asInt())
            OpType.BIT_OR -> MaidValue.IntVal(left.asInt() or right.asInt())
            OpType.BIT_XOR -> MaidValue.IntVal(left.asInt() xor right.asInt())
            
            else -> throw RuntimeException("Unsupported binary operator: $op")
        }
    }

    private fun compareValues(left: MaidValue, right: MaidValue): Int {
        return when {
            left is MaidValue.IntVal && right is MaidValue.IntVal -> left.value.compareTo(right.value)
            left is MaidValue.FloatVal || right is MaidValue.FloatVal -> left.asFloat().compareTo(right.asFloat())
            left is MaidValue.StringVal && right is MaidValue.StringVal -> left.value.compareTo(right.value)
            left is MaidValue.CharVal && right is MaidValue.CharVal -> left.value.compareTo(right.value)
            else -> left.toString().compareTo(right.toString())
        }
    }

    private fun applyUnaryOp(op: OpType, value: MaidValue, argNode: AstNode, scope: Scope): MaidValue {
        return when (op) {
            OpType.NOT -> MaidValue.IntVal(if (value.asBool()) 0 else 1)
            OpType.SUB -> {
                if (value is MaidValue.FloatVal) MaidValue.FloatVal(-value.value)
                else MaidValue.IntVal(-value.asInt())
            }
            OpType.SELF_ADD -> {
                if (argNode is AstNode.Variable) {
                    val newValue = if (value is MaidValue.FloatVal) MaidValue.FloatVal(value.value + 1) else MaidValue.IntVal(value.asInt() + 1)
                    scope.assign(argNode.name, newValue)
                    newValue
                } else throw RuntimeException("++ requires a variable")
            }
            OpType.SELF_MINUS -> {
                if (argNode is AstNode.Variable) {
                    val newValue = if (value is MaidValue.FloatVal) MaidValue.FloatVal(value.value - 1) else MaidValue.IntVal(value.asInt() - 1)
                    scope.assign(argNode.name, newValue)
                    newValue
                } else throw RuntimeException("-- requires a variable")
            }
            else -> throw RuntimeException("Unsupported unary operator: $op")
        }
    }

    /**
     * 通过反射调用 Kotlin/Java 静态方法。
     * importPath 格式: "fully.qualified.ClassName.methodName"
     */
    private fun callKotlinFunction(func: MaidValue.KotlinFuncVal, args: ArrayList<AstNode>, scope: Scope): MaidValue {
        // 计算参数值
        val argValues = args.map { eval(it, scope) }

        // 加载类
        val clazz: Class<*>
        try {
            clazz = Class.forName(func.className)
        } catch (e: ClassNotFoundException) {
            throw RuntimeException("Class not found: ${func.className}")
        }

        // 查找匹配的静态方法
        val methods = clazz.methods.filter { m ->
            m.name == func.methodName &&
            Modifier.isStatic(m.modifiers) &&
            m.parameterCount == argValues.size
        }

        if (methods.isEmpty()) {
            throw RuntimeException("Static method '${func.methodName}' with ${argValues.size} params not found on ${func.className}")
        }

        // 尝试每个匹配的方法，找到参数类型兼容的
        for (method in methods) {
            try {
                val jvmArgs = convertArgsToJvm(method, argValues)
                val result = method.invoke(null, *jvmArgs)
                return convertJvmToMaidValue(result)
            } catch (e: IllegalArgumentException) {
                // 参数类型不匹配，尝试下一个重载
                continue
            }
        }

        throw RuntimeException("Could not invoke ${func.className}.${func.methodName} with the given arguments")
    }

    /**
     * 将 MaidValue 参数列表转换为 JVM 方法调用所需的 Object[]。
     */
    private fun convertArgsToJvm(method: Method, args: List<MaidValue>): Array<Any?> {
        val paramTypes = method.parameterTypes
        return Array(paramTypes.size) { i ->
            val maidVal = args[i]
            val targetType = paramTypes[i]
            convertOneArg(maidVal, targetType)
        }
    }

    /**
     * 将单个 MaidValue 转换为目标 JVM 类型。
     */
    private fun convertOneArg(value: MaidValue, targetType: Class<*>): Any? {
        return when (value) {
            is MaidValue.IntVal -> {
                when (targetType) {
                    Int::class.javaPrimitiveType, Int::class.java -> value.value
                    Long::class.javaPrimitiveType, Long::class.java -> value.value.toLong()
                    Float::class.javaPrimitiveType, Float::class.java -> value.value.toFloat()
                    Double::class.javaPrimitiveType, Double::class.java -> value.value.toDouble()
                    Short::class.javaPrimitiveType, Short::class.java -> value.value.toShort()
                    Byte::class.javaPrimitiveType, Byte::class.java -> value.value.toByte()
                    String::class.java -> value.value.toString()
                    else -> value.value
                }
            }
            is MaidValue.FloatVal -> {
                when (targetType) {
                    Float::class.javaPrimitiveType, Float::class.java -> value.value
                    Double::class.javaPrimitiveType, Double::class.java -> value.value.toDouble()
                    Int::class.javaPrimitiveType, Int::class.java -> value.value.toInt()
                    Long::class.javaPrimitiveType, Long::class.java -> value.value.toLong()
                    String::class.java -> value.value.toString()
                    else -> value.value
                }
            }
            is MaidValue.StringVal -> {
                if (targetType == String::class.java) value.value
                else value.value
            }
            is MaidValue.CharVal -> {
                when (targetType) {
                    Char::class.javaPrimitiveType, Char::class.java -> value.value
                    Int::class.javaPrimitiveType, Int::class.java -> value.value.code
                    String::class.java -> value.value.toString()
                    else -> value.value
                }
            }
            MaidValue.NullVal -> null
            else -> value.toString()
        }
    }

    /**
     * 将 JVM 反射调用结果转换为 MaidValue。
     */
    private fun convertJvmToMaidValue(result: Any?): MaidValue {
        if (result == null) return MaidValue.NullVal
        return when (result) {
            is Int -> MaidValue.IntVal(result)
            is Long -> MaidValue.IntVal(result.toInt())
            is Short -> MaidValue.IntVal(result.toInt())
            is Byte -> MaidValue.IntVal(result.toInt())
            is Float -> MaidValue.FloatVal(result)
            is Double -> MaidValue.FloatVal(result.toFloat())
            is Boolean -> MaidValue.IntVal(if (result) 1 else 0)
            is Char -> MaidValue.CharVal(result)
            is String -> MaidValue.StringVal(result)
            else -> MaidValue.StringVal(result.toString())
        }
    }
}
