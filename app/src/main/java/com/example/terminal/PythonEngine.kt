package com.example.terminal

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

data class ExecutionResult(
    val output: String,
    val isError: Boolean = false,
    val executionTimeMs: Long = 0
)

object PythonEngine {

    // Simulated virtual workspace files
    private val virtualFiles = mutableMapOf(
        "main.py" to """
# Sample Python Script in JARVIS IDE
def fibonacci(n):
    a, b = 0, 1
    result = []
    for _ in range(n):
        result.append(a)
        a, b = b, a + b
    return result

print("Welcome to JARVIS Python Terminal!")
print("First 10 Fibonacci numbers:", fibonacci(10))
""".trimIndent(),
        "app.py" to """
import math

def calculate_stats(numbers):
    total = sum(numbers)
    avg = total / len(numbers)
    variance = sum((x - avg) ** 2 for x in numbers) / len(numbers)
    return {
        "count": len(numbers),
        "sum": total,
        "mean": avg,
        "std_dev": round(math.sqrt(variance), 2)
    }

data = [12, 45, 67, 89, 23, 56, 78, 90]
print("Stats for data:", calculate_stats(data))
""".trimIndent(),
        "requirements.txt" to """
numpy==1.26.4
pandas==2.2.1
requests==2.31.0
matplotlib==3.8.3
scikit-learn==1.4.1.post1
""".trimIndent()
    )

    private val installedPackages = mutableSetOf(
        "pip", "setuptools", "wheel", "requests", "numpy"
    )

