package android.content

abstract class Context {
    open val applicationContext: Context get() = this
    abstract fun getSharedPreferences(name: String, mode: Int): SharedPreferences
    companion object { const val MODE_PRIVATE = 0 }
}
interface SharedPreferences {
    fun contains(key: String): Boolean
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getString(key: String, default: String?): String?
    fun edit(): Editor
    interface Editor {
        fun putBoolean(key: String, value: Boolean): Editor
        fun putString(key: String, value: String): Editor
        fun remove(key: String): Editor
        fun commit(): Boolean
        fun apply()
    }
}
