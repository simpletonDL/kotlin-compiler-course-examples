// FULL_JDK
import org.itmo.my.pretty.plugin.GenerateOrThrow

@GenerateOrThrow
fun g(): String? {
    return ""
}

fun test(): String {
    return gOrThrow()
}