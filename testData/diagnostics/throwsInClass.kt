import org.itmo.my.pretty.plugin.Throws

class A {
    fun f() {

    }

    @Throws
    fun g() {
        f()
    }
}
