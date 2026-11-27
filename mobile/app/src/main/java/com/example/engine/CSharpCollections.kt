package com.example.engine

data class CollectionDescription(
    val name: String,
    val args: List<String>,
    val indexKeyType: String?,
    val indexValueType: String?,
    val iteratorType: String?
)

data class CollectionMethodSignature(
    val argumentTypes: List<String>,
    val resultType: String
)

class CollectionState(
    val name: String,
    val listItems: MutableList<Any?> = mutableListOf(),
    val dictItems: LinkedHashMap<Any?, Any?> = LinkedHashMap(),
    val setItems: LinkedHashSet<Any?> = LinkedHashSet(),
    var revision: Int = 0
)

data class CollectionMethodResult(
    val value: Any?,
    val previousCount: Int,
    val count: Int
)

object CSharpCollections {

    private val PRIMITIVES = setOf("int", "string", "bool", "char")

    private data class ModelSpec(
        val arity: Int,
        val indexTypes: Pair<Int, Int>? = null, // 0 or 1 -> arg index, -1 -> "int"
        val iterationOrder: String? = null, // "forward" | "reverse"
        val methods: Map<String, Pair<List<Any>, Any>>
    )

    private val MODELS: Map<String, ModelSpec> = mapOf(
        "List" to ModelSpec(
            arity = 1,
            indexTypes = -1 to 0, // ["int", arg0]
            iterationOrder = "forward",
            methods = mapOf(
                "Add" to (listOf(0) to "void"),
                "Contains" to (listOf(0) to "bool"),
                "RemoveAt" to (listOf("int") to "void")
            )
        ),
        "Stack" to ModelSpec(
            arity = 1,
            iterationOrder = "reverse",
            methods = mapOf(
                "Push" to (listOf(0) to "void"),
                "Pop" to (emptyList<Any>() to 0),
                "Peek" to (emptyList<Any>() to 0)
            )
        ),
        "Queue" to ModelSpec(
            arity = 1,
            iterationOrder = "forward",
            methods = mapOf(
                "Enqueue" to (listOf(0) to "void"),
                "Dequeue" to (emptyList<Any>() to 0),
                "Peek" to (emptyList<Any>() to 0)
            )
        ),
        "LinkedList" to ModelSpec(
            arity = 1,
            iterationOrder = "forward",
            methods = mapOf(
                "AddFirst" to (listOf(0) to "void"),
                "AddLast" to (listOf(0) to "void"),
                "RemoveFirst" to (emptyList<Any>() to "void")
            )
        ),
        "Dictionary" to ModelSpec(
            arity = 2,
            indexTypes = 0 to 1,
            methods = mapOf(
                "Add" to (listOf(0, 1) to "void"),
                "ContainsKey" to (listOf(0) to "bool")
            )
        ),
        "HashSet" to ModelSpec(
            arity = 1,
            methods = mapOf(
                "Add" to (listOf(0) to "bool"),
                "Contains" to (listOf(0) to "bool")
            )
        )
    )

    fun collectionArity(name: String): Int = MODELS[name]?.arity ?: 0

    fun describeCollection(type: String?): CollectionDescription? {
        if (type.isNullOrBlank()) return null
        val match = Regex("^(\\w+)<([^<>]+)>\\??$").matchEntire(type) ?: return null
        val name = match.groupValues[1]
        val args = match.groupValues[2].split(",").map { it.trim() }
        val model = MODELS[name] ?: return null
        if (model.arity != args.size || !args.all { it in PRIMITIVES }) return null
        val resolve: (Int) -> String = { code -> if (code == -1) "int" else args[code] }
        return CollectionDescription(
            name = name,
            args = args,
            indexKeyType = model.indexTypes?.first?.let(resolve),
            indexValueType = model.indexTypes?.second?.let(resolve),
            iteratorType = model.iterationOrder?.let { args[0] }
        )
    }

    fun collectionSignature(description: CollectionDescription, methodName: String): CollectionMethodSignature? {
        val spec = MODELS[description.name]?.methods?.get(methodName) ?: return null
        val resolve: (Any) -> String = { token ->
            when (token) {
                is Int -> description.args[token]
                else -> token.toString()
            }
        }
        return CollectionMethodSignature(
            argumentTypes = spec.first.map(resolve),
            resultType = resolve(spec.second)
        )
    }

    fun makeCollection(description: CollectionDescription): CollectionState {
        return CollectionState(name = description.name)
    }

    fun collectionCount(state: CollectionState): Int = when (state.name) {
        "Dictionary" -> state.dictItems.size
        "HashSet" -> state.setItems.size
        else -> state.listItems.size
    }

    fun collectionSnapshot(state: CollectionState): List<Any?> = when (state.name) {
        "Dictionary" -> state.dictItems.entries.map { "${it.key}:${it.value}" }
        "HashSet" -> state.setItems.toList()
        else -> state.listItems.toList()
    }

    fun collectionIteration(state: CollectionState): List<Any?> {
        val snap = collectionSnapshot(state)
        return if (MODELS[state.name]?.iterationOrder == "reverse") snap.reversed() else snap
    }

    private fun ensureRoom(state: CollectionState, limit: Int, fail: (String, String) -> Nothing) {
        if (collectionCount(state) >= limit) {
            fail("وصلت المجموعة إلى حدود المحاكي التعليمي.", "limit")
        }
    }

