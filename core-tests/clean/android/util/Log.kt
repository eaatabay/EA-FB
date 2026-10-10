package android.util

object Log {
    val records = java.util.concurrent.CopyOnWriteArrayList<Pair<String, String>>()
    fun i(tag: String, text: String): Int { records += tag to text; return 0 }
    fun w(tag: String, text: String): Int { records += tag to text; return 0 }
}