    /**
     * Executes interactive CLI terminal commands like python, pip, ls, cat, etc.
     */
    fun executeCommand(commandLine: String): ExecutionResult {
        val trimmed = commandLine.trim()
        if (trimmed.isEmpty()) return ExecutionResult("")

        val startTime = System.currentTimeMillis()
        val parts = trimmed.split("\\s+".toRegex())
        val command = parts[0].lowercase()
        val args = if (parts.size > 1) trimmed.substringAfter(parts[0]).trim() else ""

        val result = when (command) {
            "help" -> ExecutionResult(
                """
JARVIS Terminal Commands:
  python [file.py | code]  Execute Python script or inline statement
  pip install <pkg>       Install a Python package
  pip list                List installed packages
  ls, dir                 List virtual project files
  cat <file>              Display contents of a file
  touch <file>            Create a new file
  clear                   Clear terminal screen
  sysinfo                 Show Python & runtime system information
  date                    Show current system timestamp
  echo <text>             Print text to output
  calc <expression>       Calculate mathematical expression
""".trimIndent()
            )

            "ls", "dir" -> {
                val fileList = virtualFiles.keys.joinToString("   ") { filename ->
                    if (filename.endsWith(".py")) "🐍 $filename" else "📄 $filename"
                }
                ExecutionResult("Files in workspace:\n$fileList")
            }

            "cat" -> {
                if (args.isEmpty()) {
                    ExecutionResult("Usage: cat <filename>", isError = true)
                } else if (virtualFiles.containsKey(args)) {
                    ExecutionResult(virtualFiles[args] ?: "")
                } else {
                    ExecutionResult("cat: $args: No such file or directory", isError = true)
                }
            }

            "touch" -> {
                if (args.isEmpty()) {
                    ExecutionResult("Usage: touch <filename>", isError = true)
                } else {
                    virtualFiles[args] = "# Empty file created at ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}\n"
                    ExecutionResult("Created file: $args")
                }
            }

            "pip" -> {
                handlePip(args)
            }

            "python", "python3", "py" -> {
                if (args.isEmpty()) {
                    ExecutionResult("Python 3.12.2 (JARVIS Sandbox, Sep 2026)\nType 'help' or enter code to run.")
                } else if (virtualFiles.containsKey(args)) {
                    runPythonCode(virtualFiles[args] ?: "")
                } else {
                    runPythonCode(args)
                }
            }

            "sysinfo" -> ExecutionResult(
                """
==================================================
  JARVIS Terminal - Embedded Python Sandbox
==================================================
  Python Version: Python 3.12.2 (v3.12.2:6abddd9f04)
  Compiler:       Clang 17.0.6 (Android NDK)
  Platform:       Linux Android aarch64
  Architecture:   ARM64-v8a / x86_64 Compatible
  Memory:         Allocated: 512MB / Free: 384MB
  Packages:       ${installedPackages.size} installed
  Active Mode:    Interactive AI Assisted Execution
==================================================
""".trimIndent()
            )

            "date" -> ExecutionResult(SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.US).format(Date()))

            "echo" -> ExecutionResult(args)

            "calc" -> {
                try {
                    val evaluated = evaluateSimpleExpression(args, mutableMapOf())
                    ExecutionResult("= $evaluated")
                } catch (e: Exception) {
                    ExecutionResult("calc error: ${e.message}", isError = true)
                }
            }

            else -> {
                // If it looks like python code directly (e.g. print(...), x = 5, for ...), run it directly!
                if (trimmed.startsWith("print(") || trimmed.contains("=") || trimmed.startsWith("for ") || trimmed.startsWith("def ")) {
                    runPythonCode(trimmed)
                } else {
                    ExecutionResult("zsh: command not found: $command. Type 'help' for available commands.", isError = true)
                }
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        return result.copy(executionTimeMs = elapsed)
    }

    private fun handlePip(args: String): ExecutionResult {
        val parts = args.split("\\s+".toRegex())
        val subCommand = parts.firstOrNull()?.lowercase() ?: ""
        return when (subCommand) {
            "list" -> {
                val list = installedPackages.sorted().joinToString("\n") { "  $it (latest)" }
                ExecutionResult("Package          Version\n---------------- -------\n$list")
            }
            "install" -> {
                val pkg = parts.drop(1).firstOrNull()
                if (pkg.isNullOrBlank()) {
                    ExecutionResult("ERROR: You must give at least one requirement to install.", isError = true)
                } else {
                    installedPackages.add(pkg.lowercase())
                    ExecutionResult(
                        """
Collecting $pkg
  Downloading $pkg-2.4.0-py3-none-any.whl (1.2 MB)
     ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ 1.2/1.2 MB 14.8 MB/s eta 0:00:00
Installing collected packages: $pkg
Successfully installed $pkg-2.4.0
""".trimIndent()
                    )
                }
            }
            else -> ExecutionResult("Usage: pip install <package> | pip list", isError = true)
        }
    }

    /**
     * Executes Python code strings and captures stdout, variable states, and prints.
     */
    fun runPythonCode(code: String): ExecutionResult {
        val startTime = System.currentTimeMillis()
        val stdout = mutableListOf<String>()
        val variables = mutableMapOf<String, Any>()

        // Preload default environment
        variables["pi"] = Math.PI
        variables["e"] = Math.E

        try {
            val lines = code.lines()
            var i = 0
            while (i < lines.size) {
                val rawLine = lines[i]
                val trimmed = rawLine.trim()

                // Skip comments and empty lines
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    i++
                    continue
                }

                // Handle for loops
                if (trimmed.startsWith("for ") && trimmed.endsWith(":")) {
                    val loopHeader = trimmed.removePrefix("for ").removeSuffix(":")
                    // e.g. i in range(5) or x in [1, 2, 3]
                    val forParts = loopHeader.split(" in ")
                    if (forParts.size == 2) {
                        val loopVar = forParts[0].trim()
                        val iterableExpr = forParts[1].trim()

                        // Collect loop body (indented lines)
                        val bodyLines = mutableListOf<String>()
                        var j = i + 1
                        while (j < lines.size && (lines[j].startsWith("    ") || lines[j].startsWith("\t") || lines[j].trim().isEmpty())) {
                            if (lines[j].trim().isNotEmpty()) {
                                bodyLines.add(lines[j].trim())
                            }
                            j++
                        }

                        val rangeItems = parseIterable(iterableExpr, variables)
                        for (item in rangeItems) {
                            variables[loopVar] = item
                            for (bLine in bodyLines) {
                                executeLine(bLine, variables, stdout)
                            }
                        }
                        i = j
                        continue
                    }
                }

                // Handle single lines
                executeLine(trimmed, variables, stdout)
                i++
            }

            val elapsed = System.currentTimeMillis() - startTime
            val outputText = if (stdout.isEmpty()) {
                if (variables.isNotEmpty()) {
                    val interestingVars = variables.filterKeys { it != "pi" && it != "e" }
                    if (interestingVars.isNotEmpty()) {
                        "Script finished successfully.\nVariables: " + interestingVars.entries.joinToString(", ") { "${it.key} = ${it.value}" }
                    } else {
                        "Script finished successfully with returncode 0."
                    }
                } else {
                    "Script finished with returncode 0."
                }
            } else {
                stdout.joinToString("\n")
            }

            return ExecutionResult(
                output = outputText,
                isError = false,
                executionTimeMs = elapsed
            )
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            return ExecutionResult(
                output = "Traceback (most recent call last):\n  File \"<stdin>\", line 1\n${e.javaClass.simpleName}: ${e.localizedMessage ?: e.message}",
                isError = true,
                executionTimeMs = elapsed
            )
        }
    }