    private fun checkedIndex(items: List<Any?>, index: Int, fail: (String, String) -> Nothing): Int {
        if (index < 0 || index >= items.size) {
            fail("الفهرس $index خارج عدد العناصر ${items.size}.", "runtime")
        }
        return index
    }

    fun applyMethod(
        state: CollectionState,
        methodName: String,
        args: List<Any?>,
        limit: Int,
        fail: (String, String) -> Nothing
    ): CollectionMethodResult {
        val previousCount = collectionCount(state)
        val result: Any? = when (state.name) {
            "List" -> when (methodName) {
                "Add" -> {
                    ensureRoom(state, limit, fail)
                    state.listItems.add(args[0])
                    Unit
                }
                "Contains" -> state.listItems.contains(args[0])
                "RemoveAt" -> {
                    val idx = checkedIndex(state.listItems, args[0] as Int, fail)
                    state.listItems.removeAt(idx)
                    Unit
                }
                else -> fail("طريقة $methodName غير مدعومة.", "name")
            }
            "Stack" -> when (methodName) {
                "Push" -> {
                    ensureRoom(state, limit, fail)
                    state.listItems.add(args[0])
                    Unit
                }
                "Pop" -> {
                    if (state.listItems.isEmpty()) fail("المجموعة فارغة. افحص Count قبل أخذ عنصر.", "runtime")
                    state.listItems.removeAt(state.listItems.lastIndex)
                }
                "Peek" -> {
                    if (state.listItems.isEmpty()) fail("المجموعة فارغة. افحص Count قبل أخذ عنصر.", "runtime")
                    state.listItems.last()
                }
                else -> fail("طريقة $methodName غير مدعومة.", "name")
            }
            "Queue" -> when (methodName) {
                "Enqueue" -> {
                    ensureRoom(state, limit, fail)
                    state.listItems.add(args[0])
                    Unit
                }
                "Dequeue" -> {
                    if (state.listItems.isEmpty()) fail("المجموعة فارغة. افحص Count قبل أخذ عنصر.", "runtime")
                    state.listItems.removeAt(0)
                }
                "Peek" -> {
                    if (state.listItems.isEmpty()) fail("المجموعة فارغة. افحص Count قبل أخذ عنصر.", "runtime")
                    state.listItems.first()
                }
                else -> fail("طريقة $methodName غير مدعومة.", "name")
            }
            "LinkedList" -> when (methodName) {
                "AddFirst" -> {
                    ensureRoom(state, limit, fail)
                    state.listItems.add(0, args[0])
                    Unit
                }
                "AddLast" -> {
                    ensureRoom(state, limit, fail)
                    state.listItems.add(args[0])
                    Unit
                }
                "RemoveFirst" -> {
                    if (state.listItems.isEmpty()) fail("القائمة فارغة. افحص Count قبل الحذف.", "runtime")
                    state.listItems.removeAt(0)
                    Unit
                }
                else -> fail("طريقة $methodName غير مدعومة.", "name")
            }
            "Dictionary" -> when (methodName) {
                "Add" -> {
                    val k = args[0]
                    val v = args[1]
                    if (state.dictItems.containsKey(k)) {
                        fail("المفتاح موجود بالفعل. استخدم الفهرسة لتحديثه.", "runtime")
                    }
                    ensureRoom(state, limit, fail)
                    state.dictItems[k] = v
                    Unit
                }
                "ContainsKey" -> state.dictItems.containsKey(args[0])
                else -> fail("طريقة $methodName غير مدعومة.", "name")
            }
            "HashSet" -> when (methodName) {
                "Add" -> {
                    val v = args[0]
                    if (state.setItems.contains(v)) {
                        false
                    } else {
                        ensureRoom(state, limit, fail)
                        state.setItems.add(v)
                        true
                    }
                }
                "Contains" -> state.setItems.contains(args[0])
                else -> fail("طريقة $methodName غير مدعومة.", "name")
            }
            else -> fail("مجموعة غير معروفة.", "type")
        }
        val count = collectionCount(state)
        if (count != previousCount) state.revision++
        return CollectionMethodResult(result, previousCount, count)
    }

    fun readIndex(state: CollectionState, index: Any?, fail: (String, String) -> Nothing): Any? {
        return if (state.name == "List") {
            val idx = checkedIndex(state.listItems, index as Int, fail)
            state.listItems[idx]
        } else {
            if (!state.dictItems.containsKey(index)) {
                fail("المفتاح غير موجود. افحصه باستخدام ContainsKey أولًا.", "runtime")
            }
            state.dictItems[index]
        }
    }

    fun writeIndex(
        state: CollectionState,
        index: Any?,
        value: Any?,
        limit: Int,
        fail: (String, String) -> Nothing
    ): Any? {
        return if (state.name == "List") {
            val idx = checkedIndex(state.listItems, index as Int, fail)
            val prev = state.listItems[idx]
            state.listItems[idx] = value
            state.revision++
            prev
        } else {
            val prev = state.dictItems[index]
            if (!state.dictItems.containsKey(index)) {
                ensureRoom(state, limit, fail)
                state.revision++
            }
            state.dictItems[index] = value
            prev
        }
    }
}
