// FULL_JDK
import org.itmo.my.pretty.plugin.Throws
import java.lang.Exception

fun g(y: Int): String {
    throw Exception()
}

@Throws
fun id(x: Int): String {
    return g(x)
}
