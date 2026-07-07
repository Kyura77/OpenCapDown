package com.opencapdown.core.sources

import app.cash.quickjs.QuickJs
import com.opencapdown.core.common.Logger
import org.json.JSONObject
import org.json.JSONArray
import java.io.Closeable

internal interface HttpBridgeInterface {
    fun fetch(url: String, headersJson: String?): String
}

internal interface HtmlBridgeInterface {
    fun parse(html: String): String
}

internal interface QueryBridgeInterface {
    fun query(html: String, css: String): String
}

internal class QuickJsSourceEngine(
    private val httpBridge: HttpBridge,
    private val htmlParserBridge: HtmlParserBridge,
    private val logger: Logger,
    private val verdinhaToken: String? = null,
    private val verdinhaMode: String = "cdn"
) : Closeable {
    private val runtime = QuickJs.create()

    init {
        runtime.set("__http", HttpBridgeInterface::class.java, object : HttpBridgeInterface {
            override fun fetch(url: String, headersJson: String?): String {
                val headers = if (headersJson != null) {
                    try {
                        val obj = JSONObject(headersJson)
                        obj.keys().asSequence().associateWith { obj.getString(it) }
                    } catch (_: Exception) { emptyMap() }
                } else emptyMap()
                return httpBridge.fetch(url, headers)
            }
        })

        runtime.set("__html", HtmlBridgeInterface::class.java, object : HtmlBridgeInterface {
            override fun parse(html: String): String {
                return htmlParserBridge.parse(html).html()
            }
        })

        runtime.set("__query", QueryBridgeInterface::class.java, object : QueryBridgeInterface {
            override fun query(html: String, css: String): String {
                return htmlParserBridge.query(html, css)
            }
        })

        // Injeta o token VIP da Verdinha como uma variável global no JS
        runtime.evaluate("globalThis.__verdinhaToken = " + if (verdinhaToken != null) "\"$verdinhaToken\"" else "null")
        // Injeta o modo de leitura da Verdinha (cdn ou vip)
        runtime.evaluate("globalThis.__verdinhaMode = \"$verdinhaMode\"")

        evaluateInitScript()
    }

    private fun evaluateInitScript() {
        runtime.evaluate("""
            globalThis.SourceEnv = {
                log: function(level, msg) {},
                fetch: function(url, headers) { 
                    var h = headers || {};
                    if (url.indexOf("api.verdinha.wtf") >= 0 && globalThis.__verdinhaToken) {
                        h["Authorization"] = "Bearer " + globalThis.__verdinhaToken;
                    }
                    var hStr = JSON.stringify(h);
                    return __http.fetch(url, hStr); 
                },
                parseHtml: function(html) { 
                    return __html.parse(html); 
                },
                queryAll: function(html, css) {
                    return JSON.parse(__query.query(html, css));
                }
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
                __p.then(function(v) { 
                    globalThis.__r = (typeof v === 'string') ? v : JSON.stringify(v); 
                }, function(e) { 
                    globalThis.__r = null; 
                });
            } else {
                globalThis.__r = (typeof __p === 'string') ? __p : JSON.stringify(__p);
            }
        """.trimIndent())
        executePendingJobs()
        val rawResult = runtime.evaluate("globalThis.__r") as? String ?: return null as T

        if (rawResult.startsWith("[") || rawResult.startsWith("{")) {
            return try {
                parseJson(rawResult) as T
            } catch (_: Exception) {
                rawResult as T
            }
        }
        return rawResult as T
    }

    private fun parseJson(jsonStr: String): Any? {
        if (jsonStr.startsWith("[")) {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<Any?>()
            for (i in 0 until arr.length()) {
                val item = arr.get(i)
                list.add(if (item is JSONObject || item is JSONArray) parseJson(item.toString()) else item)
            }
            return list
        } else if (jsonStr.startsWith("{")) {
            val obj = JSONObject(jsonStr)
            val map = mutableMapOf<String, Any?>()
            obj.keys().forEach { key ->
                val value = obj.get(key)
                map[key] = if (value is JSONObject || value is JSONArray) parseJson(value.toString()) else if (value == JSONObject.NULL) null else value
            }
            return map
        }
        return jsonStr
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
