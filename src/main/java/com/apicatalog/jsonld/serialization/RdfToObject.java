package com.apicatalog.jsonld.serialization;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.json.stream.JsonParser;

import com.apicatalog.jsonld.api.JsonLdError;
import com.apicatalog.jsonld.api.JsonLdErrorCode;
import com.apicatalog.jsonld.api.JsonLdOptions.RdfDirection;
import com.apicatalog.jsonld.json.JsonUtils;
import com.apicatalog.jsonld.lang.Keywords;
import com.apicatalog.jsonld.lang.Version;
import com.apicatalog.rdf.RdfLiteral;
import com.apicatalog.rdf.RdfObject;
import com.apicatalog.rdf.lang.RdfConstants;
import com.apicatalog.rdf.lang.XsdConstants;

final class RdfToObject {

    // required
    private RdfObject value;
    private RdfDirection rdfDirection;
    private boolean useNativeTypes;
    
    // optional
    private Version processingMode;
    
    private RdfToObject(final RdfObject object, final RdfDirection rdfDirection, final boolean useNativeTypes) {
        this.value = object;
        this.rdfDirection = rdfDirection;
        this.useNativeTypes = useNativeTypes;
        
        this.processingMode = null;
    }
    
    public static final RdfToObject with(final RdfObject object, final RdfDirection rdfDirection, final boolean useNativeTypes) {
        return new RdfToObject(object, rdfDirection, useNativeTypes);
    }
    
    public RdfToObject processingMode(Version processingMode) {
        this.processingMode = processingMode;
        return this;
    }
    
    public JsonObject build() throws JsonLdError {
        
        // 1.
        if (value.isIRI() || value.isBlankNode()) {
            return Json.createObjectBuilder().add(Keywords.ID, value.toString()).build();
        }

        final Map<String, JsonValue> result = new LinkedHashMap<>();
        
        // 2.
        final RdfLiteral literal = value.getLiteral();
        
        // 2.2.
        JsonValue convertedValue = Json.createValue(literal.getValue());
        
        // 2.3.
        String type = null;

        // 2.4.
        if (useNativeTypes) {
            
            if (literal.getDatatype() != null) {
            
                // 2.4.1.
                if (XsdConstants.STRING.equals(literal.getDatatype())) {
                    convertedValue = Json.createValue(literal.toString());
    
                // 2.4.2.
                } else if (XsdConstants.BOOLEAN.equals(literal.getDatatype())) {
                    
                    if ("true".equalsIgnoreCase(literal.getValue())) {
                    
                        convertedValue = JsonValue.TRUE;
                        
                    } else if ("false".equalsIgnoreCase(literal.getValue())) {
    
                        convertedValue = JsonValue.FALSE;
                        
                    } else {
    
                        type = XsdConstants.BOOLEAN;
                    }
                    
                // 2.4.3.                
                } else if (XsdConstants.INTEGER.equals(literal.getDatatype())) {
                    
                    convertedValue = Json.createValue(Long.parseLong(literal.getValue()));
                    
                } else if (XsdConstants.DOUBLE.equals(literal.getDatatype())) {
                    
                    convertedValue = Json.createValue(Double.parseDouble(literal.getValue()));
                    
                } else if (literal.getDatatype() != null) {
                    
                    type = literal.getDatatype();
                    
                }
            }

        // 2.5.
        } else if (processingMode != Version.V1_0 
                        && literal.getDatatype() != null 
                        && RdfConstants.JSON.equals(literal.getDatatype())) {

            try (JsonParser parser = Json.createParser(new StringReader(literal.getValue()))) {
                
                parser.next();
                
                convertedValue = parser.getValue();
                type = Keywords.JSON;
                
            } catch (Exception e) {
                throw new JsonLdError(JsonLdErrorCode.INVALID_JSON_LITERAL, e);
            }
            
        // 2.6.
        } else if (RdfDirection.I18N_DATATYPE == rdfDirection
                    && literal.getDatatype() != null 
                    && literal.getDatatype().startsWith(RdfConstants.I18N_BASE)
                ) {

            convertedValue = Json.createValue(literal.getValue());

            String langId = literal.getDatatype().substring(RdfConstants.I18N_BASE.length());
            
            int directionIndex = langId.indexOf('_');

            if (directionIndex > 1) {
                
                result.put(Keywords.LANGUAGE, Json.createValue(langId.substring(0, directionIndex)));
                result.put(Keywords.DIRECTION, Json.createValue(langId.substring(directionIndex + 1)));
                
            } else if (directionIndex == 0) {
                
                result.put(Keywords.DIRECTION, Json.createValue(langId.substring(1)));
                
            } else  if (directionIndex == -1) {
                
                result.put(Keywords.LANGUAGE, Json.createValue(langId));
            }
            
        // 2.7. 
        } else if (literal.getLanguage() != null) {
            result.put(Keywords.LANGUAGE, Json.createValue(literal.getLanguage()));
                     
        // 2.8.   
        } else if (literal.getDatatype() != null 
                        && !XsdConstants.STRING.equals(literal.getDatatype())) {
            
            type = literal.getDatatype();
        }        

        // 2.9.
        result.put(Keywords.VALUE, convertedValue);
   
        // 2.10.
        if (type != null) {
            result.put(Keywords.TYPE, Json.createValue(type));
        }
        
        // 2.11.
        return JsonUtils.toJsonObject(result);
    }
}
