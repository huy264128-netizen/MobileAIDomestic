/**
 * MaidLang 虚拟机 (VM)
 *
 * 执行由编译器生成的字节码。
 * 采用基于栈的执行模型，支持常量加载、变量存取、算术运算、控制流和函数调用。
 */

import kotlin.collections.ArrayDeque
import MaidValue
import Opcode
import BytecodeChunk
import OpType
import AstNode
import Scope

/**
 * 调用帧，表示一个函数的执行上下文。
 */
private class Frame(
    val chunk: BytecodeChunk,           // 所属的字节码块
    val locals: Array<MaidValue?>,      // 局部变量槽（初始为 null）
    var ip: Int = 0,                    // 指令指针
    var stackStart: Int = 0             // 该帧在操作数栈中的起始位置（用于计算相对偏移）
) {
    /** 返回当前指令（不推进 ip）。 */
    fun peekOpcode(): Opcode {
        if (ip < 0 || ip >= chunk.code.size) {
            throw IllegalStateException("Instruction pointer out of bounds: ip=$ip, code size=${chunk.code.size}")
        }
        return Opcode.fromValue(chunk.code[ip])
    }
    
    /** 读取一个字节操作数并推进 ip。 */
    fun readByte(): Int = chunk.code[ip++]
    
    /** 读取一个整数操作数（实际上就是 readByte，因为操作数以 Int 存储）。 */
    fun readInt(): Int = readByte()
}

/**
 * MaidLang 虚拟机。
 */
class VM {
    /** 全局变量表（名称 -> 值）。 */
    private val globals = mutableMapOf<String, MaidValue>()
    
    /** 操作数栈。 */
    private val stack = ArrayDeque<MaidValue>()
    
    /** 调用栈。 */
    private val callStack = ArrayDeque<Frame>()
    
    /** 当前帧（callStack 顶部）。 */
    private var currentFrame: Frame? = null
    
    /** 是否正在运行。 */
    private var running = false
    
    /** 定义全局变量。 */
    fun defineGlobal(name: String, value: MaidValue) {
        globals[name] = value
    }
    
    /**
     * 执行一个字节码块（顶级代码或函数）。
     */
    fun interpret(chunk: BytecodeChunk): MaidValue {
        // 创建初始帧（无参数，无局部变量）
        val frame = Frame(chunk, arrayOf())
        callStack.clear()
        callStack.addLast(frame)
        currentFrame = frame
        running = true
        
        // 主执行循环
        try {
            while (running && callStack.isNotEmpty()) {
                val frame = callStack.last()
                currentFrame = frame
                executeInstruction(frame)
            }
        } catch (e: Exception) {
            println("VM runtime error: ${e.message}")
            e.printStackTrace()
            return MaidValue.NullVal
        }
        
        // 执行结束时栈顶应保存返回值
        return if (stack.isNotEmpty()) stack.last() else MaidValue.NullVal
    }
    
