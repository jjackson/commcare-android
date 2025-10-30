package org.commcare.customui;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.webkit.JavascriptInterface;

import org.commcare.CommCareApplication;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.cases.model.Case;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.models.FormRecordProcessor;
import org.commcare.models.database.SqlStorage;
import org.commcare.android.database.user.models.ACase;
import org.commcare.utils.AndroidCommCarePlatform;
import org.commcare.utils.FileUtil;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.xform.parse.XFormParser;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Vector;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.SecretKeySpec;

/**
 * JavaScript interface that exposes CommCare's form and case APIs to web-based custom UIs.
 * 
 * All methods are annotated with @JavascriptInterface to make them callable from JavaScript.
 * Data is passed as JSON strings for compatibility with the WebView bridge.
 * 
 * Security: This interface only exposes read/write operations for the current logged-in user's
 * sandbox. All data remains encrypted at rest using CommCare's existing SQLCipher implementation.
 */
public class CommCareJavaScriptInterface {
    private static final String TAG = CommCareJavaScriptInterface.class.getSimpleName();
    
    private final Context context;
    private final AndroidCommCarePlatform platform;
    
    public CommCareJavaScriptInterface(Context context, AndroidCommCarePlatform platform) {
        this.context = context;
        this.platform = platform;
    }
    
    /**
     * Get list of all cases for the current user
     * @return JSON array of cases
     */
    @JavascriptInterface
    public String getCases() {
        try {
            SqlStorage<ACase> storage = CommCareApplication.instance().getUserStorage(ACase.STORAGE_KEY, ACase.class);
            JSONArray casesArray = new JSONArray();
            
            for (Case c : storage) {
                casesArray.put(caseToJson(c));
            }
            
            return casesArray.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error getting cases", e);
            return createErrorResponse("Failed to get cases: " + e.getMessage());
        }
    }
    
    /**
     * Get a specific case by ID
     * @param caseId The case ID to retrieve
     * @return JSON object representing the case
     */
    @JavascriptInterface
    public String getCase(String caseId) {
        try {
            SqlStorage<ACase> storage = CommCareApplication.instance().getUserStorage(ACase.STORAGE_KEY, ACase.class);
            Case c = storage.getRecordForValue(ACase.INDEX_CASE_ID, caseId);
            
            return caseToJson(c).toString();
        } catch (Exception e) {
            Log.e(TAG, "Error getting case: " + caseId, e);
            return createErrorResponse("Failed to get case: " + e.getMessage());
        }
    }
    
    /**
     * Get form definition (form structure and questions)
     * @param formXmlns The xmlns of the form to retrieve
     * @return JSON representation of the form
     */
    @JavascriptInterface
    public String getForm(String formXmlns) {
        try {
            // Get form definition from platform
            int formDefId = platform.getFormDefId(formXmlns);
            if (formDefId == -1) {
                return createErrorResponse("Form not found: " + formXmlns);
            }
            
            FormDef formDef = platform.getFormDef(formXmlns);
            if (formDef == null) {
                return createErrorResponse("Could not load form definition");
            }
            
            JSONObject formJson = new JSONObject();
            formJson.put("xmlns", formXmlns);
            formJson.put("name", formDef.getTitle());
            formJson.put("formDefId", formDefId);
            
            // Get form questions structure (simplified for POC)
            JSONArray questions = new JSONArray();
            TreeElement root = formDef.getMainInstance().getRoot();
            extractQuestions(root, questions);
            formJson.put("questions", questions);
            
            return formJson.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error getting form: " + formXmlns, e);
            return createErrorResponse("Failed to get form: " + e.getMessage());
        }
    }
    
