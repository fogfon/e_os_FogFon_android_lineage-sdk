#!/bin/bash

echo "Clearing intermediates"
rm -rf intermediates
mkdir intermediates

if [[ -z "${ANDROID_HOME}" ]]; then
	echo "{ANDROID_HOME} path variable is not set. Set it to point Android SDK."
	exit 1
else
	build_tools_dir=$ANDROID_HOME/build-tools/29.0.2/
fi

aapt2="${build_tools_dir}/aapt2"

echo "Compiling resources"
$aapt2 compile --dir lineage/res/res -o intermediates/resources.zip

platform_dir=$ANDROID_HOME/platforms/android-28

echo "Linking resources"
$aapt2 link intermediates/resources.zip -I $platform_dir/android.jar \
 --private-symbols org.lineageos.platform.internal \
 --allow-reserved-package-id \
 --package-id 63 \
 --manifest lineage/res/AndroidManifest.xml \
 --java intermediates \
 -o intermediates/res.apk

echo "Unzipping temporary apk"
unzip -qo intermediates/res.apk -d intermediates/

# Creating obj directory
rm -rf obj
mkdir obj

# Compiling R.java
echo "Compiling R.java"
javac -source 1.8 -target 1.8 $(find intermediates/lineageos -type f -name 'R.java') -d obj

jar cvf e-ui-sdk.jar -C obj . -C intermediates resources.arsc

echo "Clearing intermediate sources"
rm -rf intermediates
rm -rf obj

echo "Creating sha256sum"
sha256sum e-ui-sdk.jar > e-ui-sdk.jar.sha256sum

echo "e-ui-sdk.jar generated successully."
