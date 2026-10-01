package com.example.coding

import com.example.terminal.ExecutionResult
import com.example.terminal.PythonEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class SupportedLanguage(
    val id: String,
    val name: String,
    val version: String,
    val extension: String,
    val iconEmoji: String,
    val sampleCode: String
)

@Serializable
data class PistonFile(
    val name: String,
    val content: String
)

@Serializable
data class PistonRequest(
    val language: String,
    val version: String = "*",
    val files: List<PistonFile>,
    val stdin: String = ""
)

@Serializable
data class PistonStage(
    val stdout: String = "",
    val stderr: String = "",
    val output: String = "",
    val code: Int = 0
)

@Serializable
data class PistonResponse(
    val language: String = "",
    val version: String = "",
    val run: PistonStage? = null,
    val compile: PistonStage? = null,
    val message: String? = null
)

object CodeExecutionService {

    val supportedLanguages = listOf(
        SupportedLanguage(
            id = "python",
            name = "Python 3",
            version = "3.10.0",
            extension = "py",
            iconEmoji = "🐍",
            sampleCode = """
# Real Python 3 Engine
import math

print("=== Python Execution Demo ===")
numbers = [4, 9, 16, 25, 36]
roots = [math.isqrt(n) for n in numbers]
print("Numbers:", numbers)
print("Square roots:", roots)
print("Sum of roots:", sum(roots))
""".trimIndent()
        ),
        SupportedLanguage(
            id = "javascript",
            name = "JavaScript (Node)",
            version = "18.15.0",
            extension = "js",
            iconEmoji = "⚡",
            sampleCode = """
// Real Node.js Engine
console.log("=== JavaScript Node.js Demo ===");
const fruits = ["Mango", "Apple", "Banana", "Orange"];
const mapped = fruits.map((f, i) => (i + 1) + ". " + f);
console.log(mapped.join("\n"));
""".trimIndent()
        ),
        SupportedLanguage(
            id = "kotlin",
            name = "Kotlin",
            version = "1.8.20",
            extension = "kt",
            iconEmoji = "🎯",
            sampleCode = """
// Real Kotlin Engine
fun main() {
    println("=== Kotlin Cloud Execution ===")
    val greeting = "Hello from real Kotlin JVM!"
    println(greeting)
    val numbers = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    println("Even numbers: " + numbers.filter { it % 2 == 0 })
}
""".trimIndent()
        ),
        SupportedLanguage(
            id = "java",
            name = "Java",
            version = "15.0.2",
            extension = "java",
            iconEmoji = "☕",
            sampleCode = """
// Real Java Engine
public class Main {
    public static void main(String[] args) {
        System.out.println("=== Java 15 Execution ===");
        int a = 15, b = 25;
        System.out.println("Result: " + a + " + " + b + " = " + (a + b));
    }
}
""".trimIndent()
        ),
        SupportedLanguage(
            id = "c++",
            name = "C++ (GCC)",
            version = "10.2.0",
            extension = "cpp",
            iconEmoji = "⚙️",
            sampleCode = """
// Real C++ GCC Engine
#include <iostream>
#include <vector>
#include <numeric>

int main() {
    std::cout << "=== C++ Online Runner ===" << std::endl;
    std::vector<int> v = {10, 20, 30, 40};
    int total = std::accumulate(v.begin(), v.end(), 0);
    std::cout << "Vector Sum: " << total << std::endl;
    return 0;
}
""".trimIndent()
        ),
        SupportedLanguage(
            id = "bash",
            name = "Bash Shell",
            version = "5.2.0",
            extension = "sh",
            iconEmoji = "🐚",
            sampleCode = """
# Real Linux Bash Shell
echo "=== Bash Linux Sandbox ==="
uname -a
pwd
date
""".trimIndent()
        )
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Executes real source code using the Piston Cloud Execution API.
     * If offline, falls back to internal engine for Python.
     */
    suspend fun executeCode(
        language: String,
        code: String,
        stdin: String = ""
    ): ExecutionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            val fileName = "main." + (supportedLanguages.find { it.id == language }?.extension ?: "txt")
            val requestBody = PistonRequest(
                language = language,
                version = "*",
                files = listOf(PistonFile(name = fileName, content = code)),
                stdin = stdin
            )

            val jsonBody = json.encodeToString(PistonRequest.serializer(), requestBody)
            val request = Request.Builder()
                .url("https://emkc.org/api/v2/piston/execute")
                .header("Content-Type", "application/json")
                .header("User-Agent", "JARVIS-IDE-Android/3.0")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val duration = System.currentTimeMillis() - startTime
            val responseString = response.body?.string() ?: ""

            if (response.isSuccessful && responseString.isNotBlank()) {
                val parsed = json.decodeFromString(PistonResponse.serializer(), responseString)

                // If compilation stage failed (e.g. C++, Java, Kotlin)
                if (parsed.compile != null && parsed.compile.code != 0) {
                    val err = parsed.compile.stderr.ifBlank { parsed.compile.output }
                    return@withContext ExecutionResult(
                        output = "❌ Compilation Error (Code ${parsed.compile.code}):\n\n$err",
                        isError = true,
                        executionTimeMs = duration
                    )
                }

                val runStage = parsed.run
                if (runStage != null) {
                    val hasError = runStage.code != 0 || runStage.stderr.isNotBlank()
                    val outputText = when {
                        runStage.output.isNotBlank() -> runStage.output
                        runStage.stdout.isNotBlank() -> runStage.stdout
                        runStage.stderr.isNotBlank() -> runStage.stderr
                        else -> "Program executed successfully with no output (exit code ${runStage.code})."
                    }

                    return@withContext ExecutionResult(
                        output = outputText.trimEnd(),
                        isError = hasError,
                        executionTimeMs = duration
                    )
                }
            }
        } catch (_: Exception) {
            // Network failure or timeout -> gracefully fallback
        }

        // Offline or network fallback
        if (language == "python") {
            PythonEngine.runPythonCode(code)
        } else {
            val duration = System.currentTimeMillis() - startTime
            ExecutionResult(
                output = "⚡ Server execution requires internet. When connected, real ${language.uppercase()} compiler runs in a Linux container on Piston cloud.",
                isError = false,
                executionTimeMs = duration
            )
        }
    }

    /**
     * Auto-detects language from code block content or markdown tags.
     */
    fun detectLanguage(codeBlock: String): String {
        val lower = codeBlock.lowercase().trim()
        return when {
            lower.startsWith("python") || lower.contains("def ") || lower.contains("import math") || lower.contains("print(") -> "python"
            lower.startsWith("javascript") || lower.startsWith("js") || lower.contains("console.log") || lower.contains("const ") -> "javascript"
            lower.startsWith("kotlin") || lower.contains("fun main(") || lower.contains("val ") -> "kotlin"
            lower.startsWith("java") || lower.contains("public class") || lower.contains("system.out.println") -> "java"
            lower.startsWith("cpp") || lower.startsWith("c++") || lower.contains("#include <iostream>") -> "c++"
            lower.startsWith("sh") || lower.startsWith("bash") || lower.contains("echo ") -> "bash"
            else -> "python"
        }
    }
}
