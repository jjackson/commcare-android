# Sample CCZ Structure for Custom UI POC

## Quick Start

This guide shows you how to create a minimal CommCare app (CCZ file) with custom UI enabled.

## Directory Structure

```
my-custom-app/
├── profile.ccpr                    # App profile (required)
├── suite.xml                       # App suite definition (required)
├── media/                          # Media files folder
│   └── app_logo.png               # (optional)
└── custom_ui/                      # Custom UI folder (THIS ENABLES CUSTOM UI)
    ├── config.json                 # Custom UI configuration
    └── index.html                  # Your React app
```

## File Contents

### 1. `profile.ccpr`

This is your app profile. Use this minimal template:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<profile xmlns="http://commcarehq.org/profile/2014">
    <unique_id>custom-ui-poc-app</unique_id>
    <display_name>Custom UI POC</display_name>
    <version>1</version>
    <build_number>1</build_number>
    <descriptor>Custom UI Proof of Concept</descriptor>
    
    <suite>
        <resource id="suite">
            <location authority="local">./suite.xml</location>
        </resource>
    </suite>
</profile>
```

### 2. `suite.xml`

Minimal suite with a single form:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<suite version="1" xmlns="http://commcarehq.org/suite">
    
    <!-- Define the patient case type -->
    <partial>
        <module id="patient_module">
            <text>
                <locale id="modules.patient_module"/>
            </text>
            
            <form xmlns="http://commcarehq.org/case/transaction/v2">
                <text>
                    <locale id="forms.patient_registration"/>
                </text>
                <command id="patient_registration"/>
            </form>
        </module>
    </partial>
    
    <!-- Define the form entry -->
    <entry>
        <form xmlns="http://commcarehq.org/case/transaction/v2">
            http://commcarehq.org/custom-ui-poc/patient-registration
        </form>
        <command id="patient_registration"/>
        
        <session>
            <datum id="case_id" nodeset="instance('casedb')/casedb/case[@case_type='patient']" value="./@case_id" function="start" />
        </session>
    </entry>
    
    <!-- Locale strings -->
    <locale language="default">
        <text id="modules.patient_module">
            <value>Patient Management</value>
        </text>
        <text id="forms.patient_registration">
            <value>Register Patient</value>
        </text>
    </locale>
    
</suite>
```

### 3. `custom_ui/config.json`

This file tells CommCare to use custom UI:

```json
{
  "customUI": {
    "enabled": true,
    "entrypoint": "custom_ui/index.html",
    "version": "1.0.0",
    "description": "Custom CommCare UI POC - Patient Registration"
  }
}
```

**Important**: If this file doesn't exist or `enabled: false`, CommCare will use the standard UI.

### 4. `custom_ui/index.html`

Copy from `/workspace/app/assets/custom_ui/index.html` or use a minimal version:

```html
<!DOCTYPE html>
<html>
<head>
    <title>CommCare Custom UI</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
</head>
<body>
    <h1>Hello from Custom UI!</h1>
    <button onclick="testAPI()">Test API</button>
    
    <script>
        function testAPI() {
            try {
                const user = window.CommCareAPI.getCurrentUser();
                alert('Current User: ' + user);
                
                const cases = window.CommCareAPI.getCases();
                alert('Cases: ' + cases);
            } catch (error) {
                alert('Error: ' + error.message);
            }
        }
    </script>
</body>
</html>
```

## Building the CCZ

### Option 1: Manual (Recommended for POC)

1. Create the directory structure:
```bash
mkdir -p my-custom-app/custom_ui
mkdir -p my-custom-app/media
```

2. Create each file with contents above

3. Package as ZIP:
```bash
cd my-custom-app
zip -r ../my-custom-app.ccz *
```

4. Rename to `.ccz`:
```bash
mv ../my-custom-app.zip ../my-custom-app.ccz
```

### Option 2: From Existing CommCareHQ App

1. Build and download your app from CommCareHQ

2. Unzip the CCZ:
```bash
unzip my-app.ccz -d my-app-custom
```

3. Add custom UI:
```bash
mkdir -p my-app-custom/custom_ui
cp config.json my-app-custom/custom_ui/
cp index.html my-app-custom/custom_ui/
```

4. Repackage:
```bash
cd my-app-custom
zip -r ../my-app-with-custom-ui.ccz *
```

## Testing Your CCZ

### 1. Upload to CommCareHQ (Recommended)

1. Go to your CommCareHQ domain
2. Create new app or go to existing app
3. Go to "Settings" → "Advanced" → "Multimedia"
4. Upload your custom UI files (`config.json`, `index.html`)
5. Make a new app build
6. Download and install on device

### 2. Direct Install (Developer Mode)

