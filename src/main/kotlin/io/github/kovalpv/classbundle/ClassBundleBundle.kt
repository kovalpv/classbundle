package io.github.kovalpv.classbundle

import com.intellij.DynamicBundle
import java.util.function.Supplier

const val BUNDLE = "messages.ClassBundleBundle"

object ClassBundleBundle : DynamicBundle(BUNDLE) {
    fun message(key: String, vararg params: Any): String {
        return getMessage(key, *params)
    }

    fun messagePointer(key: String, vararg params: Any): Supplier<String> {
        return getLazyMessage(key, *params)
    }
}
