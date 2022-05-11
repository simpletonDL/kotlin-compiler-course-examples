import org.itmo.my.pretty.plugin.Throws

fun g(y: Int): String {
    return y.toString()
}

@Throws
fun id(x: Int): String {
    return g(x)
}
