package be.tomsix

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.GitGuardBundle"

internal object GitGuardBundle {
    private val instance = DynamicBundle(GitGuardBundle::class.java, BUNDLE)

    @JvmStatic
    fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any?): @Nls String {
        return instance.getMessage(key, *params)
    }
}
