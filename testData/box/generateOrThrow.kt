// FULL_JDK
import org.itmo.my.pretty.plugin.GenerateOrThrow

@GenerateOrThrow
fun g(): String? {
    return "OK"
}

fun box(): String {
    return gOrThrow()
}