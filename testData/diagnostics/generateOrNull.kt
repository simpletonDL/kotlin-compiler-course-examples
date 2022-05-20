// FULL_JDK
import org.itmo.my.pretty.plugin.GenerateOrNull
import java.lang.Exception

fun g(y: Int): String {
    throw Exception()
}

@GenerateOrNull
fun id(x: Int): String {
    return g(x)
}