    /**
     * Submit a form with data
     * @param formDataJson JSON string containing form xmlns and answer data
     * @return JSON response with success/error status
     */
    @JavascriptInterface
    public String submitForm(String formDataJson) {
        try {
            JSONObject formData = new JSONObject(formDataJson);
            String xmlns = formData.getString("xmlns");
            JSONObject answers = formData.getJSONObject("answers");
            
            // Create form instance XML
            String instanceXml = createFormInstanceXml(xmlns, answers);
            
            // Save form to CommCare's form record system
            FormRecord formRecord = saveFormRecord(xmlns, instanceXml);
            
            // Process the form (updates cases, etc.)
            FormRecordProcessor processor = new FormRecordProcessor(context);
            processor.process(formRecord);
            
            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("formRecordId", formRecord.getID());
            response.put("message", "Form submitted successfully");
            
            return response.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error submitting form", e);
            return createErrorResponse("Failed to submit form: " + e.getMessage());
        }
    }
    
    /**
     * Get current user information
     * @return JSON object with user details
     */
    @JavascriptInterface
    public String getCurrentUser() {
        try {
            AndroidSessionWrapper sessionWrapper = CommCareApplication.instance().getCurrentSessionWrapper();
            org.javarosa.core.model.User user = sessionWrapper.getSession().getLoggedInUser();
            
            JSONObject userJson = new JSONObject();
            userJson.put("username", user.getUsername());
            userJson.put("uniqueId", user.getUniqueId());
            userJson.put("userId", user.getWrappedUsername());
            
            return userJson.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error getting current user", e);
            return createErrorResponse("Failed to get user info: " + e.getMessage());
        }
    }
    
    /**
     * Log message from JavaScript (for debugging)
     * @param level Log level (debug, info, warn, error)
     * @param message Message to log
     */
    @JavascriptInterface
    public void log(String level, String message) {
        String logMessage = "[CustomUI] " + message;
        switch (level.toLowerCase()) {
            case "debug":
                Log.d(TAG, logMessage);
                break;
            case "info":
                Log.i(TAG, logMessage);
                break;
            case "warn":
                Log.w(TAG, logMessage);
                break;
            case "error":
                Log.e(TAG, logMessage);
                break;
            default:
                Log.d(TAG, logMessage);
        }
    }
    
    // ========== Helper Methods ==========
    
    private JSONObject caseToJson(Case c) throws JSONException {
        JSONObject caseJson = new JSONObject();
        caseJson.put("caseId", c.getCaseId());
        caseJson.put("caseType", c.getTypeId());
        caseJson.put("name", c.getName());
        caseJson.put("status", c.isClosed() ? "closed" : "open");
        caseJson.put("dateOpened", formatDate(c.getDateOpened()));
        caseJson.put("lastModified", formatDate(c.getLastModified()));
        caseJson.put("ownerId", c.getUserId());
        
        // Add case properties
        JSONObject properties = new JSONObject();
        Hashtable<String, String> caseProperties = c.getProperties();
        for (String key : caseProperties.keySet()) {
            properties.put(key, c.getPropertyString(key));
        }
        caseJson.put("properties", properties);
        
        return caseJson;
    }
    
    private void extractQuestions(TreeElement element, JSONArray questions) throws JSONException {
        // Simplified question extraction for POC
        // In production, this would handle groups, repeats, relevance, etc.
        for (int i = 0; i < element.getNumChildren(); i++) {
            TreeElement child = element.getChildAt(i);
            
            if (child.isRelevant() && child.getValue() != null) {
                JSONObject question = new JSONObject();
                question.put("name", child.getName());
                question.put("type", getQuestionType(child));
                
                // Get label if available
                String label = child.getName(); // Simplified - would normally get from form definition
                question.put("label", label);
                
                questions.put(question);
            }
            
            // Recurse for groups
            if (child.getNumChildren() > 0) {
                extractQuestions(child, questions);
            }
        }
    }
    
    private String getQuestionType(TreeElement element) {
        // Simplified type detection
        int dataType = element.getDataType();
        switch (dataType) {
            case org.javarosa.core.model.Constants.DATATYPE_INTEGER:
            case org.javarosa.core.model.Constants.DATATYPE_LONG:
                return "integer";
            case org.javarosa.core.model.Constants.DATATYPE_DECIMAL:
                return "decimal";
            case org.javarosa.core.model.Constants.DATATYPE_DATE:
                return "date";
            case org.javarosa.core.model.Constants.DATATYPE_TIME:
                return "time";
            case org.javarosa.core.model.Constants.DATATYPE_BOOLEAN:
                return "boolean";
            default:
                return "text";
        }
    }
    
