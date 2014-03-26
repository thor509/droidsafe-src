package droidsafe.eclipse.plugin.core.view.json;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import soot.SootField;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import droidsafe.analyses.value.VAModel;
import droidsafe.analyses.value.VAUtils;
import droidsafe.analyses.value.ValueAnalysis;
import droidsafe.eclipse.plugin.core.view.json.Filter.CompareOp;

public class Utils {
    
    public static final String CHILDREN_PROP = "contents";
    
    public static final List<String> SIGNATURE_FIELDS;
    
    static {
        String[] sigFields = {"class", "method-name"};
        SIGNATURE_FIELDS = Arrays.asList(sigFields);
    }

    public static String getObjectType(JsonElement jsonElement) {
        return getFieldValueAsString(jsonElement, "type");
    }
    
    public static JsonArray getChildrenArray(JsonElement jsonElement) {
        if (jsonElement.isJsonObject()) {
            JsonElement value = ((JsonObject) jsonElement).get(CHILDREN_PROP);
            if (value != null && value.isJsonArray())
                return (JsonArray) value;
        }
        return null;
    }
    
    public static String getFieldValueAsString(JsonElement jsonElement, String field) {
        if (jsonElement.isJsonObject()) {
            JsonObject jsonObject = (JsonObject) jsonElement;
            JsonElement value = jsonObject.get(field);
            if (value != null) {
                return value.getAsString();
            } else if (SIGNATURE_FIELDS.contains(field)){
                getSignatureFieldValueAsString(jsonObject, field);
            }
        }
        return null;
    }

    public static String getSignatureFieldValueAsString(JsonObject jsonObject, String field) {
        String sig = jsonObject.get("signature").getAsString();
        if (sig != null) {
            if (field.equals("class"))
                return signatureClass(sig);
            if (field.equals("method-name"))
                return signatureMethodName(sig);
        }
        return null;
    }

    public static String shortSignature(String sig) {
        // remove the angle brackets
        sig = sig.substring(1,  sig.length() - 1);
        // remove the package qualifiers
        return sig.replaceAll("[a-zA-Z._]*[.]", "");
    }

    public static String signatureClass(String sig) {
        int pos = sig.indexOf(": ");
        return sig.substring(1, pos);
    }

    public static String signatureMethodName(String sig) {
        int pos1 = sig.indexOf(": ") + 2;
        int pos2 = sig.indexOf("(", pos1);
        return sig.substring(pos1, pos2);
    }

    public static String[] getFields(JsonObject jsonObj) {
        Set<String> fields = new TreeSet<String>();
        for (Map.Entry<String, JsonElement> entry: jsonObj.entrySet()) {
            String field = entry.getKey();
            if (!field.equals("type")) {
                if (field.equals("signature")) {
                    fields.add(field);
                    fields.addAll(SIGNATURE_FIELDS);
                } else {
                    JsonElement value = entry.getValue();
                    if (value.isJsonPrimitive()) 
                        fields.add(field);
                }
            }
        }
        return fields.toArray(new String[0]);
    }

    public static boolean isEmptyJsonObject(JsonElement jsonElt) {
        return jsonElt.isJsonObject() && ((JsonObject)jsonElt).entrySet().isEmpty();
    }
    
    public static String toStringPretty(JsonElement jsonElt) {
        return toStringPretty(jsonElt, 0);
    }
    
    public static String toStringPretty(JsonElement jsonElt, int level) {
        if (jsonElt.isJsonObject())
            return toStringPretty((JsonObject)jsonElt, level);
        if (jsonElt.isJsonArray())
            return toStringPretty((JsonArray)jsonElt, level);
        return jsonElt.toString();
    }
    
    public static String toStringPretty(JsonObject jsonObj, int level) {
        StringBuffer buf = new StringBuffer();
        buf.append("{");
        buf.append(fieldsToStringPretty(jsonObj, level + 1));
        buf.append("}");
        return buf.toString();
    }

    private static String fieldsToStringPretty(JsonObject jsonObj, int level) {
        StringBuffer buf = new StringBuffer();
        String indent = "\n" + VAUtils.indent(level);
        boolean firstField = true;
        for (Map.Entry<String, JsonElement> entry: jsonObj.entrySet()) {
            String field = entry.getKey();
            if (!field.equals(CHILDREN_PROP) && !field.equals("src-loc")) {
                if (!firstField)
                    buf.append(",");
                if (level == 1 && firstField)
                    buf.append(" ");
                else
                    buf.append(indent);
                firstField = false;
                buf.append(field);
                buf.append(": ");
                JsonElement value = entry.getValue();
                buf.append(toStringPretty(value, level + 1));
            }
        }
        return buf.toString();
    }

    private static String toStringPretty(JsonArray jsonArray, int level) {
        StringBuffer buf = new StringBuffer();
        buf.append("[");
        String indent = "\n" + VAUtils.indent(level);
        boolean firstElt = true;
        for (int i = 0; i < jsonArray.size(); i++) {
            JsonElement elt = jsonArray.get(i);
            if (firstElt)
                firstElt = false;
            else
                buf.append(",");
            buf.append(indent);
            buf.append(toStringPretty(elt, level + 1));
        }
        buf.append("]");
        return buf.toString();
    }

}
