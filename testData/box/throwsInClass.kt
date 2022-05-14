// FULL_JDK
import org.itmo.my.pretty.plugin.Throws
import java.lang.Exception

class A {
    @Throws
    fun f(): String {
        throw Exception()
    }
}

fun box(): String {
    val x = A()
    return x.fOrNull() ?: "OK"
}