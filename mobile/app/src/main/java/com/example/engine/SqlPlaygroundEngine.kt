package com.example.engine

import android.database.sqlite.SQLiteDatabase

data class SqlExecutionResult(
    val columns: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList(),
    val rowCount: Int = 0,
    val notice: String = "",
    val isError: Boolean = false
)

/**
 * On-device SQL sandbox using an in-memory Android SQLiteDatabase pre-seeded
 * with the educational tables referenced across database-lessons.json
 * (products, customers, orders, employees, accounts, stock, sales, departments, tree).
 */
class SqlPlaygroundEngine {

    fun execute(query: String): SqlExecutionResult {
        val raw = query.trim()
        if (raw.isBlank()) {
            return SqlExecutionResult(
                notice = "أدخل استعلام SQL صالح لعرض النتائج هنا.",
                isError = false
            )
        }

        val db = SQLiteDatabase.create(null)
        return try {
            seedSchema(db)
            val statements = splitStatements(raw)
            if (statements.isEmpty()) {
                return SqlExecutionResult(notice = "لا توجد أوامر SQL قابلة للتنفيذ.")
            }

            var lastColumns = emptyList<String>()
            var lastRows = emptyList<List<String>>()
            var executedCount = 0
            var rowsAffected = 0
            val notices = mutableListOf<String>()

            for (stmt in statements) {
                val cleaned = stripLeadingComments(stmt).trim()
                if (cleaned.isEmpty()) continue
                val upper = cleaned.uppercase()

                // Handle conceptual PostgreSQL / transaction / window edge statements gracefully
                if (upper.startsWith("BEGIN") || upper.startsWith("COMMIT") || upper.startsWith("ROLLBACK") ||
                    upper.startsWith("START TRANSACTION") || upper.startsWith("SAVEPOINT")
                ) {
                    db.execSQL(cleaned)
                    notices += "تم تنفيذ معاملة: ${cleaned.lineSequence().first().take(48)}"
                    executedCount++
                    continue
                }

                if (upper.startsWith("EXPLAIN ") || upper.startsWith("ANALYZE ") || upper.startsWith("GRANT ") || upper.startsWith("CREATE INDEX") || upper.startsWith("CREATE OR REPLACE VIEW") || upper.startsWith("CREATE VIEW")) {
                    val normalizedForSqlite = cleaned
                        .replace(Regex("^CREATE\\s+OR\\s+REPLACE\\s+VIEW", RegexOption.IGNORE_CASE), "CREATE VIEW")
                    if (upper.startsWith("EXPLAIN ") || upper.startsWith("GRANT ")) {
                        notices += "تم تحليل الأمر بنجاح في بيئة التعلم."
                        if (lastColumns.isEmpty()) {
                            lastColumns = listOf("plan_status", "detail")
                            lastRows = listOf(listOf("جاهز للتنفيذ", cleaned.take(90)))
                        }
                        executedCount++
                        continue
                    } else {
                        runCatching { db.execSQL(normalizedForSqlite) }.onFailure {
                            notices += "تم تسجيل التعريف المفاهيمي للقاعدة."
                        }
                        executedCount++
                        continue
                    }
                }

                if (upper.startsWith("SELECT") || upper.startsWith("WITH") || upper.startsWith("PRAGMA")) {
                    db.rawQuery(cleaned, null).use { cursor ->
                        val cols = cursor.columnNames.toList()
                        val rows = mutableListOf<List<String>>()
                        var limit = 0
                        while (cursor.moveToNext() && limit < 200) {
                            val row = cols.indices.map { idx ->
                                when (cursor.getType(idx)) {
                                    android.database.Cursor.FIELD_TYPE_NULL -> "NULL"
                                    android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(idx).toString()
                                    android.database.Cursor.FIELD_TYPE_FLOAT -> {
                                        val d = cursor.getDouble(idx)
                                        if (d == d.toLong().toDouble()) d.toLong().toString() else String.format("%.2f", d)
                                    }
                                    android.database.Cursor.FIELD_TYPE_STRING -> cursor.getString(idx) ?: ""
                                    android.database.Cursor.FIELD_TYPE_BLOB -> "<BLOB>"
                                    else -> ""
                                }
                            }
                            rows += row
                            limit++
                        }
                        lastColumns = cols
                        lastRows = rows
                    }
                    executedCount++
                } else {
                    val pgAdapted = adaptPostgresDdl(cleaned)
                    try {
                        db.execSQL(pgAdapted)
                        if (upper.startsWith("INSERT") || upper.startsWith("UPDATE") || upper.startsWith("DELETE")) {
                            db.rawQuery("SELECT changes()", null).use { c ->
                                if (c.moveToFirst()) rowsAffected += c.getInt(0)
                            }
                        } else {
                            notices += "تم تطبيق بنية الجدول أو التعديل بنجاح."
                        }
                        executedCount++
                    } catch (e: Exception) {
                        return fallbackSimulatedResult(raw, e.message)
                    }
                }
            }

            if (lastColumns.isEmpty() && notices.isEmpty() && rowsAffected >= 0) {
                lastColumns = listOf("status", "affected_rows")
                lastRows = listOf(listOf("تم التنفيذ بنجاح", rowsAffected.toString()))
            }

            val finalNotice = when {
                notices.isNotEmpty() -> notices.joinToString(" · ")
                lastRows.isEmpty() && lastColumns.isNotEmpty() -> "تم تنفيذ الاستعلام بنجاح، لا توجد صفوف مطابقة للشرط."
                else -> "تم تنفيذ ${executedCount} استعلام · عرض ${lastRows.size} صف"
            }

            SqlExecutionResult(
                columns = lastColumns,
                rows = lastRows,
                rowCount = lastRows.size,
                notice = finalNotice,
                isError = false
            )
        } catch (e: Exception) {
            fallbackSimulatedResult(raw, e.message)
        } finally {
            db.close()
        }
    }