    /**
     * 执行当前帧的一条指令。
     */
    private fun executeInstruction(frame: Frame) {
        val opcode = frame.peekOpcode()
        when (opcode) {
            // 常量加载
            Opcode.LOAD_INT -> {
                val constIndex = frame.readInt()
                val value = frame.chunk.constants.getConstant(constIndex)
                stack.addLast(value)
            }
            Opcode.LOAD_FLOAT, Opcode.LOAD_STRING, Opcode.LOAD_CHAR -> {
                // 与 LOAD_INT 类似，只是常量类型不同
                val constIndex = frame.readInt()
                val value = frame.chunk.constants.getConstant(constIndex)
                stack.addLast(value)
            }
            Opcode.LOAD_NULL -> {
                stack.addLast(MaidValue.NullVal)
            }
            Opcode.LOAD_TRUE -> {
                stack.addLast(MaidValue.IntVal(1))
            }
            Opcode.LOAD_FALSE -> {
                stack.addLast(MaidValue.IntVal(0))
            }
            
            // 变量存取（简化版：仅支持全局变量，通过名称查找）
            Opcode.LOAD_VAR -> {
                val varIndex = frame.readInt()
                // TODO: 目前假设变量索引对应全局变量名称，临时实现为通过索引映射
                // 这里简化：直接使用索引作为变量名（仅用于演示）
                val name = "var$varIndex"
                val value = globals[name] ?: MaidValue.NullVal
                stack.addLast(value)
            }
            Opcode.STORE_VAR -> {
                val varIndex = frame.readInt()
                val value = stack.removeLast()
                val name = "var$varIndex"
                globals[name] = value
            }
            
            // 二元运算
            Opcode.ADD -> {
                val b = stack.removeLast()
                val a = stack.removeLast()
                val result = applyBinaryOp(OpType.ADD, a, b)
                stack.addLast(result)
            }
            Opcode.SUB -> {
                val b = stack.removeLast()
                val a = stack.removeLast()
                val result = applyBinaryOp(OpType.SUB, a, b)
                stack.addLast(result)
            }
            Opcode.MUL -> {
                val b = stack.removeLast()
                val a = stack.removeLast()
                val result = applyBinaryOp(OpType.MUL, a, b)
                stack.addLast(result)
            }
            Opcode.DIV -> {
                val b = stack.removeLast()
                val a = stack.removeLast()
                val result = applyBinaryOp(OpType.DIV, a, b)
                stack.addLast(result)
            }
            Opcode.MOD -> {
                val b = stack.removeLast()
                val a = stack.removeLast()
                val result = applyBinaryOp(OpType.MOD, a, b)
                stack.addLast(result)
            }
            Opcode.GREATER -> {
                val b = stack.removeLast()
                val a = stack.removeLast()
                val result = applyBinaryOp(OpType.GREATER, a, b)
                stack.addLast(result)
            }
            Opcode.LESS -> {
                val b = stack.removeLast()
                val a = stack.removeLast()
                val result = applyBinaryOp(OpType.LESS, a, b)
                stack.addLast(result)
            }
            Opcode.GREATER_EQUAL -> {
                val b = stack.removeLast()
                val a = stack.removeLast()
                val result = applyBinaryOp(OpType.GREATER_EQUAL, a, b)
                stack.addLast(result)
            }
            Opcode.LESS_EQUAL -> {
                val b = stack.removeLast()
                val a = stack.removeLast()
                val result = applyBinaryOp(OpType.LESS_EQUAL, a, b)
                stack.addLast(result)
            }
            Opcode.EQUAL -> {
                val b = stack.removeLast()
                val a = stack.removeLast()
                val result = applyBinaryOp(OpType.EQUAL, a, b)
                stack.addLast(result)
            }
            Opcode.NOT_EQUAL -> {
                val b = stack.removeLast()
                val a = stack.removeLast()
                val result = applyBinaryOp(OpType.NOT_EQUAL, a, b)
                stack.addLast(result)
            }
            Opcode.LOGICAL_AND -> {
                val b = stack.removeLast()
                val a = stack.removeLast()
                val result = applyBinaryOp(OpType.LOGICAL_AND, a, b)
                stack.addLast(result)
            }
            Opcode.LOGICAL_OR -> {
                val b = stack.removeLast()
                val a = stack.removeLast()
                val result = applyBinaryOp(OpType.LOGICAL_OR, a, b)
                stack.addLast(result)
            }
            Opcode.BIT_AND -> {
                val b = stack.removeLast()
                val a = stack.removeLast()
                val result = applyBinaryOp(OpType.BIT_AND, a, b)
                stack.addLast(result)
            }
            Opcode.BIT_OR -> {
                val b = stack.removeLast()
                val a = stack.removeLast()
                val result = applyBinaryOp(OpType.BIT_OR, a, b)
                stack.addLast(result)
            }
            Opcode.BIT_XOR -> {
                val b = stack.removeLast()
                val a = stack.removeLast()
                val result = applyBinaryOp(OpType.BIT_XOR, a, b)
                stack.addLast(result)
            }
            
            // 一元运算
            Opcode.NEGATE -> {
                val value = stack.removeLast()
                val result = applyUnaryOp(OpType.SUB, value, null, null) // 使用 SUB 作为负号（需特殊处理）
                stack.addLast(result)
            }
            Opcode.NOT -> {
                val value = stack.removeLast()
                val result = applyUnaryOp(OpType.NOT, value, null, null)
                stack.addLast(result)
            }
            
            // 控制流
            Opcode.JUMP -> {
                val offset = frame.readInt()
                frame.ip = offset
            }
            Opcode.JUMP_IF_TRUE -> {
                val offset = frame.readInt()
                val condition = stack.removeLast()
                if (condition.asInt() != 0) {
                    frame.ip = offset
                }
            }
            Opcode.JUMP_IF_FALSE -> {
                val offset = frame.readInt()
                val condition = stack.removeLast()
                if (condition.asInt() == 0) {
                    frame.ip = offset
                }
            }
            
            // 函数调用（简化版：暂不支持）
            Opcode.CALL -> {
                val argCount = frame.readInt()
                // TODO: 实现函数调用
                println("Warning: CALL instruction not implemented")
                // 临时：弹出参数，压入 null
                repeat(argCount) { stack.removeLast() }
                stack.addLast(MaidValue.NullVal)
            }
            Opcode.RETURN -> {
                val returnValue = if (stack.isNotEmpty()) stack.removeLast() else MaidValue.NullVal
                // 返回到调用者（弹出当前帧）
                callStack.removeLast()
                if (callStack.isNotEmpty()) {
                    // 将返回值压入调用者的栈
                    this@VM.stack.addLast(returnValue)
                } else {
                    // 顶级返回，停止执行
                    running = false
                    stack.addLast(returnValue)
                }
            }
            
            // 其他指令暂不实现
            else -> {
                throw IllegalStateException("Unimplemented opcode: $opcode")
            }
        }
        // 默认情况下，指令执行完毕后 ip 已由 readInt 推进；但对于未读取操作数的指令，需要手动推进
        // 我们的 when 分支中，所有指令都通过 readInt 或显式设置 ip 来推进，因此无需额外操作。
    }
    