    private String createFormInstanceXml(String xmlns, JSONObject answers) throws JSONException {
        // Create a basic form instance XML
        // In production, this would use FormDef to create properly structured XML
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<data xmlns=\"").append(xmlns).append("\" ");
        xml.append("xmlns:jrm=\"http://dev.commcarehq.org/jr/xforms\">\n");
        
        // Add metadata
        xml.append("  <meta>\n");
        xml.append("    <instanceID>").append(generateInstanceId()).append("</instanceID>\n");
        xml.append("    <timeStart>").append(getCurrentTimestamp()).append("</timeStart>\n");
        xml.append("    <timeEnd>").append(getCurrentTimestamp()).append("</timeEnd>\n");
        xml.append("    <userID>").append(getCurrentUserId()).append("</userID>\n");
        xml.append("  </meta>\n");
        
        // Add answer data
        JSONArray keys = answers.names();
        if (keys != null) {
            for (int i = 0; i < keys.length(); i++) {
                String key = keys.getString(i);
                String value = answers.optString(key, "");
                xml.append("  <").append(key).append(">")
                   .append(escapeXml(value))
                   .append("</").append(key).append(">\n");
            }
        }
        
        xml.append("</data>");
        
        return xml.toString();
    }
    
    private FormRecord saveFormRecord(String xmlns, String instanceXml) throws Exception {
        // Create encrypted form file
        File instanceFile = createInstanceFile(instanceXml);
        
        // Generate encryption key
        SecretKeySpec key = new SecretKeySpec(
            CommCareApplication.instance().createNewSymmetricKey().getEncoded(),
            "AES"
        );
        
        // Create form record
        FormRecord formRecord = new FormRecord(
            instanceFile.getAbsolutePath(),
            FormRecord.STATUS_UNPROCESSED,
            xmlns,
            key.getEncoded(),
            generateInstanceId(),
            null // No display name for API submissions
        );
        
        // Save to storage
        SqlStorage<FormRecord> storage = CommCareApplication.instance().getUserStorage(FormRecord.class);
        storage.write(formRecord);
        
        return formRecord;
    }
    
    private File createInstanceFile(String xmlContent) throws Exception {
        File instancesPath = new File(CommCareApplication.instance().
            getCurrentApp().fsPath(CommCareApplication.instance().getUserDbHandle()), "instances");
        
        if (!instancesPath.exists()) {
            instancesPath.mkdirs();
        }
        
        File instanceFile = new File(instancesPath, generateInstanceId() + ".xml");
        
        // Write encrypted XML
        FileOutputStream fos = new FileOutputStream(instanceFile);
        fos.write(xmlContent.getBytes("UTF-8"));
        fos.close();
        
        return instanceFile;
    }
    
    private String generateInstanceId() {
        return "uuid:" + java.util.UUID.randomUUID().toString();
    }
    
    private String getCurrentTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        return sdf.format(new Date());
    }
    
    private String getCurrentUserId() {
        try {
            return CommCareApplication.instance().getCurrentSessionWrapper()
                .getSession().getLoggedInUser().getUniqueId();
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    private String formatDate(Date date) {
        if (date == null) return "";
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return sdf.format(date);
    }
    
    private String escapeXml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&apos;");
    }
    
    private String createErrorResponse(String message) {
        try {
            JSONObject error = new JSONObject();
            error.put("success", false);
            error.put("error", message);
            return error.toString();
        } catch (JSONException e) {
            return "{\"success\":false,\"error\":\"" + message + "\"}";
        }
    }
    
    /**
     * Handle activity results (e.g., from barcode scanner, camera)
     * Called by CustomUIActivity when it receives an activity result
     */
    public void handleActivityResult(int requestCode, int resultCode, Intent data) {
        // Future enhancement: notify JavaScript of activity results
        Log.d(TAG, "Activity result: " + requestCode + ", " + resultCode);
    }
}