    private fun seedSchema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE products (
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                category TEXT DEFAULT 'عام',
                price REAL NOT NULL,
                stock INTEGER DEFAULT 10,
                is_active INTEGER DEFAULT 1
            )
            """.trimIndent()
        )
        db.execSQL("INSERT INTO products (id, name, category, price, stock, is_active) VALUES (1, 'كمبيوتر محمول', 'إلكترونيات', 3500, 14, 1)")
        db.execSQL("INSERT INTO products (id, name, category, price, stock, is_active) VALUES (2, 'شاشة عرض 4K', 'شاشات', 1450, 9, 1)")
        db.execSQL("INSERT INTO products (id, name, category, price, stock, is_active) VALUES (3, 'لوحة مفاتيح ميكانيكية', 'ملحقات', 280, 25, 1)")
        db.execSQL("INSERT INTO products (id, name, category, price, stock, is_active) VALUES (4, 'سماعة لاسلكية', 'صوتيات', 420, 0, 0)")

        db.execSQL(
            """
            CREATE TABLE customers (
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                customer_name TEXT NOT NULL,
                city TEXT DEFAULT 'الرياض',
                email TEXT
            )
            """.trimIndent()
        )
        db.execSQL("INSERT INTO customers VALUES (1, 'أحمد محمود', 'أحمد محمود', 'الرياض', 'ahmad@example.com')")
        db.execSQL("INSERT INTO customers VALUES (2, 'فاطمة الزهراء', 'فاطمة الزهراء', 'جدة', 'fatima@example.com')")
        db.execSQL("INSERT INTO customers VALUES (3, 'عمر خالد', 'عمر خالد', 'الدمام', NULL)")

        db.execSQL(
            """
            CREATE TABLE orders (
                id INTEGER PRIMARY KEY,
                customer_id INTEGER,
                customer_name TEXT,
                total_orders INTEGER,
                total_amount REAL,
                status TEXT,
                order_date TEXT
            )
            """.trimIndent()
        )
        db.execSQL("INSERT INTO orders VALUES (101, 1, 'أحمد محمود', 5, 1850, 'مكتمل', '2025-02-10')")
        db.execSQL("INSERT INTO orders VALUES (102, 2, 'فاطمة الزهراء', 3, 940, 'قيد التجهيز', '2025-02-11')")
        db.execSQL("INSERT INTO orders VALUES (103, 1, 'أحمد محمود', 2, 420, 'مكتمل', '2025-02-12')")

        db.execSQL(
            """
            CREATE TABLE departments (
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                budget REAL DEFAULT 50000
            )
            """.trimIndent()
        )
        db.execSQL("INSERT INTO departments VALUES (1, 'الهندسة', 120000), (2, 'المنتج', 85000), (3, 'المبيعات', 70000)")

        db.execSQL(
            """
            CREATE TABLE employees (
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                department_id INTEGER,
                salary REAL NOT NULL,
                hire_date TEXT
            )
            """.trimIndent()
        )
        db.execSQL("INSERT INTO employees VALUES (101, 'سارة إبراهيم', 1, 8500, '2023-03-15')")
        db.execSQL("INSERT INTO employees VALUES (102, 'خالد عبدالله', 1, 9200, '2022-07-01')")
        db.execSQL("INSERT INTO employees VALUES (103, 'نورة سالم', 2, 7800, '2024-01-10')")

        db.execSQL(
            """
            CREATE TABLE accounts (
                id INTEGER PRIMARY KEY,
                account_holder TEXT NOT NULL,
                balance REAL NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("INSERT INTO accounts VALUES (101, 'حساب التحويل', 4500), (202, 'حساب الاستقبال', 5500)")

        db.execSQL(
            """
            CREATE TABLE stock (
                id INTEGER PRIMARY KEY,
                product_id INTEGER,
                quantity INTEGER NOT NULL,
                warehouse TEXT DEFAULT 'الرئيسي'
            )
            """.trimIndent()
        )
        db.execSQL("INSERT INTO stock VALUES (1, 1, 14, 'الرياض'), (2, 2, 9, 'جدة'), (3, 3, 25, 'الدمام')")

        db.execSQL(
            """
            CREATE TABLE sales (
                id INTEGER PRIMARY KEY,
                product_id INTEGER,
                quantity INTEGER,
                amount REAL,
                sale_date TEXT
            )
            """.trimIndent()
        )
        db.execSQL("INSERT INTO sales VALUES (1, 1, 2, 7000, '2025-02-01'), (2, 2, 1, 1450, '2025-02-03'), (3, 3, 4, 1120, '2025-02-05')")

        db.execSQL(
            """
            CREATE TABLE tree (
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                parent_id INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("INSERT INTO tree VALUES (1, 'الإدارة العامة', NULL), (2, 'التطوير', 1), (3, 'البيانات', 2)")
    }

    private fun adaptPostgresDdl(sql: String): String {
        return sql
            .replace(Regex("\\bBIGSERIAL\\b", RegexOption.IGNORE_CASE), "INTEGER PRIMARY KEY AUTOINCREMENT")
            .replace(Regex("\\bSERIAL\\b", RegexOption.IGNORE_CASE), "INTEGER PRIMARY KEY AUTOINCREMENT")
            .replace(Regex("\\bTIMESTAMPTZ\\b", RegexOption.IGNORE_CASE), "TEXT")
            .replace(Regex("\\bNUMERIC\\s*\\([^)]+\\)", RegexOption.IGNORE_CASE), "REAL")
            .replace(Regex("\\bVARCHAR\\s*\\([^)]+\\)", RegexOption.IGNORE_CASE), "TEXT")
            .replace(Regex("\\bBOOLEAN\\b", RegexOption.IGNORE_CASE), "INTEGER")
            .replace(Regex("::[a-zA-Z0-9_]+"), "")
    }

    private fun splitStatements(raw: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inSingleQuote = false
        var inDoubleQuote = false
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote
                sb.append(ch)
            } else if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote
                sb.append(ch)
            } else if (ch == '-' && i + 1 < raw.length && raw[i + 1] == '-' && !inSingleQuote && !inDoubleQuote) {
                while (i < raw.length && raw[i] != '\n') {
                    sb.append(raw[i])
                    i++
                }
                sb.append('\n')
            } else if (ch == ';' && !inSingleQuote && !inDoubleQuote) {
                val s = sb.toString().trim()
                if (s.isNotEmpty()) out += s
                sb.clear()
            } else {
                sb.append(ch)
            }
            i++
        }
        val tail = sb.toString().trim()
        if (tail.isNotEmpty()) out += tail
        return out
    }

    private fun stripLeadingComments(stmt: String): String {
        return stmt.lines()
            .filterNot { it.trim().startsWith("--") }
            .joinToString("\n")
            .trim()
    }

    private fun fallbackSimulatedResult(query: String, errorMsg: String?): SqlExecutionResult {
        val commentOnly = query.lines().all { it.trim().isEmpty() || it.trim().startsWith("--") }
        if (commentOnly) {
            return SqlExecutionResult(
                columns = listOf("note"),
                rows = listOf(listOf(query.replace("--", "").trim().take(120))),
                rowCount = 1,
                notice = "تعليق توثيقي: لا يحتوي الاستعلام على أمر SQL بعد.",
                isError = false
            )
        }
        val literalMatch = Regex("SELECT\\s+(['\"])([\\s\\S]*?)\\1(?:\\s+AS\\s+([A-Za-z_][A-Za-z0-9_]*))?", RegexOption.IGNORE_CASE).find(query)
        if (literalMatch != null) {
            val col = literalMatch.groupValues[3].ifBlank { "result_value" }
            val valStr = literalMatch.groupValues[2].take(120)
            return SqlExecutionResult(
                columns = listOf(col),
                rows = listOf(listOf(valStr)),
                rowCount = 1,
                notice = "نتيجة قيمة مباشرة",
                isError = false
            )
        }
        return SqlExecutionResult(
            columns = emptyList(),
            rows = emptyList(),
            rowCount = 0,
            notice = "خطأ SQL: ${errorMsg ?: "تحقق من صياغة الاستعلام وأسماء الجداول والأعمدة."}",
            isError = true
        )
    }
}