    /**
     * 应用二元运算（复用 interpreter.kt 中的逻辑）。
     * 由于 interpreter.kt 中的 applyBinaryOp 是私有函数，这里临时复制一份简化实现。
     */
    private fun applyBinaryOp(op: OpType, left: MaidValue, right: MaidValue): MaidValue {
        // 简化实现：直接调用 interpreter.kt 中的函数？因为无法直接访问，我们复制逻辑。
        // 这里先实现一个简单版本，仅支持整数运算。
        return when (op) {
            OpType.ADD -> MaidValue.IntVal(left.asInt() + right.asInt())
            OpType.SUB -> MaidValue.IntVal(left.asInt() - right.asInt())
            OpType.MUL -> MaidValue.IntVal(left.asInt() * right.asInt())
            OpType.DIV -> MaidValue.IntVal(left.asInt() / right.asInt())
            OpType.MOD -> MaidValue.IntVal(left.asInt() % right.asInt())
            OpType.GREATER -> MaidValue.IntVal(if (left.asInt() > right.asInt()) 1 else 0)
            OpType.LESS -> MaidValue.IntVal(if (left.asInt() < right.asInt()) 1 else 0)
            OpType.GREATER_EQUAL -> MaidValue.IntVal(if (left.asInt() >= right.asInt()) 1 else 0)
            OpType.LESS_EQUAL -> MaidValue.IntVal(if (left.asInt() <= right.asInt()) 1 else 0)
            OpType.EQUAL -> MaidValue.IntVal(if (left.asInt() == right.asInt()) 1 else 0)
            OpType.NOT_EQUAL -> MaidValue.IntVal(if (left.asInt() != right.asInt()) 1 else 0)
            OpType.LOGICAL_AND -> MaidValue.IntVal(if (left.asInt() != 0 && right.asInt() != 0) 1 else 0)
            OpType.LOGICAL_OR -> MaidValue.IntVal(if (left.asInt() != 0 || right.asInt() != 0) 1 else 0)
            OpType.BIT_AND -> MaidValue.IntVal(left.asInt() and right.asInt())
            OpType.BIT_OR -> MaidValue.IntVal(left.asInt() or right.asInt())
            OpType.BIT_XOR -> MaidValue.IntVal(left.asInt() xor right.asInt())
            else -> MaidValue.NullVal
        }
    }
    
    /**
     * 应用一元运算。
     */
    private fun applyUnaryOp(op: OpType, value: MaidValue, argNode: AstNode?, scope: Scope?): MaidValue {
        return when (op) {
            OpType.NOT -> MaidValue.IntVal(if (value.asInt() == 0) 1 else 0)
            OpType.SUB -> MaidValue.IntVal(-value.asInt())  // 负号
            else -> MaidValue.NullVal
        }
    }
}