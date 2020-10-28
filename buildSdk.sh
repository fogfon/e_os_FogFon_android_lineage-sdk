#!/bin/bash

echo "Clearing intermediates"
rm -rf intermediates
mkdir intermediates

if [[ -z "${ANDROID_HOME}" ]]; then
	echo "{ANDROID_HOME} path variable is not set. Set it to point Android SDK."
	exit 1
else
	build_tools_dir=$ANDROID_HOME/build-tools/
	declare -a build_tools
	for f in $build_tools_dir*;
	do
		if [ -d "$f" ]; then
			build_tools+=($(basename $f))
    	fi
	done
	if [ ${#build_tools[@]} -eq 0 ]; then
		echo "No build-tools found at $build_tools_dir. Make sure you've atleast one build tools downloaded."
		exit 1
	else 
		sorted_build_tools=($(for l in ${build_tools[@]}; do echo $l; done | sort))
		echo "Available build-tools are ${sorted_build_tools[@]}"
		latest_build_tool=${sorted_build_tools[${#sorted_build_tools[@]}-1]}
		echo "Using latest build tool available: $latest_build_tool"
	fi
fi

latest_build_tool_dir="$build_tools_dir$latest_build_tool"
aapt2="${latest_build_tool_dir}/aapt2"

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
echo "e-ui-sdk.jar generated successully."