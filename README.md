# PermissionRetriever-Kt

Easiest way for getting android runtime permissions

[ ![Download](https://api.bintray.com/packages/androidpirates/maven/retriever-kt/images/download.svg) ](https://bintray.com/androidpirates/maven/retriever-kt/_latestVersion)

[Java Version](https://github.com/AndroidPirates/PermissionRetriever)

Install
------- 

```groove
implementation 'ru.androidpirates.permissions:retriever-kt:1.0.2'
```

Usage
-----

```kotlin
class SomeClass { /* extends android.app.Activity or android.app.Fragment or android.support.v4.app.Fragment*/
    private val permissionRetriever = PermissionRetriever()
        
    private fun onCapturePhotoClicked() {
        permissionRetriever
                .silentMode(false) // enable or disable AlertDialog with explanations after deny
                .logging(BuildConfig.DEBUG) // enable or disable error logging
                .withPermission(Manifest.permission.CAMERA, "for take Your beautiful face" /* it's optional explanation */)
                .withPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                .run(this, 
                     { /*action if all permissions was accepted*/ }, //optional part
                     { /*action if some permission was denied*/ } //optional part
                )
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val willDoSomeWork = permissionRetriever.onRequestPermissionsResult(requestCode)
    }
}
```

Global settings
```kotlin
private fun setRetrieverGlobalSettings() {
    PermissionRetriever.Global.setSilentMode(true) // false by default
    PermissionRetriever.Global.setLoggingEnabled(true) // false by default
}
```

In-box we are supports only two translations: English and Russian. Below you can see used string resources. You can change or translate they how you need
```xml
<resources>
    <string name="perm_retriever_button_ask_again">Ask again</string>
    <string name="perm_retriever_button_settings">Settings</string>
    <string name="perm_retriever_button_cancel">Cancel</string>

    <plurals name="perm_retriever_message_denied">
        <item quantity="one">For correctly app working we need to you grant this permission:\n</item>
        <item quantity="many">For correctly app working we need to you grant this permissions list:\n</item>
    </plurals>

    <plurals name="perm_retriever_title_denied">
        <item quantity="one">You have denied permission</item>
        <item quantity="many">You have denied permissions</item>
    </plurals>
</resources>
```

License
-------

       Copyright 2018 Android Pirates

       Licensed under the Apache License, Version 2.0 (the "License");
       you may not use this file except in compliance with the License.
       You may obtain a copy of the License at

           http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing, software
       distributed under the License is distributed on an "AS IS" BASIS,
       WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
       See the License for the specific language governing permissions and
       limitations under the License.
