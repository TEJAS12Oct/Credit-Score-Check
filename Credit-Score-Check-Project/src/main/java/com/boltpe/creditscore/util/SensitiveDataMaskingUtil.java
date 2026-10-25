package com.boltpe.creditscore.util;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
@Component
public class SensitiveDataMaskingUtil {
 private static final Set<String> SECRET_FIELDS=Set.of("otp","secretkey","clientid","token","authorization","accesstoken","refreshtoken");
 private final ObjectMapper mapper; public SensitiveDataMaskingUtil(ObjectMapper mapper){this.mapper=mapper;}
 public String sanitize(Object value){try{JsonNode node=mapper.valueToTree(value);mask(node);return mapper.writeValueAsString(node);}catch(Exception ex){return "{\"sanitized\":true}";}}
 /** Sanitizes unstructured vendor messages without exposing financial identifiers. */
 public String sanitizeFreeText(String value) {
  if(value==null)return null;
  String safe=value.replaceAll("(?i)\\b[A-Z]{5}\\d{4}[A-Z]\\b", "[REDACTED_PAN]")
      .replaceAll("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b", "[REDACTED_EMAIL]")
      .replaceAll("(?<!\\d)[6-9]\\d{9}(?!\\d)", "[REDACTED_MOBILE]")
      .replaceAll("(?i)\\b(otp|password|secret|token|client[_ -]?id|authorization)\\s*[:=]\\s*[^\\s,;]+", "$1=[REDACTED]");
  return safe.length()>500 ? safe.substring(0,500) : safe;
 }
 private void mask(JsonNode node){if(node==null)return;if(node.isObject()){ObjectNode o=(ObjectNode)node;for(var e:o.properties()){String k=e.getKey();JsonNode v=e.getValue();if(isSecret(k))o.put(k,"***");else if(v!=null&&v.isTextual())o.put(k,maskValue(k,v.asText()));else mask(v);}}else if(node.isArray())node.forEach(this::mask);}
 private boolean isSecret(String key){String n=normalize(key);return SECRET_FIELDS.contains(n);}
 private String maskValue(String key,String value){if(value==null)return null;String n=normalize(key);
  if(n.contains("pan")||n.contains("incometaxpan"))return keepLast(value,4);
  if(n.contains("mobile")||n.contains("telephone")||n.contains("phone"))return keepLast(value,4);
  if(n.contains("accountnumber")||n.equals("accountno")||n.contains("accountnum"))return keepLast(value,4);
  if(n.contains("email"))return maskEmail(value);
  return value;}
 private String normalize(String key){return key==null?"":key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");}
 private String keepLast(String value,int count){if(value.isBlank())return value;if(value.length()<=count)return "****";return "*".repeat(Math.max(4,value.length()-count))+value.substring(value.length()-count);}
 private String maskEmail(String value){int at=value.indexOf('@');return at>0?value.charAt(0)+"***"+value.substring(at):"***";}
}