    private fun executeLine(line: String, variables: MutableMap<String, Any>, stdout: MutableList<String>) {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return

        // Print statement
        if (trimmed.startsWith("print(") && trimmed.endsWith(")")) {
            val inside = trimmed.substring(6, trimmed.length - 1).trim()
            val evaluated = evaluatePrintArgs(inside, variables)
            stdout.add(evaluated)
            return
        }

        // Variable assignment: x = 10 or text = "hello"
        if (trimmed.contains("=") && !trimmed.contains("==") && !trimmed.contains("!=") && !trimmed.contains("<=") && !trimmed.contains(">=")) {
            val parts = trimmed.split("=", limit = 2)
            val varName = parts[0].trim()
            val expr = parts[1].trim()
            val value = evaluateSimpleExpression(expr, variables)
            variables[varName] = value
            return
        }

        // Method invocation like list.append(...)
        if (trimmed.contains(".append(")) {
            val listName = trimmed.substringBefore(".append(").trim()
            val arg = trimmed.substringAfter(".append(").removeSuffix(")").trim()
            val list = variables[listName] as? MutableList<Any> ?: mutableListOf()
            val evalArg = evaluateSimpleExpression(arg, variables)
            list.add(evalArg)
            variables[listName] = list
            return
        }
    }

    private fun evaluatePrintArgs(argsStr: String, variables: Map<String, Any>): String {
        if (argsStr.isEmpty()) return ""
        val args = splitArguments(argsStr)
        return args.joinToString(" ") { arg ->
            evaluateSimpleExpression(arg.trim(), variables).toString()
        }
    }

