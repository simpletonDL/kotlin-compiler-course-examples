import org.itmo.my.pretty.plugin.Throws

@Throws
fun f(): String {
    return "OK"
}

fun box(): String {
    return fOrNull() ?: "Fail"
}
