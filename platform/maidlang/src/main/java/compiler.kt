/**
 * MaidLang 编译器
 *
 * 将抽象语法树（AST）编译为虚拟机字节码。
 */

import MaidValue
import AstNode
import OpType
import Opcode
import BytecodeChunk

/**
 * 编译上下文，记录编译过程中的状态。
 */
class CompilationContext {
    /** 当前正在编译的字节码块。 */
    val chunk = BytecodeChunk()
    
    /** 全局变量名到常量池索引的映射（临时方案）。 */
    private val globalVarIndices = mutableMapOf<String, Int>()
    private var nextVarIndex = 0
    
    /** 获取或分配一个全局变量的索引。 */
    fun getOrAllocVarIndex(name: String): Int {
        return globalVarIndices.getOrPut(name) { nextVarIndex++ }
    }
    
    /** 常量池快捷方法。 */
    fun addConstant(value: MaidValue): Int = chunk.constants.addConstant(value)
}

/**
 * 编译器主类。
 */
class Compiler {
    private val context = CompilationContext()
    
    /** 编译一个 AST 节点，返回生成的字节码块。 */
    fun compile(node: AstNode): BytecodeChunk {
        compileNode(node)
        return context.chunk
    }
    
    /** 递归编译节点。 */
    private fun compileNode(node: AstNode) {
        when (node) {
            is AstNode.IntNode -> {
                val constIndex = context.addConstant(MaidValue.IntVal(node.value))
                context.chunk.emit(Opcode.LOAD_INT, constIndex)
            }
            is AstNode.FloatNode -> {
                val constIndex = context.addConstant(MaidValue.FloatVal(node.value))
                context.chunk.emit(Opcode.LOAD_FLOAT, constIndex)
            }
            is AstNode.StringNode -> {
                val constIndex = context.addConstant(MaidValue.StringVal(node.value))
                context.chunk.emit(Opcode.LOAD_STRING, constIndex)
            }
            is AstNode.CharNode -> {
                val constIndex = context.addConstant(MaidValue.CharVal(node.value))
                context.chunk.emit(Opcode.LOAD_CHAR, constIndex)
            }
            is AstNode.Variable -> {
                // 加载变量的值（假设为全局变量）
                val varIndex = context.getOrAllocVarIndex(node.name)
                context.chunk.emit(Opcode.LOAD_VAR, varIndex)
            }
            is AstNode.BinaryOp -> {
                // 先编译左操作数，再编译右操作数
                compileNode(node.arg1)
                compileNode(node.arg2)
                // 根据操作符生成对应的字节码
                val opcode = when (node.op) {
                    OpType.ADD -> Opcode.ADD
                    OpType.SUB -> Opcode.SUB
                    OpType.MUL -> Opcode.MUL
                    OpType.DIV -> Opcode.DIV
                    OpType.MOD -> Opcode.MOD
                    OpType.GREATER -> Opcode.GREATER
                    OpType.LESS -> Opcode.LESS
                    OpType.GREATER_EQUAL -> Opcode.GREATER_EQUAL
                    OpType.LESS_EQUAL -> Opcode.LESS_EQUAL
                    OpType.EQUAL -> Opcode.EQUAL
                    OpType.NOT_EQUAL -> Opcode.NOT_EQUAL
                    OpType.LOGICAL_AND -> Opcode.LOGICAL_AND
                    OpType.LOGICAL_OR -> Opcode.LOGICAL_OR
                    OpType.BIT_AND -> Opcode.BIT_AND
                    OpType.BIT_OR -> Opcode.BIT_OR
                    OpType.BIT_XOR -> Opcode.BIT_XOR
                    else -> throw IllegalArgumentException("Unsupported binary operator: ${node.op}")
                }
                context.chunk.emit(opcode)
            }
            is AstNode.Unary -> {
                compileNode(node.arg)
                val opcode = when (node.op) {
                    OpType.NOT -> Opcode.NOT
                    OpType.SUB -> Opcode.NEGATE  // 负号
                    else -> throw IllegalArgumentException("Unsupported unary operator: ${node.op}")
                }
                context.chunk.emit(opcode)
            }
            is AstNode.Assign -> {
                // 赋值：先编译右值，然后存储到左值变量
                // 左值目前只支持 Variable 节点（暂不考虑 ProxyVar）
                if (node.left is AstNode.Variable) {
                    compileNode(node.right)
                    val varIndex = context.getOrAllocVarIndex((node.left as AstNode.Variable).name)
                    context.chunk.emit(Opcode.STORE_VAR, varIndex)
                } else {
                    throw IllegalStateException("Unsupported assignment target: ${node.left}")
                }
            }
            is AstNode.CodeBlock -> {
                // 代码块：依次编译每条语句，每条语句的结果会被留在栈上，但我们会弹出丢弃（除非需要保留最后的值）
                for (stmt in node.codes) {
                    compileNode(stmt)
                    // 语句执行后栈顶会有一个值，如果不是最后一条语句，我们将其弹出丢弃
                    // 这里简化：总是弹出，最后一条语句的值会保留（由调用者处理）
                }
            }
            is AstNode.If -> {
                // 条件跳转：先编译条件表达式
                compileNode(node.condition)
                // 生成条件跳转指令，跳过 then 分支
                val jumpIfFalsePos = context.chunk.code.size
                context.chunk.emit(Opcode.JUMP_IF_FALSE, 0) // 偏移量占位
                // 编译 then 分支
                compileNode(node.thenBranch)
                // 如果有 else 分支，需要跳过 else 分支
                val jumpPos = context.chunk.code.size
                context.chunk.emit(Opcode.JUMP, 0) // 占位
                // 修正 JUMP_IF_FALSE 的偏移量，跳转到 else 分支或之后
                val elseStart = context.chunk.code.size
                context.chunk.code[jumpIfFalsePos + 1] = elseStart
                // 编译 else 分支（如果有）
                if (node.elseBranch != null) {
                    compileNode(node.elseBranch)
                }
                // 修正 JUMP 的偏移量，跳转到代码结尾
                val afterElse = context.chunk.code.size
                context.chunk.code[jumpPos + 1] = afterElse
            }
            is AstNode.While -> {
                val loopStart = context.chunk.code.size
                // 编译条件
                compileNode(node.condition)
                // 条件为假时跳出循环
                val jumpIfFalsePos = context.chunk.code.size
                context.chunk.emit(Opcode.JUMP_IF_FALSE, 0) // 占位
                // 编译循环体
                compileNode(node.body)
                // 循环体结束后跳回条件检查
                context.chunk.emit(Opcode.JUMP, loopStart)
                // 修正跳出偏移量
                val afterLoop = context.chunk.code.size
                context.chunk.code[jumpIfFalsePos + 1] = afterLoop
            }
            is AstNode.For -> {
                // for (init; cond; inc) body
                // 编译初始化子句
                node.initializer?.let { compileNode(it) }
                // 如果初始化子句产生了一个值，弹出丢弃（它不在栈上保留）
                // 注意：compileNode 可能将结果留在栈上，需要 POP 处理
                // 但目前的 CodeBlock 实现未处理 POP，暂不处理

                val loopStart = context.chunk.code.size
                // 编译条件（条件为 null 时视为 true，但编译模式下需生成 LOAD_TRUE）
                if (node.condition != null) {
                    compileNode(node.condition)
                } else {
                    context.chunk.emit(Opcode.LOAD_TRUE)
                }
                // 条件为假时跳出循环
                val jumpIfFalsePos = context.chunk.code.size
                context.chunk.emit(Opcode.JUMP_IF_FALSE, 0) // 占位
                // 编译循环体
                compileNode(node.body)
                // 编译增量子句
                node.increment?.let { compileNode(it) }
                // 循环体结束后跳回条件检查
                context.chunk.emit(Opcode.JUMP, loopStart)
                // 修正跳出偏移量
                val afterLoop = context.chunk.code.size
                context.chunk.code[jumpIfFalsePos + 1] = afterLoop
            }
            is AstNode.FuncDefNode -> {
                // 函数定义：暂时不编译函数体，仅记录函数名（后续实现）
                println("Warning: function definition compilation not implemented yet")
                // 生成一个空操作
                context.chunk.emit(Opcode.LOAD_NULL)
            }
            is AstNode.FuncCall -> {
                // 函数调用：依次编译每个参数，然后调用 CALL 指令
                for (arg in node.args) {
                    compileNode(arg)
                }
                context.chunk.emit(Opcode.CALL, node.args.size)
            }
            is AstNode.Return -> {
                // 返回值：编译返回值表达式，然后发出 RETURN 指令
                if (node.value != null) {
                    compileNode(node.value)
                } else {
                    context.chunk.emit(Opcode.LOAD_NULL)
                }
                context.chunk.emit(Opcode.RETURN)
            }
            is AstNode.ImportNode -> {
                // 导入声明：在编译模式下暂时只输出警告
                println("Warning: import statement is not supported in compiled mode yet")
                context.chunk.emit(Opcode.LOAD_NULL)
            }
            is AstNode.ExternalFuncDecl -> {
                // 外部函数声明：编译模式下暂不支持
                println("Warning: external function declaration is not supported in compiled mode yet")
                context.chunk.emit(Opcode.LOAD_NULL)
            }
            is AstNode.ProxyVar -> {
                // 数组/代理访问：暂不实现
                throw IllegalStateException("ProxyVar compilation not implemented")
            }
            else -> {
                throw IllegalStateException("Unsupported AST node type: ${node::class.simpleName}")
            }
        }
    }
}