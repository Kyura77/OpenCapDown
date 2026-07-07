package com.opencapdown.core.sources

import android.util.Log
import app.cash.quickjs.QuickJs
import app.cash.quickjs.QuickJsFunction
import com.opencapdown.core.common.Logger
import org.json.JSONObject
import java.io.Closeable

internal class QuickJsSourceEngine(
    private val httpBridge: HttpBridge,
    private val htmlParserBridge: HtmlParserBridge,
    private val logger: Logger
) : Closeable {
    private val runtime = QuickJs.create()

    init {
        runtime.set("__http", createHttpBridgeFunction())
        runtime.set("__html", createHtmlBridgeFunction())
        evaluateInitScript()
    }

    private fun createHttpBridgeFunction(): QuickJsFunction {
        return QuickJsFunction { args ->
            if (args.isEmpty()) return@QuickJsFunction ""
            val url = args[0]?.toString() ?: return@QuickJsFunction ""
            val headersJson = args.getOrNull(1)?.toString()
            val headers = if (headersJson != null) {
                try {
                    val obj = JSONObject(headersJson)
                    obj.keys().asSequence().associateWith { obj.getString(it) }
                } catch (_: Exception) { emptyMap() }
            } else emptyMap()
            httpBridge.fetch(url, headers)
        }
    }

    private fun createHtmlBridgeFunction(): QuickJsFunction {
        return QuickJsFunction { args ->
            val html = args.getOrNull(0)?.toString() ?: return@QuickJsFunction ""
            htmlParserBridge.parse(html).html()
        }
    }

    private fun evaluateInitScript() {
        runtime.evaluate("""
            globalThis.SourceEnv = {
                log: function(level, msg) {},
                fetch: function(url) { return __http.fetch(url, null); },
                parseHtml: function(html) { return __html.parse(html); }
            };
        """.trimIndent())
    }

    fun loadSource(code: String) {
        val wrapped = wrapSourceCode(code)
        runtime.evaluate(wrapped)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> invoke(methodName: String, vararg args: Any?): T {
        val argStr = serializeForJs(args)
        runtime.evaluate("""
            var __p = __source_module__["$methodName"]($argStr);
            if (__p && typeof __p.then === 'function') {
                __p.then(function(v) { globalThis.__r = v; }, function(e) { globalThis.__r = null; });
            } else {
                globalThis.__r = __p;
            }
        """.trimIndent())
        executePendingJobs()
        return runtime.get("__r") as T
    }

    private fun executePendingJobs() {
        var remaining = 1
        var safety = 64
        while (remaining > 0 && safety > 0) {
            remaining = try {
                val method = runtime::class.java.getMethod("executePending")
                method.invoke(runtime) as Int
            } catch (_: NoSuchMethodException) {
                0
            }
            safety--
        }
    }

    override fun close() {
        runtime.close()
    }

    companion object {
        fun wrapSourceCode(code: String): String {
            val stripped = code.trimStart().replace(Regex("""^export\s+default\s*"""), "")
            return "var __source_module__ = $stripped"
        }

        internal fun serializeForJs(args: Array<out Any?>): String {
            return args.joinToString(", ") { arg ->
                when (arg) {
                    is String -> "\"${arg.replace("\\", "\\\\").replace("\"", "\\\"")}\""
                    is Number -> arg.toString()
                    is Boolean -> arg.toString()
                    null -> "null"
                    is Map<*, *> -> {
                        arg.entries.joinToString(",", "{", "}") { (k, v) ->
                            "\"${k}\": ${serializeForJs(arrayOf(v))}"
                        }
                    }
                    is List<*> -> arg.joinToString(",", "[", "]") { serializeForJs(arrayOf(it)) }
                    else -> arg.toString()
                }
            }
        }
    }
}
