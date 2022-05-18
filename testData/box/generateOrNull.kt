// FULL_JDK
import org.itmo.my.pretty.plugin.GenerateOrNull
import java.lang.Exception

@GenerateOrNull
fun f(): String {
    throw Exception()
}

fun box(): String {
    return fOrNull() ?: "OK"
}
