#!/bin/bash

# CommCare Custom UI POC - Build and Test Script
# This script builds the CommCare APK and helps you create a test CCZ

set -e  # Exit on error

echo "========================================"
echo "CommCare Custom UI POC - Build Script"
echo "========================================"
echo ""

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Function to print colored messages
print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

# Check if we're in the right directory
if [ ! -f "gradlew" ]; then
    print_error "This script must be run from the CommCare Android root directory (/workspace)"
    exit 1
fi

echo "Step 1: Building CommCare Android APK..."
echo "This may take a few minutes on first build..."
echo ""

./gradlew assembleCommcareDebug

if [ $? -eq 0 ]; then
    print_success "APK built successfully!"
    APK_PATH="app/build/outputs/apk/commcare/debug/app-commcare-debug.apk"
    echo "   Location: $APK_PATH"
    echo ""
else
    print_error "Build failed. Check error messages above."
    exit 1
fi

echo "Step 2: Checking for connected Android device..."
echo ""

if command -v adb &> /dev/null; then
    DEVICES=$(adb devices | grep -v "List" | grep "device$" | wc -l)
    if [ $DEVICES -gt 0 ]; then
        print_success "Found $DEVICES connected device(s)"
        echo ""
        read -p "Install APK on device now? (y/n) " -n 1 -r
        echo ""
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            adb install -r "$APK_PATH"
            if [ $? -eq 0 ]; then
                print_success "APK installed on device!"
            else
                print_warning "Installation failed. Device may not have USB debugging enabled."
            fi
        fi
    else
        print_warning "No Android devices found"
        echo "   Connect device and enable USB debugging, then run:"
        echo "   adb install -r $APK_PATH"
    fi
else
    print_warning "adb not found in PATH"
    echo "   Install Android SDK Platform Tools to use adb"
fi

echo ""
echo "Step 3: Custom UI Assets"
echo ""

if [ -f "app/assets/custom_ui/index.html" ] && [ -f "app/assets/custom_ui/config.json" ]; then
    print_success "Custom UI assets found in app/assets/custom_ui/"
    echo "   - index.html (React form UI)"
    echo "   - config.json (Enable flag)"
    echo ""
    echo "   These are automatically included in the APK."
else
    print_error "Custom UI assets not found!"
    echo "   Expected files:"
    echo "   - app/assets/custom_ui/index.html"
    echo "   - app/assets/custom_ui/config.json"
fi

echo ""
echo "Step 4: Creating a Test CCZ"
echo ""

# Create a sample CCZ directory
CCZ_DIR="sample-custom-app"
if [ -d "$CCZ_DIR" ]; then
    print_warning "Removing existing $CCZ_DIR directory"
    rm -rf "$CCZ_DIR"
fi

mkdir -p "$CCZ_DIR/custom_ui"

# Copy custom UI assets
if [ -d "app/assets/custom_ui" ]; then
    cp app/assets/custom_ui/* "$CCZ_DIR/custom_ui/"
    print_success "Copied custom UI assets to $CCZ_DIR/custom_ui/"
fi

# Create minimal profile.ccpr
cat > "$CCZ_DIR/profile.ccpr" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<profile xmlns="http://commcarehq.org/profile/2014">
    <unique_id>custom-ui-poc-test</unique_id>
    <display_name>Custom UI Test App</display_name>
    <version>1</version>
    <build_number>1</build_number>
    <descriptor>CommCare Custom UI POC Test Application</descriptor>
</profile>
EOF

print_success "Created profile.ccpr"

# Create the CCZ file
if command -v zip &> /dev/null; then
    cd "$CCZ_DIR"
    zip -r "../${CCZ_DIR}.ccz" * > /dev/null 2>&1
    cd ..
    print_success "Created ${CCZ_DIR}.ccz"
    echo ""
    echo "   You can now:"
    echo "   1. Upload ${CCZ_DIR}.ccz to CommCareHQ"
    echo "   2. Or install directly on device (requires CommCare Debug build)"
    echo ""
else
    print_warning "zip command not found - CCZ not created"
    echo "   Manually zip the contents of $CCZ_DIR/ to create a CCZ file"
fi

echo ""
echo "========================================"
echo "Build Complete!"
echo "========================================"
echo ""
echo "Next Steps:"
echo ""
echo "1. Install the APK on your device (if not done already):"
echo "   adb install -r $APK_PATH"
echo ""
echo "2. Install the test app:"
echo "   - Upload ${CCZ_DIR}.ccz to CommCareHQ, or"
echo "   - Use app management in CommCare to install from file"
echo ""
echo "3. Login to CommCare"
echo ""
echo "4. Select your custom UI app"
echo "   → Should see React UI instead of standard CommCare home screen"
echo ""
echo "5. Test functionality:"
echo "   - Register a patient (form submission)"
echo "   - View cases (case retrieval)"
echo "   - Test offline (disable WiFi, submit form, enable WiFi, sync)"
echo ""
echo "6. Debug with Chrome DevTools:"
echo "   - Connect device via USB"
echo "   - Open chrome://inspect in Chrome"
echo "   - Find and inspect your WebView"
echo ""
echo "Documentation:"
echo "   - Quick Start: QUICK_START.md"
echo "   - Full Guide: CUSTOM_UI_POC_README.md"
echo "   - CCZ Creation: SAMPLE_CCZ_GUIDE.md"
echo ""
echo "Troubleshooting:"
echo "   - View logs: adb logcat | grep CustomUI"
echo "   - Check API: Open chrome://inspect console"
echo ""
print_success "POC is ready to test!"
echo ""
