# MaidLang 语言规范

MaidLang 是一个静态类型的脚本语言，语法类似C，运行在Kotlin虚拟机上。它包含词法分析器、解析器和解释器，支持变量、函数、控制流等基本编程结构。

## 1. 类型系统

MaidLang 是静态类型语言，所有变量和函数必须在编译时确定类型。

### 基本类型

| 类型关键字 | 描述           | 示例值        |
|------------|----------------|---------------|
| `int`      | 32位整数       | `42`, `-7`    |
| `float`    | 单精度浮点数   | `3.14`, `-2.5`|
| `char`     | 单个字符       | `'a'`, `'\n'` |
| `string`   | 字符串         | `"hello"`     |
| `bool`     | 布尔值         | `true`, `false`|
| `void`     | 无类型，用于函数返回值 | `void` |

### 类型推导

变量声明时可以省略类型，编译器会根据初始化表达式推断类型：
```c
var x = 5;          // 推断为 int
var y = 3.14;       // 推断为 float
var z = "hello";    // 推断为 string
```

### 函数类型

函数类型表示为参数类型列表和返回类型，例如 `(int, int) -> int`。

## 2. 语法

### 2.1 变量声明

```c
int age = 25;
float pi = 3.14159;
char initial = 'A';
string name = "Alice";
bool flag = true;

// 类型推导
var inferredInt = 42;
var inferredFloat = 2.718;
```

### 2.2 函数声明

```c
// 无参数无返回值
void sayHello() {
    print("Hello!");
}

// 带参数和返回值
int add(int a, int b) {
    return a + b;
}

// 类型推导返回值（暂不支持）
```

### 2.3 控制流

```c
// if 语句
if (x > 0) {
    print("positive");
} else {
    print("non-positive");
}

// while 循环
int i = 0;
while (i < 10) {
    print(i);
    i = i + 1;
}

// for 循环（C 风格）
for (int i = 0; i < 10; i = i + 1) {
    print(i);
}

// for 循环 - 使用 var 推导类型
for (var i = 0; i < 5; i = i + 1) {
    print(i * i);
}

// for 循环 - 省略初始化子句
int j = 0;
for (; j < 3; j = j + 1) {
    print(j);
}

// for 循环 - 无限循环（使用 break 退出）
for (;;) {
    // 无限循环
}
```

### 2.4 表达式

支持常见的算术、逻辑、比较和位运算：

```c
int a = 5 + 3 * 2;      // 算术
bool b = a > 10 && a < 20; // 逻辑
int c = a & 0xFF;       // 位运算
```

## 3. 内置函数

### 3.1 打印函数

```c
print("Hello");      // 输出不换行
println("World");    // 输出并换行
```

### 3.2 导入Kotlin/Java函数

可以使用 `import` 语句导入任意 Kotlin/Java 类的静态方法：

```c
// 导入具体方法，指定别名
import "java.lang.Math.abs" as abs;
import "java.lang.Math.sin" as sin;
import "java.lang.Math.sqrt" as sqrt;

int x = abs(-5);       // 输出 5
float y = sin(1.57);   // 输出 ~1.0
float z = sqrt(9.0);   // 输出 3.0

// 也支持 Kotlin 标准库
import "kotlin.math.roundToInt" as round;
int r = round(3.7);
```

导入路径格式为 `"包名.类名.方法名"`，`as` 指定在 MaidLang 中使用的别名。

### 3.3 外部函数声明（免反射）

可以使用 `external fun` 声明一个由 Kotlin 宿主代码注册的外部函数，无需反射即可调用：

```c
// MaidLang 中声明外部函数签名
external fun abs(int) -> int;
external fun sin(float) -> float;
external fun sqrt(float) -> float;

int x = abs(-5);     // 输出 5
float y = sin(1.57); // 输出 ~1.0
```

在 Kotlin 宿主代码中注册实现：

```kotlin
val interpreter = Interpreter()
interpreter.registerNative("abs") { args ->
    MaidValue.IntVal(kotlin.math.abs(args[0].asInt()))
}
interpreter.registerNative("sin") { args ->
    MaidValue.FloatVal(kotlin.math.sin(args[0].asFloat().toDouble()).toFloat())
}
```

语法格式：`external fun 函数名(参数类型1, 参数类型2, ...) -> 返回类型;`

省略 `-> 返回类型` 时默认为 `void`。

### 3.4 运算符

支持双字符比较运算符：

支持双字符比较运算符：

| 运算符 | 含义 |
|--------|------|
| `<=`   | 小于等于 |
| `>=`   | 大于等于 |
| `==`   | 等于 |
| `!=`   | 不等于 |

## 4. 示例程序

```c
// 计算斐波那契数列
int fib(int n) {
    if (n <= 1) {
        return n;
    }
    return fib(n - 1) + fib(n - 2);
}

void main() {
    int result = fib(10);
    print("fib(10) = " + result);
}
```

## 5. 实现细节

MaidLang 由以下组件构成：

- **lexer.kt**: 词法分析器，将源代码转换为令牌流。
- **parser.kt**: 递归下降解析器，构建抽象语法树（AST）。
- **interpreter.kt**: 解释器，执行AST并管理作用域。
- **tester.kt**: REPL交互环境，用于测试代码。

## 6. 未来扩展

- 数组和集合类型
- 结构体/类定义
- 模块系统
- 泛型
