/**
 * MaidLang 字节码定义
 *
 * 本文件定义了编译器生成的字节码格式、操作码枚举、常量池以及字节码块结构。
 */

/**
 * 字节码操作码枚举。
 * 每条指令由操作码和可选的操作数组成，操作数以整数形式紧跟在操作码之后。
 */
enum class Opcode(val value: Int) {
    // 常量加载
    LOAD_INT(0),
    LOAD_FLOAT(1),
    LOAD_STRING(2),
    LOAD_CHAR(3),
    LOAD_NULL(4),
    LOAD_TRUE(5),
    LOAD_FALSE(6),
    
    // 变量存取
    LOAD_VAR(10),
    STORE_VAR(11),
    
    // 二元运算
    ADD(20),
    SUB(21),
    MUL(22),
    DIV(23),
    MOD(24),
    GREATER(25),
    LESS(26),
    GREATER_EQUAL(27),
    LESS_EQUAL(28),
    EQUAL(29),
    NOT_EQUAL(30),
    LOGICAL_AND(31),
    LOGICAL_OR(32),
    BIT_AND(33),
    BIT_OR(34),
    BIT_XOR(35),
    
    // 一元运算
    NEGATE(40),
    NOT(41),
    
    // 控制流
    JUMP(50),
    JUMP_IF_TRUE(51),
    JUMP_IF_FALSE(52),
    
    // 函数调用
    CALL(60),
    RETURN(61),
    
    // 作用域管理（暂未使用）
    ENTER_SCOPE(70),
    EXIT_SCOPE(71),
    
    // 数组/代理（预留）
    LOAD_INDEX(80),
    STORE_INDEX(81);
    
    companion object {
        private val valueMap = values().associateBy { it.value }
        fun fromValue(value: Int): Opcode = valueMap[value] ?: throw IllegalArgumentException("Unknown opcode value: $value")
    }
}

/**
 * 常量池，存储编译期间收集的所有字面量常量。
 * 常量以 MaidValue 形式存储，通过索引引用。
 */
class ConstantPool {
    private val constants = mutableListOf<MaidValue>()
    
    /** 添加一个常量并返回其索引（如果已存在相同值则返回现有索引）。 */
    fun addConstant(value: MaidValue): Int {
        val index = constants.indexOfFirst { it == value }
        if (index != -1) return index
        constants.add(value)
        return constants.size - 1
    }
    
    /** 根据索引获取常量。 */
    fun getConstant(index: Int): MaidValue = constants[index]
    
    /** 返回常量数量。 */
    fun size(): Int = constants.size
    
    /** 获取所有常量（用于调试）。 */
    fun getAll(): List<MaidValue> = constants.toList()
}

/**
 * 字节码块，表示一个函数或顶级代码的编译结果。
 * 包含指令序列和关联的常量池。
 */
class BytecodeChunk(val name: String = "main") {
    val constants = ConstantPool()
    val code = mutableListOf<Int>()
    
    /** 添加一条无操作数的指令。 */
    fun emit(op: Opcode) {
        code.add(op.value)
    }
    
    /** 添加一条带一个整数操作数的指令。 */
    fun emit(op: Opcode, operand: Int) {
        code.add(op.value)
        code.add(operand)
    }
    
    /** 添加一条带两个整数操作数的指令。 */
    fun emit(op: Opcode, operand1: Int, operand2: Int) {
        code.add(op.value)
        code.add(operand1)
        code.add(operand2)
    }
    
    /** 返回指令数量。 */
    fun instructionCount(): Int = code.size
    
    /** 返回字节码的字符串表示（反汇编）。 */
    fun disassemble(): String {
        val out = StringBuilder()
        var i = 0
        while (i < code.size) {
            val opcode = Opcode.fromValue(code[i])
            out.append("%04d  %-20s".format(i, opcode.name))
            i++
            when (opcode) {
                Opcode.LOAD_INT, Opcode.LOAD_FLOAT, Opcode.LOAD_STRING, Opcode.LOAD_CHAR,
                Opcode.LOAD_VAR, Opcode.STORE_VAR,
                Opcode.JUMP, Opcode.JUMP_IF_TRUE, Opcode.JUMP_IF_FALSE,
                Opcode.CALL -> {
                    val operand = code[i]
                    out.append(" $operand")
                    if (opcode in listOf(Opcode.LOAD_INT, Opcode.LOAD_FLOAT, Opcode.LOAD_STRING, Opcode.LOAD_CHAR)) {
                        val const = constants.getConstant(operand)
                        out.append("  ; $const")
                    }
                    i++
                }
                else -> {}
            }
            out.append("\n")
        }
        return out.toString()
    }
}