    private fun splitArguments(argsStr: String): List<String> {
        val tokens = mutableListOf<String>()
        var inQuotes = false
        var quoteChar = ' '
        var depth = 0
        val sb = StringBuilder()

        for (ch in argsStr) {
            if ((ch == '"' || ch == '\'') && (quoteChar == ' ' || quoteChar == ch)) {
                inQuotes = !inQuotes
                quoteChar = if (inQuotes) ch else ' '
                sb.append(ch)
            } else if (inQuotes) {
                sb.append(ch)
            } else if (ch == '(' || ch == '[' || ch == '{') {
                depth++
                sb.append(ch)
            } else if (ch == ')' || ch == ']' || ch == '}') {
                depth--
                sb.append(ch)
            } else if (ch == ',' && depth == 0) {
                tokens.add(sb.toString().trim())
                sb.clear()
            } else {
                sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) {
            tokens.add(sb.toString().trim())
        }
        return tokens
    }

    private fun evaluateSimpleExpression(expr: String, variables: Map<String, Any>): Any {
        val t = expr.trim()

        // String literals
        if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'"))) {
            return t.substring(1, t.length - 1)
        }

        // F-strings: f"Hello {name}"
        if (t.startsWith("f\"") || t.startsWith("f'")) {
            val raw = t.substring(2, t.length - 1)
            var formatted = raw
            val regex = "\\{([^}]+)\\}".toRegex()
            regex.findAll(raw).forEach { match ->
                val varExpr = match.groupValues[1]
                val value = evaluateSimpleExpression(varExpr, variables)
                formatted = formatted.replace(match.value, value.toString())
            }
            return formatted
        }

        // Boolean & None
        if (t.equals("True", ignoreCase = true)) return true
        if (t.equals("False", ignoreCase = true)) return false
        if (t.equals("None", ignoreCase = true)) return "None"

        // Number literals
        t.toLongOrNull()?.let { return it }
        t.toDoubleOrNull()?.let { return it }

        // Variables
        if (variables.containsKey(t)) {
            return variables[t] ?: "None"
        }

        // List literal: [1, 2, 3]
        if (t.startsWith("[") && t.endsWith("]")) {
            val inside = t.substring(1, t.length - 1).trim()
            if (inside.isEmpty()) return mutableListOf<Any>()
            return splitArguments(inside).map { evaluateSimpleExpression(it, variables) }.toMutableList()
        }

        // Functions: len(x), sum(x), max(x), min(x), round(x)
        if (t.startsWith("len(") && t.endsWith(")")) {
            val arg = evaluateSimpleExpression(t.substring(4, t.length - 1), variables)
            return when (arg) {
                is List<*> -> arg.size
                is String -> arg.length
                else -> 0
            }
        }
        if (t.startsWith("sum(") && t.endsWith(")")) {
            val arg = evaluateSimpleExpression(t.substring(4, t.length - 1), variables)
            if (arg is List<*>) {
                return arg.mapNotNull { (it as? Number)?.toDouble() }.sum()
            }
        }
        if (t.startsWith("round(") && t.endsWith(")")) {
            val parts = splitArguments(t.substring(6, t.length - 1))
            val num = (evaluateSimpleExpression(parts[0], variables) as? Number)?.toDouble() ?: 0.0
            return String.format(Locale.US, "%.2f", num).toDoubleOrNull() ?: num
        }

        // Simple arithmetic: a + b, a - b, a * b, a / b
        val op = findOperator(t)
        if (op != null) {
            val left = evaluateSimpleExpression(t.substring(0, op.index), variables)
            val right = evaluateSimpleExpression(t.substring(op.index + op.symbol.length), variables)
            val numLeft = (left as? Number)?.toDouble()
            val numRight = (right as? Number)?.toDouble()

            if (numLeft != null && numRight != null) {
                return when (op.symbol) {
                    "+" -> if (left is Long && right is Long) left + right else numLeft + numRight
                    "-" -> if (left is Long && right is Long) left - right else numLeft - numRight
                    "*" -> if (left is Long && right is Long) left * right else numLeft * numRight
                    "/" -> if (numRight != 0.0) numLeft / numRight else "ZeroDivisionError"
                    "%" -> numLeft % numRight
                    "**" -> Math.pow(numLeft, numRight)
                    else -> 0
                }
            } else if (op.symbol == "+") {
                return "$left$right"
            }
        }

        return t
    }

    private data class OperatorMatch(val symbol: String, val index: Int)

    private fun findOperator(s: String): OperatorMatch? {
        val ops = listOf("+", "-", "*", "/", "%")
        for (op in ops) {
            var inQuotes = false
            for (idx in s.indices) {
                val c = s[idx]
                if (c == '"' || c == '\'') inQuotes = !inQuotes
                if (!inQuotes && c.toString() == op && idx > 0 && idx < s.length - 1) {
                    return OperatorMatch(op, idx)
                }
            }
        }
        return null
    }

    private fun parseIterable(expr: String, variables: Map<String, Any>): List<Any> {
        val t = expr.trim()
        if (t.startsWith("range(") && t.endsWith(")")) {
            val inside = t.substring(6, t.length - 1)
            val parts = splitArguments(inside)
            val start = if (parts.size > 1) (evaluateSimpleExpression(parts[0], variables) as? Number)?.toInt() ?: 0 else 0
            val end = (evaluateSimpleExpression(parts.last(), variables) as? Number)?.toInt() ?: 10
            return (start until end).map { it }
        }
        val evaluated = evaluateSimpleExpression(t, variables)
        if (evaluated is List<*>) {
            return evaluated.filterNotNull()
        }
        return emptyList()
    }
}
