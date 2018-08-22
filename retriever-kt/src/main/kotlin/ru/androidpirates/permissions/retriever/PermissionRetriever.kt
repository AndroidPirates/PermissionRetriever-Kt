/*
 * Copyright (c) 2018 Android Pirates
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.androidpirates.permissions.retriever

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.support.annotation.StringRes
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.text.TextUtils
import android.util.Log
import android.app.Fragment as PlatformFragment
import android.support.v4.app.Fragment as SupportFragment

class PermissionRetriever {
    private var pendingIfGrantedAction: (() -> Unit)? = null
    private var pendingIfUnacceptedAction: (() -> Unit)? = null
    private var appCompatFragment: SupportFragment? = null
    private var platformFragment: PlatformFragment? = null
    private var activity: Activity? = null
    private var isLoggingEnabled: Boolean? = null
    private var isSilentMode: Boolean? = null

    private var isRewriteProtectionDisabled = true

    private val permissionsRationalesMap: MutableMap<String, Any> = HashMap()

    private val context: Context
        get() = when {
            platformFragment != null -> platformFragment!!.activity
            appCompatFragment != null -> appCompatFragment!!.activity!!
            else -> activity!!
        }

    /**
     * This method defines usage of "silent mode". It means [AlertDialog] will not be called
     * after declining some permissions, also if `ifUnaccepted` is present, it will be called
     * immediately.
     *
     *
     * This setting overrides same global setting
     *
     * @param isSilentMode value for turning on/off "silent mode"
     * @return this instance for chained calls
     */
    fun silentMode(isSilentMode: Boolean): PermissionRetriever {
        if (isRewriteProtectionDisabled) {
            this.isSilentMode = isSilentMode
        } else {
            logRewriteProtectionEnabled()
        }
        return this
    }

    /**
     * This method defines usage of error logging.
     *
     *
     * This setting overrides same global setting
     *
     * @param isLoggingEnabled for turning on/off logging
     * @return this instance for chained calls
     */
    fun logging(isLoggingEnabled: Boolean): PermissionRetriever {
        if (isRewriteProtectionDisabled) {
            this.isLoggingEnabled = isLoggingEnabled
        } else {
            logRewriteProtectionEnabled()
        }
        return this
    }

    /**
     * This method puts a permission with associated resource id of explanation for a request.
     *
     * @param permission name of permission from [android.Manifest.permission]
     * @param explanation some information about usage this permission, this part will be displayed
     *                    to user if request will be denied
     *
     * @return this instance for chained calls
     *
     * @see android.Manifest.permission
     */
    fun withPermission(permission: String, @StringRes explanation: Int): PermissionRetriever {
        if (isRewriteProtectionDisabled) {
            if (permission.isNotBlank()) {
                permissionsRationalesMap[permission] = explanation
            } else {
                logPermissionIsEmpty()
            }
        } else {
            logRewriteProtectionEnabled()
        }
        return this
    }

    /**
     * This method puts a permission with associated string explanation for a request.
     *
     * @param permission name of permission from [android.Manifest.permission]
     * @param explanation some information about usage this permission, this part will be displayed
     *                    to user if request will be denied
     *
     * @return this instance for chained calls
     *
     * @see android.Manifest.permission
     */
    @JvmOverloads
    fun withPermission(permission: String, explanation: String? = null): PermissionRetriever {
        if (isRewriteProtectionDisabled) {
            if (permission.isNotBlank()) {
                permissionsRationalesMap[permission] = explanation ?: ""
            } else {
                logPermissionIsEmpty()
            }
        } else {
            logRewriteProtectionEnabled()
        }
        return this
    }

    /**
     * This method requests permissions and if when a user accepts it invokes the presented blocks
     * of code.
     *
     * @param caller an object who can be instantiated from [android.app.Fragment]
     * or [android.support.v4.app.Fragment] or [android.app.Activity]
     * @param ifGranted the runnable who will be invoked when a user will accept the requested
     *                  permissions
     * @param ifUnaccepted the runnable who will be invoked when a user will decline at least
     *                     one of the requested permissions
     */
    @JvmOverloads
    fun run(caller: Any, ifGranted: (() -> Unit)? = null, ifUnaccepted: (() -> Unit)? = null) {
        if (isRewriteProtectionDisabled) {
            setTrueCaller(caller)
            pendingIfGrantedAction = ifGranted
            pendingIfUnacceptedAction = ifUnaccepted
            if (isSilentMode == null) {
                isSilentMode = Global.instance.isSilentMode
            }
            if (isLoggingEnabled == null) {
                isLoggingEnabled = Global.instance.isLoggingEnabled
            }
            isRewriteProtectionDisabled = false
            checkAndRun()
        } else {
            logRewriteProtectionEnabled()
        }
    }

    /**
     * This method should be called in `onPermissionResult` of your `Fragment`
     * or `Activity`.
     *
     *
     * It checks request code and if it equaled [REQUEST_PERMISSIONS_CODE] does some checks
     * for showing the [AlertDialog] or run `ifUnaccepted` block if it present and the
     * [silentMode] turned on.
     *
     * @param requestCode a request code delegated from `onPermissionResult` of your
     *                    `Fragment` or `Activity`
     * @return true if `requestCode` is equaled [REQUEST_PERMISSIONS_CODE] and
     *         `PermissionRetriever` will do some stuff
     */
    fun onPermissionResult(requestCode: Int): Boolean {
        if (requestCode != REQUEST_PERMISSIONS_CODE) {
            return false
        } else {
            if (check()) {
                runGranted()
            } else {
                if (somePermissionPermanentlyDenied()) {
                    showRationaleToSettings()
                } else {
                    showRationaleToRequest()
                }
            }
            return true
        }
    }

    /**
     * This method clears internal variables for make this instance are re-used. It will be called
     * automatically after calling `ifGranted` or `ifUnaccepted` blocks.
     */
    fun clear() {
        permissionsRationalesMap.clear()
        platformFragment = null
        appCompatFragment = null
        activity = null
        pendingIfGrantedAction = null
        pendingIfUnacceptedAction = null
        isSilentMode = null
        isLoggingEnabled = null
        isRewriteProtectionDisabled = true
    }

    private fun logRewriteProtectionEnabled() {
        if (isLoggingEnabled!!) {
            Log.e(LOG_TAG, "Rewrite protection is enabled, call clear() for re-use this " +
                    "instance. @" + hashCode())
        }
    }

    private fun logPermissionIsEmpty() {
        if (isLoggingEnabled!!) {
            Log.e(LOG_TAG, "Passed permissions can not be empty!")
        }
    }

    @Throws(IllegalArgumentException::class)
    private fun setTrueCaller(caller: Any) {
        when (caller) {
            is PlatformFragment -> platformFragment = caller
            is SupportFragment -> appCompatFragment = caller
            is Activity -> activity = caller
            else -> throw IllegalArgumentException("Passed wrong caller object")
        }
    }

    private fun checkAndRun() {
        if (check()) {
            runGranted()
        } else {
            if (shouldShowRationale()) {
                showRationaleToRequest()
            } else {
                request()
            }
        }
    }

    private fun check(): Boolean {
        var allGranted = true
        if (!permissionsRationalesMap.isEmpty()) {
            val granted = ArrayList<Map.Entry<String, Any>>()
            permissionsRationalesMap.forEach {
                if (hasPermission(context, it.key)) {
                    granted.add(it)
                } else {
                    allGranted = false
                }
            }
            permissionsRationalesMap.entries.removeAll(granted)
        }
        return allGranted
    }

    private fun runGranted() {
        pendingIfGrantedAction?.invoke()
        clear()
    }

    private fun runUnaccepted() {
        pendingIfUnacceptedAction?.invoke()
        clear()
    }

    private fun request() {
        requestPermissions(permissionsRationalesMap.keys.toTypedArray())
    }

    private fun requestPermissions(permissions: Array<String>) {
        if (appCompatFragment != null) {
            appCompatFragment!!.requestPermissions(permissions, REQUEST_PERMISSIONS_CODE)
        } else if (platformFragment != null) {
            if (Build.VERSION.SDK_INT >= 23) {
                platformFragment!!.requestPermissions(permissions, REQUEST_PERMISSIONS_CODE)
            } else {
                if (isLoggingEnabled!!) {
                    Log.e(LOG_TAG, "Current sdk < 23 ver api and used platform's Fragment. " +
                            "Request permissions wasn't called")
                }
            }
        } else {
            ActivityCompat.requestPermissions(activity!!, permissions, REQUEST_PERMISSIONS_CODE)
        }
    }

    private fun somePermissionPermanentlyDenied(): Boolean {
        permissionsRationalesMap.keys.filter {
            !shouldShowRequestPermissionRationale(it)
        }.forEach {
            return true
        }
        return false
    }

    private fun shouldShowRationale(): Boolean {
        permissionsRationalesMap.keys.filter {
            shouldShowRequestPermissionRationale(it)
        }.forEach {
            return true
        }
        return false
    }

    private fun shouldShowRequestPermissionRationale(permission: String): Boolean {
        return when {
            appCompatFragment != null -> {
                appCompatFragment!!.shouldShowRequestPermissionRationale(permission)
            }
            platformFragment != null -> {
                if (Build.VERSION.SDK_INT >= 23) {
                    platformFragment!!.shouldShowRequestPermissionRationale(permission)
                } else {
                    if (isLoggingEnabled!!) {
                        Log.w(LOG_TAG, "Current sdk < 23 ver api and used platform's " +
                                "Fragment. Trying to get value from " +
                                "Activity.shouldShowRequestPermissionRationale()")
                    }
                    ActivityCompat.shouldShowRequestPermissionRationale(activity!!, permission)
                }
            }
            else -> ActivityCompat.shouldShowRequestPermissionRationale(activity!!, permission)
        }
    }

    private fun showRationaleToRequest() {
        if (!isSilentMode!!) {
            prepareDialog()
                    .setPositiveButton(R.string.perm_retriever_button_ask_again) { _, _ ->
                        request()
                    }
                    .show()
        } else {
            runUnaccepted()
        }
    }

    private fun showRationaleToSettings() {
        if (!isSilentMode!!) {
            prepareDialog()
                    .setPositiveButton(R.string.perm_retriever_button_settings) { _, _ ->
                        context.startActivity(intentToSettings())
                    }
                    .show()
        } else {
            runUnaccepted()
        }
    }

    private fun prepareDialog(): AlertDialog.Builder {
        val permCount = permissionsRationalesMap.size
        val message = StringBuilder(getQuantity(
                R.string.perm_retriever_message_denied_one,
                R.string.perm_retriever_message_denied_many,
                permCount
        ))

        permissionsRationalesMap.forEach { permission, explanation ->
            message.append("\n").append(cutPermissionName(permission))

            if (explanation is Int) {
                if (explanation != -1) {
                    message.append(" - ").append(context.getString(explanation))
                }
            } else if (explanation is String) {
                if (!TextUtils.isEmpty(explanation)) {
                    message.append(" - ").append(explanation)
                }
            }
        }
        return AlertDialog.Builder(context)
                .setTitle(getQuantity(
                        R.string.perm_retriever_title_denied_one,
                        R.string.perm_retriever_title_denied_many,
                        permCount
                ))
                .setMessage(message)
                .setOnCancelListener { runUnaccepted() }
                .setNegativeButton(R.string.perm_retriever_button_cancel) { _, _ ->
                    runUnaccepted()
                }
    }

    private fun cutPermissionName(permission: String): String {
        val lastDotIndex = permission.lastIndexOf('.') + 1
        return permission.substring(lastDotIndex).replace("_", " ")
    }

    private fun intentToSettings(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
    }

    private fun getQuantity(@StringRes oneId: Int, @StringRes manyId: Int, quantity: Int): String {
        return context.getString(if (quantity > 1) manyId else oneId)
    }

    companion object {
        private const val LOG_TAG = "PermissionRetriever"
        private const val REQUEST_PERMISSIONS_CODE = 1689

        /**
         * Checks passed permission.
         *
         * @param permission permission for check
         * @param caller an object who can be instantiated from [android.app.Fragment]
         * or [android.support.v4.app.Fragment] or [android.app.Activity]
         *
         * @return true if passed permission are grated
         *
         * @throws IllegalArgumentException if passed caller not an instance of expected types
         *
         * @see android.Manifest.permission
         */
        @JvmStatic fun hasPermission(caller: Any, permission: String): Boolean {
            return hasPermission(getContextFromCaller(caller), permission)
        }

        /**
         * Checks all passed permissions.
         *
         * @param permissions array of permission for check
         * @param caller an object who can be instantiated from [android.app.Fragment]
         * or [android.support.v4.app.Fragment] or [android.app.Activity]
         *
         * @return true if all passed permissions are grated
         *
         * @throws IllegalArgumentException if passed caller not an instance of expected types
         *
         * @see android.Manifest.permission
         */
        @JvmStatic fun hasAllPermissions(caller: Any, vararg permissions: String): Boolean {
            val context = getContextFromCaller(caller)
            permissions.forEach { if (!hasPermission(context, it)) return false }
            return true
        }

        /**
         * Checks all passed permissions.
         *
         * @param permissions collection of permission for check
         * @param caller an object who can be instantiated from [android.app.Fragment]
         * or [android.support.v4.app.Fragment] or [android.app.Activity]
         *
         * @return true if all passed permissions are grated
         *
         * @throws IllegalArgumentException if passed caller not an instance of expected types
         *
         * @see android.Manifest.permission
         */
        @JvmStatic fun hasAllPermissions(caller: Any, permissions: List<String>): Boolean {
            return hasAllPermissions(caller, *permissions.toTypedArray())
        }

        @JvmStatic private fun hasPermission(context: Context, permission: String): Boolean {
            return ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED
        }

        @JvmStatic
        @Throws(IllegalArgumentException::class)
        private fun getContextFromCaller(caller: Any): Context {
            return when (caller) {
                is PlatformFragment -> caller.activity
                is SupportFragment -> caller.activity!!
                is Activity -> caller
                else -> throw IllegalArgumentException("Cant get context from caller")
            }
        }
    }

    /**
     * Global settings for [PermissionRetriever]
     * */
    class Global private constructor() {

        companion object {

            /**
             * @return singleton instance of global settings
             */
            @JvmStatic internal val instance: Global by lazy { Holder.INSTANCE }

            /**
             * This method defines usage of "silent mode". Like as
             * [PermissionRetriever.silentMode], but have global effect
             *
             * @param silentMode value for turning on/off global "silent mode"
             */
            @JvmStatic fun setSilentMode(silentMode: Boolean) {
                instance.isSilentMode = silentMode
            }

            /**
             * This method defines usage of error logging. Like as
             * [PermissionRetriever.logging], but have global effect
             *
             * @param loggingEnabled for turning on/off global logging
             */
            @JvmStatic fun setLoggingEnabled(loggingEnabled: Boolean) {
                instance.isLoggingEnabled = loggingEnabled
            }
        }

        internal var isLoggingEnabled = false
        internal var isSilentMode = false

        private object Holder {
            val INSTANCE = Global()
        }
    }
}
