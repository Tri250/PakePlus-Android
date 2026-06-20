#!/bin/bash
set -e

export JAVA_HOME=/root/.local/share/mise/installs/java/17.0.2
export PATH=$JAVA_HOME/bin:$PATH

JAR_PATH="/workspace/android-sdk/platforms/android-36/android.jar"

# 1. Create temp directory
TEMP_DIR=$(mktemp -d)
echo "Using temp directory: $TEMP_DIR"

# 2. Create directory structure for colors.xml
RES_DIR="$TEMP_DIR/android/res/values"
mkdir -p "$RES_DIR"

# 3. Generate colors.xml with all required color definitions
COLORS_XML="$RES_DIR/colors.xml"

cat > "$COLORS_XML" << 'XMLEOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
XMLEOF

# Basic colors
cat >> "$COLORS_XML" << 'XMLEOF'
    <color name="black">#FF000000</color>
    <color name="white">#FFFFFFFF</color>
    <color name="transparent">#00000000</color>

    <!-- Holo colors -->
    <color name="holo_red_dark">#FFCC0000</color>
    <color name="holo_red_light">#FFFF4444</color>
    <color name="holo_blue_dark">#FF0099CC</color>
    <color name="holo_blue_light">#FF33B5E5</color>
    <color name="holo_green_dark">#FF669900</color>
    <color name="holo_green_light">#FF99CC00</color>
    <color name="holo_orange_dark">#FFFF8800</color>
    <color name="holo_orange_light">#FFFFBB33</color>
    <color name="holo_purple">#FFAA66CC</color>

    <!-- Common gray colors -->
    <color name="darker_gray">#FFAAAAAA</color>
    <color name="background_dark">#FF000000</color>
    <color name="background_light">#FFFFFFFF</color>
    <color name="bright_foreground_dark">#FFFFFFFF</color>
    <color name="bright_foreground_light">#FF000000</color>
    <color name="dim_foreground_dark">#FF808080</color>
    <color name="dim_foreground_light">#FF808080</color>
    <color name="dim_foreground_dark_disabled">#FF434343</color>
    <color name="dim_foreground_light_disabled">#FFC0C0C0</color>
    <color name="hint_foreground_dark">#FF808080</color>
    <color name="hint_foreground_light">#FF808080</color>
    <color name="secondary_text_dark">#FF999999</color>
    <color name="secondary_text_light">#FF666666</color>
    <color name="tertiary_text_dark">#FF666666</color>
    <color name="tertiary_text_light">#FF999999</color>
    <color name="primary_text_dark">#FFFFFFFF</color>
    <color name="primary_text_light">#FF000000</color>
    <color name="widget_edittext_dark">#FF999999</color>
    <color name="widget_edittext_light">#FF999999</color>

    <!-- More common Android colors -->
    <color name="holo_dark_primary">#FF111111</color>
    <color name="holo_light_primary">#FFEEEEEE</color>
    <color name="link_text_dark">#FF5C5CFF</color>
    <color name="link_text_light">#FF0000EE</color>

XMLEOF

# Generate Material You system_neutral1 colors (0 to 1000 in steps of 10)
# These are grayscale values interpolated from white (0) to black (1000)
echo "    <!-- Material You system_neutral1 colors -->" >> "$COLORS_XML"
for i in $(seq 0 10 1000); do
    # Linear grayscale interpolation: 0=white(255), 1000=black(0)
    gray=$((255 - (i * 255 / 1000)))
    # Format as 2-digit hex
    hex_gray=$(printf "%02X" $gray)
    echo "    <color name=\"system_neutral1_${i}\">#FF${hex_gray}${hex_gray}${hex_gray}</color>" >> "$COLORS_XML"
done

# Also generate system_neutral2 colors (slightly warm gray)
echo "" >> "$COLORS_XML"
echo "    <!-- Material You system_neutral2 colors -->" >> "$COLORS_XML"
for i in $(seq 0 10 1000); do
    gray=$((255 - (i * 255 / 1000)))
    # Slightly warm: R slightly higher, B slightly lower
    r=$((gray + (gray * 2 / 100) > 255 ? 255 : gray + (gray * 2 / 100)))
    g=$gray
    b=$((gray - (gray * 2 / 100) < 0 ? 0 : gray - (gray * 2 / 100)))
    hex_r=$(printf "%02X" $r)
    hex_g=$(printf "%02X" $g)
    hex_b=$(printf "%02X" $b)
    echo "    <color name=\"system_neutral2_${i}\">#FF${hex_r}${hex_g}${hex_b}</color>" >> "$COLORS_XML"
done

# Generate system_accent1 colors (blue-ish)
echo "" >> "$COLORS_XML"
echo "    <!-- Material You system_accent1 colors -->" >> "$COLORS_XML"
for i in $(seq 0 10 1000); do
    # Blue accent: from very light blue to very dark blue
    gray=$((255 - (i * 255 / 1000)))
    r=$((gray * 70 / 100))
    g=$((gray * 70 / 100))
    b=$gray
    hex_r=$(printf "%02X" $r)
    hex_g=$(printf "%02X" $g)
    hex_b=$(printf "%02X" $b)
    echo "    <color name=\"system_accent1_${i}\">#FF${hex_r}${hex_g}${hex_b}</color>" >> "$COLORS_XML"
done

# Generate system_accent2 colors
echo "" >> "$COLORS_XML"
echo "    <!-- Material You system_accent2 colors -->" >> "$COLORS_XML"
for i in $(seq 0 10 1000); do
    gray=$((255 - (i * 255 / 1000)))
    r=$((gray * 60 / 100))
    g=$((gray * 65 / 100))
    b=$((gray * 90 / 100))
    hex_r=$(printf "%02X" $r)
    hex_g=$(printf "%02X" $g)
    hex_b=$(printf "%02X" $b)
    echo "    <color name=\"system_accent2_${i}\">#FF${hex_r}${hex_g}${hex_b}</color>" >> "$COLORS_XML"
done

# Generate system_accent3 colors
echo "" >> "$COLORS_XML"
echo "    <!-- Material You system_accent3 colors -->" >> "$COLORS_XML"
for i in $(seq 0 10 1000); do
    gray=$((255 - (i * 255 / 1000)))
    r=$((gray * 55 / 100))
    g=$((gray * 75 / 100))
    b=$((gray * 85 / 100))
    hex_r=$(printf "%02X" $r)
    hex_g=$(printf "%02X" $g)
    hex_b=$(printf "%02X" $b)
    echo "    <color name=\"system_accent3_${i}\">#FF${hex_r}${hex_g}${hex_b}</color>" >> "$COLORS_XML"
done

echo "</resources>" >> "$COLORS_XML"

echo "Generated colors.xml at: $COLORS_XML"
echo "File size: $(wc -l < "$COLORS_XML") lines"

# 4. Update the android.jar to include these resources
echo ""
echo "Updating android.jar with resource files..."

# Backup original jar
cp "$JAR_PATH" "${JAR_PATH}.bak"
echo "Backup created at: ${JAR_PATH}.bak"

# Add the res directory to the jar
cd "$TEMP_DIR"
jar uf "$JAR_PATH" android/res/values/colors.xml

echo ""
echo "Verifying update..."
jar tf "$JAR_PATH" | grep "res/" || echo "ERROR: No res/ entries found in jar!"

# Cleanup
rm -rf "$TEMP_DIR"
echo ""
echo "Done! Temp directory cleaned up."