1. Host your CCZ file somewhere accessible:
   ```bash
   # Using Python
   cd /path/to/ccz-folder
   python -m http.server 8000
   ```

2. On device, install CommCare Debug version

3. Go to Settings → Install App from File

4. Enter URL: `http://your-ip:8000/my-custom-app.ccz`

### 3. SD Card Install

1. Copy CCZ to device:
```bash
adb push my-custom-app.ccz /sdcard/Download/
```

2. In CommCare, install from local file

## Form Definition (Advanced)

If you want CommCare to actually process the form and create cases, you need a proper XForm definition.

Create `modules-0/form0.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<h:html xmlns="http://www.w3.org/2002/xforms" 
        xmlns:h="http://www.w3.org/1999/xhtml" 
        xmlns:jr="http://openrosa.org/javarosa">
    <h:head>
        <h:title>Patient Registration</h:title>
        <model>
            <instance>
                <data xmlns="http://commcarehq.org/custom-ui-poc/patient-registration" 
                      xmlns:jrm="http://dev.commcarehq.org/jr/xforms">
                    <patient_name/>
                    <patient_age/>
                    <patient_gender/>
                    <patient_id/>
                    <registration_date/>
                    <meta>
                        <instanceID/>
                        <timeStart/>
                        <timeEnd/>
                        <userID/>
                    </meta>
                </data>
            </instance>
            
            <!-- Create a patient case -->
            <bind nodeset="/data/patient_name" type="string" required="true()"/>
            <bind nodeset="/data/patient_age" type="int" required="true()"/>
            <bind nodeset="/data/patient_gender" type="select1" required="true()"/>
            <bind nodeset="/data/patient_id" type="string" required="true()"/>
            <bind nodeset="/data/registration_date" type="date" required="true()"/>
            
            <bind nodeset="/data/meta/instanceID" type="string" readonly="true()" 
                  calculate="concat('uuid:', uuid())"/>
            <bind nodeset="/data/meta/timeStart" type="dateTime" readonly="true()" 
                  calculate="now()"/>
            <bind nodeset="/data/meta/timeEnd" type="dateTime" readonly="true()" 
                  calculate="now()"/>
            <bind nodeset="/data/meta/userID" type="string" readonly="true()" />
            
            <!-- Case block to create patient -->
            <instance id="commcaresession" src="jr://instance/session"/>
            
            <create xmlns="http://commcarehq.org/case/transaction/v2">
                <case case_id="uuid()" user_id="instance('commcaresession')/session/data/user_id" 
                      date_modified="now()" xmlns="http://commcarehq.org/case/transaction/v2">
                    <create>
                        <case_name>/data/patient_name</case_name>
                        <owner_id>instance('commcaresession')/session/data/user_id</owner_id>
                        <case_type>patient</case_type>
                    </create>
                    <update>
                        <patient_name>/data/patient_name</patient_name>
                        <patient_age>/data/patient_age</patient_age>
                        <patient_gender>/data/patient_gender</patient_gender>
                        <patient_id>/data/patient_id</patient_id>
                        <registration_date>/data/registration_date</registration_date>
                    </update>
                </case>
            </create>
        </model>
    </h:head>
    <h:body>
        <!-- No UI needed - custom UI handles display -->
    </h:body>
</h:html>
```

Update your `profile.ccpr` to include the form:

```xml
<xform>
    <resource id="form0">
        <location authority="local">./modules-0/form0.xml</location>
    </resource>
</xform>
```

## Minimal Working Example

For the absolute minimum POC, you only need:

```
my-app.ccz
├── profile.ccpr (basic profile)
└── custom_ui/
    ├── config.json (enabled: true)
    └── index.html (your React app)
```

CommCare will launch the custom UI but won't process forms properly without form definitions. This is fine for UI testing.

## Troubleshooting

### "Custom UI not detected"

- Ensure `custom_ui/config.json` exists in CCZ root
- Verify `enabled: true` in config.json
- Check file paths are correct
- Run: `unzip -l my-app.ccz` to verify structure

### "App won't install"

- Verify CCZ is properly zipped
- Check XML syntax in profile.ccpr and suite.xml
- Ensure xmlns attributes are correct
- Try installing via CommCareHQ instead of direct install

### "Forms not creating cases"

- Need proper XForm definition (see above)
- Check form xmlns matches between XForm and custom UI
- Verify case block is in XForm definition
- Check CommCare logs: `adb logcat | grep CommCare`

## Next Steps

1. Start with minimal CCZ (just config.json + index.html)
2. Test custom UI loads
3. Add form definition if you want case creation
4. Gradually enhance your React UI
5. Test offline functionality
6. Deploy to production

---

**Quick Test**: Create a minimal CCZ with just the files shown above, install it, and verify the custom UI loads. Once working, add complexity incrementally.
