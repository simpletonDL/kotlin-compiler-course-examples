// FULL_JDK
import org.itmo.my.pretty.plugin.Throws
import java.lang.Exception

@Throws
fun f(): String {
    throw Exception()
}

fun box(): String {
    return fOrNull() ?: "OK"
}
