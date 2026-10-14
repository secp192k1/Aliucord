package com.aliucord.injector

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Member

private const val GMS_PACKAGE = "com.google.android.gms"
private const val MICROG_PACKAGE = "org.microg.gms"

/**
 * Use microG Limited Services to send Firebase push requests when Play Services is not installed
 *
 * Must run before Discord requests its push token
 */
internal fun initMicroG(appCtx: Application) {
    val pm = appCtx.packageManager
    if (pm.isInstalled(GMS_PACKAGE) || !pm.isInstalled(MICROG_PACKAGE)) return

    Logger.i("Play Services is missing, routing push notifications through $MICROG_PACKAGE")

    // Firebase sends its requests to Play Services by package name
    hookBefore(Intent::class.java.getDeclaredMethod("setPackage", String::class.java)) {
        if (it.args[0] == GMS_PACKAGE) it.args[0] = MICROG_PACKAGE
    }

    // Play Services version code is used by Firebase to pick its IPC transport
    pm.javaClass.declaredMethods
        .filter { it.name == "getPackageInfo" && it.parameterTypes.firstOrNull() == String::class.java }
        .forEach { method ->
            hookBefore(method) {
                if (it.args[0] == GMS_PACKAGE) it.args[0] = MICROG_PACKAGE
            }
        }

    // Firebase only trusts a service whose package has the SEND permission
    hookBefore(pm.javaClass.getMethod("checkPermission", String::class.java, String::class.java)) {
        if (it.args[1] == GMS_PACKAGE) it.args[1] = MICROG_PACKAGE
    }
}

private fun PackageManager.isInstalled(packageName: String): Boolean = try {
    getPackageInfo(packageName, 0)
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
}

private inline fun hookBefore(method: Member, crossinline callback: (XC_MethodHook.MethodHookParam) -> Unit) {
    XposedBridge.hookMethod(method, object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) = callback(param)
    })
}
