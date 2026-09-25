package android.content

abstract class Context {
    abstract fun getSharedPreferences(name: String, mode: Int): SharedPreferences
    companion object { const val MODE_PRIVATE = 0 }
}
interface SharedPreferences {
    fun getString(key: String, default: String?): String?
    fun getLong(key: String, default: Long): Long
    fun edit(): Editor
    interface Editor {
        fun putString(key: String, value: String): Editor
        fun putLong(key: String, value: Long): Editor
        fun commit(): Boolean
    }
}
