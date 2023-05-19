package io.github.deficuet.alsd

import org.json.JSONArray
import org.json.JSONObject

class JsonBuilder {
    val json = JSONObject()
    val empty = object { }

    inline operator fun String.invoke(builder: JsonBuilder.() -> Unit) {
        json.put(this, JsonBuilder().apply(builder).json)
    }
    operator fun String.invoke(to: Any) { json.put(this, to) }
    operator fun String.get(vararg op: Any) {
        json.put(
            this,
            JSONArray(
                if (op.any { it === empty }) {
                    emptyArray()
                } else {
                    op
                }
            )
        )
    }
}

inline fun json(builder: JsonBuilder.() -> Unit): JSONObject {
    return JsonBuilder().apply(builder).json
}