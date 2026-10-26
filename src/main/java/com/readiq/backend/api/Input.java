package com.readiq.backend.api;
import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.*;

/** Explicit request allowlists prevent mass assignment; values are validated before SQL binding. */
public final class Input {
    private Input(){}
    public static UUID uuid(Object value){
        try{return UUID.fromString(Objects.toString(value,""));}
        catch(IllegalArgumentException ex){throw ApiException.bad("Invalid identifier");}
    }
    public static String text(Map<String,Object> body,String field,int max,boolean required){
        Object value=body.get(field);
        if(value==null){if(required)throw ApiException.bad(field+" is required");return null;}
        if(!(value instanceof String s)||s.length()>max||(required&&s.isBlank()))throw ApiException.bad("Invalid "+field);
        return s;
    }
    public static int integer(Object value,String field,int min,int max){
        if(!(value instanceof Number n))throw ApiException.bad(field+" must be an integer");
        try{
            int result=new BigDecimal(n.toString()).intValueExact();
            if(result<min||result>max)throw ApiException.bad("Invalid "+field);
            return result;
        }catch(ArithmeticException ex){throw ApiException.bad("Invalid "+field);}
    }
    public static boolean bool(Object value,String field){
        if(!(value instanceof Boolean b))throw ApiException.bad(field+" must be a boolean");return b;
    }
    public static List<Map<String,Object>> objects(Object value,String field,int max){
        if(!(value instanceof List<?> list)||list.size()>max)throw ApiException.bad("Invalid "+field);
        List<Map<String,Object>> result=new ArrayList<>();
        for(Object element:list){
            if(!(element instanceof Map<?,?> map))throw ApiException.bad("Invalid "+field);
            Map<String,Object> copy=new LinkedHashMap<>();map.forEach((k,v)->copy.put(k.toString(),v));result.add(copy);
        }
        return result;
    }
    public static Map<String,Object> fields(Map<String,Object> body,String... allowed){
        Map<String,Object> result=new LinkedHashMap<>();
        for(String field:allowed){
            if(!body.containsKey(field))continue;
            Object value=body.get(field);
            if(field.endsWith("_id")||field.equals("uploaded_by"))value=value==null?null:uuid(value);
            else if(field.startsWith("is_"))value=bool(value,field);
            else if(Set.of("duration_minutes","unit_order","lesson_order","order_index","start_page","end_page","page").contains(field))
                value=value==null?null:integer(value,field,0,1_000_000);
            else if(field.equals("start_time")||field.equals("end_time")){
                try{value=value==null?null:OffsetDateTime.parse(value.toString());}
                catch(Exception ex){throw ApiException.bad("Invalid "+field);}
            }else{
                value=text(body,field,field.equals("content_data")?500_000:10_000,false);
                if(value!=null&&field.endsWith("_url")&&!value.toString().isBlank()){
                    try{
                        URI uri=URI.create(value.toString());
                        if(!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null)throw new IllegalArgumentException();
                    }catch(Exception ex){throw ApiException.bad("Invalid "+field);}
                }
            }
            result.put(field,value);
        }
        return result;
    }
    public static void choice(Map<String,Object> body,String field,String... allowed){
        if(body.get(field)!=null&&!Set.of(allowed).contains(body.get(field)))throw ApiException.bad("Invalid "+field);
    }
    public static Map<String,Object> map(Object... entries){
        Map<String,Object> result=new LinkedHashMap<>();
        for(int i=0;i<entries.length;i+=2)result.put(entries[i].toString(),entries[i+1]);return result;
    }
}
